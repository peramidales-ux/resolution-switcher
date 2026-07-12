package com.resolution.switcher

import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.resolution.switcher.presets.Preset
import com.resolution.switcher.presets.PresetStorage
import com.resolution.switcher.resolution.ResolutionController
import com.resolution.switcher.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var windowManager: WindowManager
    private lateinit var presetStorage: PresetStorage
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var overlayView: View? = null
    private var collapsedView: View? = null

    private var nativeWidth = 1080
    private var nativeHeight = 2400
    private var nativeDensity = 420
    private var minWidth = 0
    private var maxWidth = 1080
    private var minHeight = 0
    private var maxHeight = 2400

    private var resolutionController: ResolutionController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        presetStorage = PresetStorage(this)

        // Auto-detect access method if not set
        PermissionHelper.hasAnyAccessMethod(this)
        resolutionController = ResolutionController.create(this)
        Log.d("ResSwitcher", "Controller created: ${resolutionController != null}, method: ${PermissionHelper.getAccessMethod(this)}")

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = "v1.0.7"

        val btnGrantOverlay = findViewById<Button>(R.id.btnGrantOverlay)
        val btnUseRoot = findViewById<Button>(R.id.btnUseRoot)
        val btnUseShizuku = findViewById<Button>(R.id.btnUseShizuku)
        val btnSetupShizuku = findViewById<Button>(R.id.btnSetupShizuku)
        val btnStart = findViewById<Button>(R.id.btnStart)

        btnGrantOverlay.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }

        btnUseRoot.setOnClickListener {
            if (PermissionHelper.isRootAvailable()) {
                PermissionHelper.setAccessMethod(this, "root")
                resolutionController = ResolutionController.create(this)
                Toast.makeText(this, "Root выбран", Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }

        btnSetupShizuku.setOnClickListener {
            try {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
                if (intent != null) startActivity(intent)
                else Toast.makeText(this, "Shizuku не установлен", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Shizuku не установлен", Toast.LENGTH_SHORT).show()
            }
        }

        btnUseShizuku.setOnClickListener {
            if (PermissionHelper.isShizukuAvailable()) {
                if (PermissionHelper.isShizukuPermissionGranted()) {
                    PermissionHelper.setAccessMethod(this, "shizuku")
                    resolutionController = ResolutionController.create(this)
                    Toast.makeText(this, "Shizuku выбран", Toast.LENGTH_SHORT).show()
                    updateUI()
                } else {
                    PermissionHelper.requestShizukuPermission()
                }
            } else {
                Toast.makeText(this, "Shizuku не запущен", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            showOverlay()
            Toast.makeText(this, "Оверлей показан!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        findViewById<TextView>(R.id.overlayStatus).apply {
            text = if (hasOverlay) "Предоставлено" else "Не предоставлено"
            setTextColor(getColor(if (hasOverlay) R.color.status_green else R.color.status_red))
        }
        findViewById<Button>(R.id.btnGrantOverlay).isEnabled = !hasOverlay

        val hasRoot = PermissionHelper.isRootAvailable()
        findViewById<TextView>(R.id.rootStatus).apply {
            text = if (hasRoot) "Доступен" else "Недоступен"
            setTextColor(getColor(if (hasRoot) R.color.status_green else R.color.status_red))
        }
        findViewById<Button>(R.id.btnUseRoot).isEnabled = hasRoot

        val hasShizuku = PermissionHelper.isShizukuAvailable()
        val shizukuPerm = PermissionHelper.isShizukuPermissionGranted()
        findViewById<TextView>(R.id.shizukuStatus).apply {
            text = when {
                !hasShizuku -> "Не запущен"
                !shizukuPerm -> "Нет разрешения"
                else -> "Готов"
            }
            setTextColor(getColor(
                if (hasShizuku && shizukuPerm) R.color.status_green else R.color.status_red
            ))
        }
        findViewById<Button>(R.id.btnUseShizuku).isEnabled = hasShizuku

        val method = PermissionHelper.getAccessMethod(this)
        val canStart = hasOverlay && (method == "root" || method == "shizuku")
        findViewById<Button>(R.id.btnStart).isEnabled = canStart
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_panel, null)

        val params = WindowManager.LayoutParams(
            320,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        overlayView!!.tag = params

        try {
            windowManager.addView(overlayView, params)
            setupOverlayListeners(overlayView!!)
            loadNativeResolution()
            Toast.makeText(this, "Оверлей добавлен в WindowManager!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            overlayView = null
        }
    }

    private fun setupOverlayListeners(view: View) {
        val header = view.findViewById<LinearLayout>(R.id.header)
        val btnCollapse = view.findViewById<ImageButton>(R.id.btnCollapse)
        val seekWidth = view.findViewById<SeekBar>(R.id.seekWidth)
        val seekHeight = view.findViewById<SeekBar>(R.id.seekHeight)
        val etWidth = view.findViewById<EditText>(R.id.etWidth)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val btnReset = view.findViewById<Button>(R.id.btnReset)
        val btnSavePreset = view.findViewById<Button>(R.id.btnSavePreset)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupPresets)

        setupDrag(header, view)
        btnCollapse.setOnClickListener { collapseOverlay() }

        seekWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val width = progressToValue(p, minWidth, maxWidth)
                    etWidth.setText(width.toString())
                    debouncedSet(width, getCurrentHeight())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val height = progressToValue(p, minHeight, maxHeight)
                    etHeight.setText(height.toString())
                    debouncedSet(getCurrentWidth(), height)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        etWidth.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.toIntOrNull() ?: return
                if (v in minWidth..maxWidth) {
                    seekWidth.progress = valueToProgress(v, minWidth, maxWidth)
                    debouncedSet(v, getCurrentHeight())
                }
            }
        })

        etHeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.toIntOrNull() ?: return
                if (v in minHeight..maxHeight) {
                    seekHeight.progress = valueToProgress(v, minHeight, maxHeight)
                    debouncedSet(getCurrentWidth(), v)
                }
            }
        })

        btnReset.setOnClickListener {
            scope.launch {
                resolutionController?.resetResolution()
                resolutionController?.resetDensity()
                overlayView?.post {
                    setWidthValue(nativeWidth)
                    setHeightValue(nativeHeight)
                }
            }
        }

        btnSavePreset.setOnClickListener {
            val w = getCurrentWidth()
            val h = getCurrentHeight()
            val builder = android.app.AlertDialog.Builder(this, R.style.Theme_ResolutionSwitcher)
            builder.setTitle("Сохранить пресет")
            val input = EditText(this).apply {
                hint = "Название"
                setPadding(48, 32, 48, 16)
            }
            builder.setView(input)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    presetStorage.save(Preset(name = name, width = w, height = h))
                    loadPresets(chipGroup)
                }
            }
            builder.setNegativeButton(android.R.string.cancel, null)
            builder.show()
        }

        loadPresets(chipGroup)
    }

    private fun setupDrag(header: View, view: View) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        header.setOnTouchListener { _, e ->
            val p = view.tag as WindowManager.LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = p.x; iy = p.y; tx = e.rawX; ty = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    p.x = ix + (e.rawX - tx).toInt()
                    p.y = iy + (e.rawY - ty).toInt()
                    try { windowManager.updateViewLayout(view, p) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    private fun collapseOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null

        collapsedView = View(this).apply { setBackgroundColor(0xFF1976D2.toInt()) }
        val p = WindowManager.LayoutParams(48, 48,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 200 }
        collapsedView!!.tag = p
        collapsedView!!.setOnClickListener { expandOverlay() }
        setupDrag(collapsedView!!, p, true)
        windowManager.addView(collapsedView, p)
    }

    private fun expandOverlay() {
        collapsedView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        collapsedView = null
        showOverlay()
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams, isCollapsed: Boolean = false) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (dx*dx + dy*dy > 100) moved = true
                    params.x = ix + dx.toInt(); params.y = iy + dy.toInt()
                    try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && isCollapsed) expandOverlay()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadNativeResolution() {
        scope.launch {
            resolutionController?.getNativeResolution()?.let { (w, h) ->
                nativeWidth = w; nativeHeight = h
                minWidth = (w * 0.4).toInt(); maxWidth = w
                minHeight = (h * 0.4).toInt(); maxHeight = h
                overlayView?.post {
                    overlayView?.findViewById<TextView>(R.id.tvNativeRes)?.text = "Заводское: ${w}x${h}"
                }
            }
            resolutionController?.getNativeDensity()?.let { dpi ->
                nativeDensity = dpi
                Log.d("ResSwitcher", "Native density: $dpi")
            }
        }
    }

    private fun getCurrentWidth(): Int = overlayView?.findViewById<EditText>(R.id.etWidth)?.text?.toString()?.toIntOrNull() ?: nativeWidth
    private fun getCurrentHeight(): Int = overlayView?.findViewById<EditText>(R.id.etHeight)?.text?.toString()?.toIntOrNull() ?: nativeHeight

    private fun setWidthValue(v: Int) {
        overlayView?.let { it.findViewById<SeekBar>(R.id.seekWidth)?.progress = valueToProgress(v, minWidth, maxWidth)
            it.findViewById<EditText>(R.id.etWidth)?.setText(v.toString()) }
    }
    private fun setHeightValue(v: Int) {
        overlayView?.let { it.findViewById<SeekBar>(R.id.seekHeight)?.progress = valueToProgress(v, minHeight, maxHeight)
            it.findViewById<EditText>(R.id.etHeight)?.setText(v.toString()) }
    }

    private fun progressToValue(p: Int, min: Int, max: Int) = min + ((max - min) * p / 100)
    private fun valueToProgress(v: Int, min: Int, max: Int): Int {
        if (max == min) return 0
        return ((v - min) * 100 / (max - min)).coerceIn(0, 100)
    }

    private var pendingW: Runnable? = null
    private var pendingH: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun debouncedSet(w: Int, h: Int) {
        pendingW?.let { handler.removeCallbacks(it) }
        pendingH?.let { handler.removeCallbacks(it) }
        pendingW = Runnable {
            scope.launch {
                // Calculate proportional density so the image stretches to fill the screen
                val scale = maxOf(w.toFloat() / nativeWidth, h.toFloat() / nativeHeight)
                val newDpi = (nativeDensity * scale).toInt().coerceAtLeast(1)
                Log.d("ResSwitcher", "Calling setResolution($w, $h) + density($newDpi), controller=${resolutionController != null}")
                val resOk = resolutionController?.setResolution(w, h)
                val dpiOk = resolutionController?.setDensity(newDpi)
                Log.d("ResSwitcher", "setResolution=$resOk, setDensity=$dpiOk")
                overlayView?.post {
                    Toast.makeText(this@MainActivity, "Разрешение: $w x $h, density: $newDpi (${if (resOk == true && dpiOk == true) "OK" else "FAIL"})", Toast.LENGTH_SHORT).show()
                }
            }
        }
        handler.postDelayed(pendingW!!, 200)
    }

    private fun loadPresets(chipGroup: ChipGroup) {
        chipGroup.removeAllViews()
        presetStorage.getAll().forEach { preset ->
            val chip = Chip(this).apply {
                text = "${preset.name}\n${preset.width}x${preset.height}"
                isCheckable = false; isCloseIconVisible = true
                setOnClickListener {
                    setWidthValue(preset.width); setHeightValue(preset.height)
                    debouncedSet(preset.width, preset.height)
                }
                setOnCloseIconClickListener {
                    presetStorage.delete(preset.id); chipGroup.removeView(this)
                }
            }
            chipGroup.addView(chip)
        }
    }
}
