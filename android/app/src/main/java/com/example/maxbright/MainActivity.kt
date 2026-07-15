package com.example.maxbright

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * MaxBright pins the screen to the maximum brightness a non-rooted app can reach.
 *
 * What it does, in order of impact:
 *  1. Forces this window's brightness to 1.0 (full). This overrides the system
 *     slider while the app is in the foreground and needs no permission.
 *  2. Keeps the screen awake so it never dims on its own.
 *  3. If the user grants WRITE_SETTINGS, it also raises the *system* brightness
 *     to 255 and switches auto-brightness off, so the effect is stronger and
 *     more consistent across manufacturers.
 *
 * The honest ceiling: without root an app cannot drive the backlight past the
 * hardware maximum. On many devices (Samsung, Pixel, Xiaomi...) holding window
 * brightness at 1.0 in bright ambient light lets the OEM engage High Brightness
 * Mode (HBM / "outdoor mode") automatically, which is the only way to go above
 * the manual slider maximum without root. The app enables that path; it cannot
 * force HBM itself.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var maxSwitch: SwitchCompat
    private lateinit var grantButton: Button

    /** Whether we changed the system brightness, so we can restore it on exit. */
    private var previousSystemBrightness: Int? = null
    private var previousSystemMode: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        maxSwitch = findViewById(R.id.maxSwitch)
        grantButton = findViewById(R.id.grantButton)

        maxSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            if (isChecked) enableMax() else disableMax()
        }

        grantButton.setOnClickListener { requestWriteSettings() }

        // Start maxed out immediately.
        maxSwitch.isChecked = true
    }

    override fun onResume() {
        super.onResume()
        // Re-apply on resume; the system may have reset the window brightness.
        if (maxSwitch.isChecked) enableMax()
        updateStatus()
    }

    private fun enableMax() {
        // 1. Force window brightness to full and keep the screen on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL // 1.0f
        window.attributes = lp

        // 2. If allowed, also push the system brightness to the maximum.
        if (canWriteSettings()) {
            raiseSystemBrightnessToMax()
        }
        updateStatus()
    }

    private fun disableMax() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE // -1f
        window.attributes = lp

        restoreSystemBrightness()
        updateStatus()
    }

    private fun raiseSystemBrightnessToMax() {
        try {
            val resolver = contentResolver
            if (previousSystemBrightness == null) {
                previousSystemBrightness = Settings.System.getInt(
                    resolver, Settings.System.SCREEN_BRIGHTNESS, 128
                )
                previousSystemMode = Settings.System.getInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            }
            // Auto-brightness must be off or the system overrides our value.
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 255)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the write.
            Toast.makeText(this, R.string.need_permission, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreSystemBrightness() {
        if (!canWriteSettings()) return
        try {
            previousSystemBrightness?.let {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, it)
            }
            previousSystemMode?.let {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it)
            }
        } catch (_: SecurityException) {
            // Ignore: nothing to restore if we lost the permission.
        } finally {
            previousSystemBrightness = null
            previousSystemMode = null
        }
    }

    private fun canWriteSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(this)
        } else {
            // Prior to Android 6.0 the manifest permission is enough.
            true
        }
    }

    private fun requestWriteSettings() {
        if (canWriteSettings()) {
            raiseSystemBrightnessToMax()
            updateStatus()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun updateStatus() {
        val maxed = maxSwitch.isChecked
        val system = if (canWriteSettings()) {
            getString(R.string.status_system_on)
        } else {
            getString(R.string.status_system_off)
        }
        grantButton.isEnabled = !canWriteSettings()
        statusText.text = if (maxed) {
            getString(R.string.status_on, system)
        } else {
            getString(R.string.status_off)
        }
    }

    override fun onDestroy() {
        // Leave the system as we found it when the app closes.
        restoreSystemBrightness()
        super.onDestroy()
    }
}
