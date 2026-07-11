package com.resolution.switcher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.resolution.switcher.util.PermissionHelper
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var overlayStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var rootStatus: TextView
    private lateinit var btnUseRoot: Button
    private lateinit var shizukuStatus: TextView
    private lateinit var btnSetupShizuku: Button
    private lateinit var btnUseShizuku: Button
    private lateinit var btnStart: Button

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1002) {
            runOnUiThread { updateUI() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayStatus = findViewById(R.id.overlayStatus)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        rootStatus = findViewById(R.id.rootStatus)
        btnUseRoot = findViewById(R.id.btnUseRoot)
        shizukuStatus = findViewById(R.id.shizukuStatus)
        btnSetupShizuku = findViewById(R.id.btnSetupShizuku)
        btnUseShizuku = findViewById(R.id.btnUseShizuku)
        btnStart = findViewById(R.id.btnStart)

        btnGrantOverlay.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }

        btnUseRoot.setOnClickListener {
            if (PermissionHelper.isRootAvailable()) {
                PermissionHelper.setAccessMethod(this, "root")
                Toast.makeText(this, "Root выбран", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                Toast.makeText(this, "Root недоступен", Toast.LENGTH_SHORT).show()
            }
        }

        btnSetupShizuku.setOnClickListener {
            try {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Shizuku не установлен", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Shizuku не установлен", Toast.LENGTH_SHORT).show()
            }
        }

        btnUseShizuku.setOnClickListener {
            if (PermissionHelper.isShizukuAvailable()) {
                if (PermissionHelper.isShizukuPermissionGranted()) {
                    PermissionHelper.setAccessMethod(this, "shizuku")
                    Toast.makeText(this, "Shizuku выбран", Toast.LENGTH_SHORT).show()
                    updateUI()
                } else {
                    PermissionHelper.requestShizukuPermission()
                }
            } else {
                Toast.makeText(this, "Shizuku недоступен. Запустите приложение Shizuku.", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            startOverlayService()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {}
    }

    private fun updateUI() {
        // Overlay permission
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        overlayStatus.text = if (hasOverlay) "Предоставлено" else "Не предоставлено"
        overlayStatus.setTextColor(getColor(if (hasOverlay) R.color.status_green else R.color.status_red))
        btnGrantOverlay.isEnabled = !hasOverlay

        // Root
        val hasRoot = PermissionHelper.isRootAvailable()
        rootStatus.text = if (hasRoot) "Доступен" else "Недоступен"
        rootStatus.setTextColor(getColor(if (hasRoot) R.color.status_green else R.color.status_red))
        btnUseRoot.isEnabled = hasRoot

        // Shizuku
        val hasShizuku = PermissionHelper.isShizukuAvailable()
        val shizukuPerm = PermissionHelper.isShizukuPermissionGranted()
        when {
            !hasShizuku -> {
                shizukuStatus.text = "Не запущен"
                shizukuStatus.setTextColor(getColor(R.color.status_red))
            }
            !shizukuPerm -> {
                shizukuStatus.text = "Запущен, нет разрешения"
                shizukuStatus.setTextColor(getColor(R.color.status_red))
            }
            else -> {
                shizukuStatus.text = "Готов"
                shizukuStatus.setTextColor(getColor(R.color.status_green))
            }
        }
        btnUseShizuku.isEnabled = hasShizuku

        // Start button
        val method = PermissionHelper.getAccessMethod(this)
        val canStart = hasOverlay && (method == "root" || method == "shizuku")
        btnStart.isEnabled = canStart
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        finish()
    }
}
