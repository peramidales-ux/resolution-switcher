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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.resolution.switcher.presets.Preset
import com.resolution.switcher.presets.PresetStorage
import com.resolution.switcher.resolution.ResolutionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var presetStorage: PresetStorage
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var overlayView: View? = null
    private var collapsedView: View? = null
    private var isCollapsed = false

    private var nativeWidth = 1080
    private var nativeHeight = 2400
    private var minWidth = 0
    private var maxWidth = 1080
    private var minHeight = 0
    private var maxHeight = 2400

    private val handler = Handler(Looper.getMainLooper())
    private var pendingWidthRunnable: Runnable? = null
    private var pendingHeightRunnable: Runnable? = null
    private val DEBOUNCE_MS = 200L

    private var resolutionController: ResolutionController? = null

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
            presetStorage = PresetStorage(this)
            resolutionController = ResolutionController.create(this)

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())

            loadNativeResolution()
            showOverlay()
            Toast.makeText(this, "Resolution Switcher запущен", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сервиса: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CLOSE") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlay()
        removeCollapsedView()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Resolution Switcher",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомление сервиса Resolution Switcher"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val closeIntent = Intent(this, OverlayService::class.java).apply {
            action = "CLOSE"
        }
        val closePendingIntent = PendingIntent.getService(
            this, 0, closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.close_overlay),
                    closePendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private fun loadNativeResolution() {
        serviceScope.launch {
            resolutionController?.getNativeResolution()?.let { (w, h) ->
                nativeWidth = w
                nativeHeight = h
                computeRanges()
            } ?: run {
                nativeWidth = 1080
                nativeHeight = 2400
                computeRanges()
            }
        }
    }

    private fun computeRanges() {
        minWidth = (nativeWidth * 0.4).toInt()
        maxWidth = nativeWidth
        minHeight = (nativeHeight * 0.4).toInt()
        maxHeight = nativeHeight
    }

    private fun showOverlay() {
        try {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_panel, null)

            setupOverlayWindow(overlayView!!)
            setupOverlayListeners(overlayView!!)
            updateNativeResText()

            // Load current resolution
            serviceScope.launch {
                resolutionController?.getCurrentResolution()?.let { (w, h) ->
                    overlayView?.post {
                        setWidthValue(w)
                        setHeightValue(h)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка оверлея: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupOverlayWindow(view: View) {
        val params = WindowManager.LayoutParams(
            320,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }
        view.tag = params
        windowManager.addView(view, params)
    }

    private fun setupOverlayListeners(view: View) {
        val header = view.findViewById<LinearLayout>(R.id.header)
        val content = view.findViewById<LinearLayout>(R.id.content)
        val btnCollapse = view.findViewById<ImageButton>(R.id.btnCollapse)
        val seekWidth = view.findViewById<SeekBar>(R.id.seekWidth)
        val seekHeight = view.findViewById<SeekBar>(R.id.seekHeight)
        val etWidth = view.findViewById<EditText>(R.id.etWidth)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val btnReset = view.findViewById<Button>(R.id.btnReset)
        val btnSavePreset = view.findViewById<Button>(R.id.btnSavePreset)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupPresets)

        // Drag handling
        setupDrag(header, view)

        // Collapse
        btnCollapse.setOnClickListener {
            collapseOverlay()
        }

        // Width SeekBar
        seekWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val width = progressToValue(progress, minWidth, maxWidth)
                    etWidth.setText(width.toString())
                    debouncedSetWidth(width)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Height SeekBar
        seekHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val height = progressToValue(progress, minHeight, maxHeight)
                    etHeight.setText(height.toString())
                    debouncedSetHeight(height)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Width EditText
        etWidth.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toIntOrNull() ?: return
                if (value in minWidth..maxWidth) {
                    seekWidth.progress = valueToProgress(value, minWidth, maxWidth)
                    debouncedSetWidth(value)
                }
            }
        })

        // Height EditText
        etHeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toIntOrNull() ?: return
                if (value in minHeight..maxHeight) {
                    seekHeight.progress = valueToProgress(value, minHeight, maxHeight)
                    debouncedSetHeight(value)
                }
            }
        })

        // Reset button
        btnReset.setOnClickListener {
            serviceScope.launch {
                resolutionController?.resetResolution()
                overlayView?.post {
                    setWidthValue(nativeWidth)
                    setHeightValue(nativeHeight)
                }
            }
        }

        // Save preset
        btnSavePreset.setOnClickListener {
            showSavePresetDialog()
        }

        // Load presets
        loadPresets(chipGroup)
    }

    private fun setupDrag(header: View, view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            val params = view.tag as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun debouncedSetWidth(width: Int) {
        pendingWidthRunnable?.let { handler.removeCallbacks(it) }
        pendingWidthRunnable = Runnable {
            serviceScope.launch {
                resolutionController?.setResolution(width, getCurrentHeight())
            }
        }
        handler.postDelayed(pendingWidthRunnable!!, DEBOUNCE_MS)
    }

    private fun debouncedSetHeight(height: Int) {
        pendingHeightRunnable?.let { handler.removeCallbacks(it) }
        pendingHeightRunnable = Runnable {
            serviceScope.launch {
                resolutionController?.setResolution(getCurrentWidth(), height)
            }
        }
        handler.postDelayed(pendingHeightRunnable!!, DEBOUNCE_MS)
    }

    private fun getCurrentWidth(): Int {
        return overlayView?.findViewById<EditText>(R.id.etWidth)
            ?.text?.toString()?.toIntOrNull() ?: nativeWidth
    }

    private fun getCurrentHeight(): Int {
        return overlayView?.findViewById<EditText>(R.id.etHeight)
            ?.text?.toString()?.toIntOrNull() ?: nativeHeight
    }

    private fun setWidthValue(value: Int) {
        overlayView?.let { view ->
            view.findViewById<SeekBar>(R.id.seekWidth)?.progress =
                valueToProgress(value, minWidth, maxWidth)
            view.findViewById<EditText>(R.id.etWidth)?.setText(value.toString())
        }
    }

    private fun setHeightValue(value: Int) {
        overlayView?.let { view ->
            view.findViewById<SeekBar>(R.id.seekHeight)?.progress =
                valueToProgress(value, minHeight, maxHeight)
            view.findViewById<EditText>(R.id.etHeight)?.setText(value.toString())
        }
    }

    private fun progressToValue(progress: Int, min: Int, max: Int): Int {
        return min + ((max - min) * progress / 100)
    }

    private fun valueToProgress(value: Int, min: Int, max: Int): Int {
        if (max == min) return 0
        return ((value - min) * 100 / (max - min)).coerceIn(0, 100)
    }

    private fun updateNativeResText() {
        overlayView?.findViewById<TextView>(R.id.tvNativeRes)?.text =
            getString(R.string.native_resolution, nativeWidth, nativeHeight)
    }

    private fun collapseOverlay() {
        removeOverlay()
        showCollapsedTab()
        isCollapsed = true
    }

    private fun expandOverlay() {
        removeCollapsedView()
        showOverlay()
        isCollapsed = false
    }

    private fun showCollapsedTab() {
        collapsedView = View(this).apply {
            setBackgroundColor(0xFF1976D2.toInt())
        }

        val params = WindowManager.LayoutParams(
            48, 48,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }

        collapsedView?.tag = params
        collapsedView?.setOnClickListener { expandOverlay() }

        setupDragCollapsed(collapsedView!!, params)
        windowManager.addView(collapsedView, params)
    }

    private fun setupDragCollapsed(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) moved = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        expandOverlay()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun removeCollapsedView() {
        collapsedView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        collapsedView = null
    }

    private fun showSavePresetDialog() {
        val width = getCurrentWidth()
        val height = getCurrentHeight()

        val builder = android.app.AlertDialog.Builder(this, R.style.Theme_ResolutionSwitcher)
        builder.setTitle(R.string.save_preset)

        val input = EditText(this).apply {
            hint = getString(R.string.preset_name_hint)
            setPadding(48, 32, 48, 16)
        }
        builder.setView(input)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                presetStorage.save(Preset(name = name, width = width, height = height))
                overlayView?.let { loadPresets(it.findViewById(R.id.chipGroupPresets)) }
            }
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    private fun loadPresets(chipGroup: ChipGroup) {
        chipGroup.removeAllViews()
        presetStorage.getAll().forEach { preset ->
            val chip = Chip(this).apply {
                text = "${preset.name}\n${preset.width}x${preset.height}"
                isCheckable = false
                isCloseIconVisible = true
                setOnClickListener {
                    setWidthValue(preset.width)
                    setHeightValue(preset.height)
                    serviceScope.launch {
                        resolutionController?.setResolution(preset.width, preset.height)
                    }
                }
                setOnCloseIconClickListener {
                    presetStorage.delete(preset.id)
                    chipGroup.removeView(this)
                }
            }
            chipGroup.addView(chip)
        }
    }
}
