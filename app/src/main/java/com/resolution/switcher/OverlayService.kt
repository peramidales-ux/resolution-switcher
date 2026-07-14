package com.resolution.switcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.resolution.switcher.resolution.ResolutionController
import com.resolution.switcher.util.OverlayPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var overlayView: View? = null
    private var collapsedView: View? = null
    private var isCollapsed = false

    private var nativeW = 1080
    private var nativeH = 2400
    private var nativeDpi = 420
    private var minW = 0
    private var maxW = 2160
    private var minH = 0
    private var maxH = 4800

    private val handler = Handler(Looper.getMainLooper())
    private var pendingApply: Runnable? = null
    private var isApplying = false

    private var ctrl: ResolutionController? = null
    private var ignoreText = false
    private var foregroundMonitor: ForegroundAppMonitor? = null

    companion object {
        const val CHANNEL_ID = "resolution_switcher"
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            ctrl = ResolutionController.create(this)

            loadNative()
            showOverlay()

            foregroundMonitor = ForegroundAppMonitor(this, ctrl)
            foregroundMonitor?.start()

            Toast.makeText(this, "Оверлей запущен!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CLOSE") {
            foregroundMonitor?.stop()
            Thread {
                try {
                    val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", "wm size reset"))
                    p1.waitFor()
                    val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", "wm density reset"))
                    p2.waitFor()
                } catch (_: Exception) {}
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }.start()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        foregroundMonitor?.stop()
        pendingApply?.let { handler.removeCallbacks(it) }
        removeOverlay()
        removeCollapsed()
        Thread {
            try {
                val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", "wm size reset"))
                p1.waitFor()
                val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", "wm density reset"))
                p2.waitFor()
            } catch (_: Exception) {}
        }.start()
        scope.cancel()
    }

    private fun loadNative() {
        scope.launch {
            val res = ctrl?.getNativeResolution()
            if (res != null) {
                nativeW = res.first
                nativeH = res.second
                nativeDpi = ctrl?.getNativeDensity() ?: 420
            }
            minW = (nativeW * 0.4).toInt()
            maxW = nativeW * 2
            minH = (nativeH * 0.4).toInt()
            maxH = nativeH * 2
            updateNativeText()
            restoreValues()
        }
    }

    private fun showOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_panel, null)

        val savedPos = OverlayPrefs.getOverlayPosition(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedPos.first
            y = savedPos.second
        }
        overlayView!!.tag = params
        windowManager.addView(overlayView, params)

        setupListeners(overlayView!!)
        updateNativeText()

        val saved = OverlayPrefs.getSavedResolution(this)
        val initW = saved?.first ?: nativeW
        val initH = saved?.second ?: nativeH
        val ar = if (nativeW > 0) ((initW.toFloat() / nativeW) * 100).toInt().coerceIn(0, 200) else 100

        ignoreText = true
        setWidth(initW)
        setHeight(initH)
        ignoreText = false
        overlayView?.findViewById<SeekBar>(R.id.seekAR)?.progress = ar
        overlayView?.findViewById<TextView>(R.id.tvARValue)?.text = "$ar%"
    }

    private fun restoreValues() {
        val saved = OverlayPrefs.getSavedResolution(this)
        val w = saved?.first ?: nativeW
        val h = saved?.second ?: nativeH
        val ar = if (nativeW > 0) ((w.toFloat() / nativeW) * 100).toInt().coerceIn(0, 200) else 100

        ignoreText = true
        setWidth(w)
        setHeight(h)
        ignoreText = false
        overlayView?.findViewById<SeekBar>(R.id.seekAR)?.progress = ar
        overlayView?.findViewById<TextView>(R.id.tvARValue)?.text = "$ar%"
    }

    private fun setupListeners(view: View) {
        val header = view.findViewById<LinearLayout>(R.id.header)
        val btnCollapse = view.findViewById<ImageButton>(R.id.btnCollapse)
        val seekW = view.findViewById<SeekBar>(R.id.seekWidth)
        val seekH = view.findViewById<SeekBar>(R.id.seekHeight)
        val seekAR = view.findViewById<SeekBar>(R.id.seekAR)
        val tvAR = view.findViewById<TextView>(R.id.tvARValue)
        val etW = view.findViewById<EditText>(R.id.etWidth)
        val etH = view.findViewById<EditText>(R.id.etHeight)
        val btnReset = view.findViewById<Button>(R.id.btnReset)

        setupDrag(header, view)
        btnCollapse.setOnClickListener { collapseOverlay() }

        seekAR.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvAR.text = "$progress%"
                if (fromUser && !isApplying) {
                    val s = progress / 100f
                    val nw = (nativeW * s).toInt().coerceIn(minW, maxW)
                    val nh = (nativeH * s).toInt().coerceIn(minH, maxH)
                    ignoreText = true
                    setWidth(nw)
                    setHeight(nh)
                    ignoreText = false
                    applyRes(nw, nh)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekW.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isApplying) {
                    val w = progressToVal(progress, minW, maxW)
                    ignoreText = true
                    etW.setText(w.toString())
                    ignoreText = false
                    applyRes(w, getCurH())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekH.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isApplying) {
                    val h = progressToVal(progress, minH, maxH)
                    ignoreText = true
                    etH.setText(h.toString())
                    ignoreText = false
                    applyRes(getCurW(), h)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        etW.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (ignoreText || isApplying) return
                val v = s?.toString()?.toIntOrNull() ?: return
                if (v in minW..maxW) {
                    seekW.progress = valToProgress(v, minW, maxW)
                    applyRes(v, getCurH())
                }
            }
        })

        etH.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (ignoreText || isApplying) return
                val v = s?.toString()?.toIntOrNull() ?: return
                if (v in minH..maxH) {
                    seekH.progress = valToProgress(v, minH, maxH)
                    applyRes(getCurW(), v)
                }
            }
        })

        btnReset.setOnClickListener {
            pendingApply?.let { handler.removeCallbacks(it) }
            pendingApply = null
            isApplying = true

            OverlayPrefs.clearSavedResolution(this@OverlayService)

            val resetW = nativeW
            val resetH = nativeH
            val resetDpi = nativeDpi

            scope.launch {
                ctrl?.resetResolution()
                ctrl?.resetDensity()
                OverlayPrefs.saveResolution(this@OverlayService, resetW, resetH, resetDpi)
                isApplying = false

                overlayView?.post {
                    ignoreText = true
                    setWidth(resetW)
                    setHeight(resetH)
                    ignoreText = false
                    seekAR.progress = 100
                    tvAR.text = "100%"
                    Toast.makeText(this@OverlayService, "Сброшено", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyRes(w: Int, h: Int) {
        if (isApplying) return

        val dpi = (nativeDpi * maxOf(w.toFloat() / nativeW, h.toFloat() / nativeH)).toInt().coerceAtLeast(1)
        OverlayPrefs.saveResolution(this, w, h, dpi)

        pendingApply?.let { handler.removeCallbacks(it) }

        pendingApply = Runnable {
            scope.launch {
                ctrl?.setResolution(w, h)
                ctrl?.setDensity(dpi)
            }
        }
        handler.postDelayed(pendingApply!!, 300)
    }

    private fun setupDrag(header: View, view: View) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var moved = false; var startY = 0f
        header.setOnTouchListener { _, e ->
            val p = view.tag as WindowManager.LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = p.x; iy = p.y; tx = e.rawX; ty = e.rawY; startY = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (dx*dx + dy*dy > 100) moved = true
                    p.x = ix + dx.toInt(); p.y = iy + dy.toInt()
                    windowManager.updateViewLayout(view, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (e.rawY - startY > 200 && !moved) collapseOverlay()
                    else if (moved) OverlayPrefs.saveOverlayPosition(this@OverlayService, p.x, p.y)
                    true
                }
                else -> false
            }
        }
    }

    private fun getCurW(): Int = overlayView?.findViewById<EditText>(R.id.etWidth)?.text?.toString()?.toIntOrNull() ?: nativeW
    private fun getCurH(): Int = overlayView?.findViewById<EditText>(R.id.etHeight)?.text?.toString()?.toIntOrNull() ?: nativeH

    private fun setWidth(v: Int) {
        overlayView?.let {
            it.findViewById<SeekBar>(R.id.seekWidth)?.progress = valToProgress(v, minW, maxW)
            it.findViewById<EditText>(R.id.etWidth)?.setText(v.toString())
        }
    }

    private fun setHeight(v: Int) {
        overlayView?.let {
            it.findViewById<SeekBar>(R.id.seekHeight)?.progress = valToProgress(v, minH, maxH)
            it.findViewById<EditText>(R.id.etHeight)?.setText(v.toString())
        }
    }

    private fun progressToVal(p: Int, min: Int, max: Int) = min + ((max - min) * p / 100)
    private fun valToProgress(v: Int, min: Int, max: Int): Int {
        if (max == min) return 0
        return ((v - min) * 100 / (max - min)).coerceIn(0, 100)
    }

    private fun updateNativeText() {
        overlayView?.findViewById<TextView>(R.id.tvNativeRes)?.text = "Заводское: ${nativeW}x${nativeH}"
    }

    private fun collapseOverlay() {
        overlayView?.let {
            val p = it.tag as? WindowManager.LayoutParams
            if (p != null) OverlayPrefs.saveOverlayPosition(this, p.x, p.y)
        }
        removeOverlay()
        showCollapsed()
        isCollapsed = true
    }

    private fun expandOverlay() {
        collapsedView?.let {
            val p = it.tag as? WindowManager.LayoutParams
            if (p != null) OverlayPrefs.saveCollapsedPosition(this, p.x, p.y)
        }
        removeCollapsed()
        showOverlay()
        isCollapsed = false
    }

    private fun showCollapsed() {
        collapsedView = View(this).apply { setBackgroundColor(0xFF1976D2.toInt()) }
        val sp = OverlayPrefs.getCollapsedPosition(this)
        val p = WindowManager.LayoutParams(48, 48,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = sp.first; y = sp.second }
        collapsedView?.tag = p
        collapsedView?.setOnClickListener { expandOverlay() }
        setupDragSmall(collapsedView!!, p)
        windowManager.addView(collapsedView, p)
    }

    private fun setupDragSmall(view: View, params: WindowManager.LayoutParams) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (dx*dx + dy*dy > 100) moved = true
                    params.x = ix + dx.toInt(); params.y = iy + dy.toInt()
                    windowManager.updateViewLayout(view, params); true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) OverlayPrefs.saveCollapsedPosition(this@OverlayService, params.x, params.y)
                    if (!moved) expandOverlay()
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() { overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }; overlayView = null }
    private fun removeCollapsed() { collapsedView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }; collapsedView = null }
}
