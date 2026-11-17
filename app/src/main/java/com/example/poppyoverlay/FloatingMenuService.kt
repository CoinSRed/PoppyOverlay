package com.example.poppyoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingMenuService : Service() {

    companion object {
        const val PREFS_NAME = "poppy_menu_prefs"
        const val KEY_SPEED = "player_speed"
        const val KEY_FLY = "fly"
        const val KEY_NOCLIP = "noclip"
        const val KEY_ESP = "esp"
        const val KEY_ENEMY_SLOW = "enemy_slow"
        const val KEY_FREEZE = "freeze_enemies"
        const val KEY_GAME_SPEED = "game_speed"
        private const val CHANNEL_ID = "poppy_overlay_channel"
        private const val NOTIF_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var iconView: View? = null
    private var panelView: View? = null
    private var panelVisible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        startAsForeground()
        createIcon()
        createPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        iconView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        iconView = null
        panelView = null
    }

    // ---------- Foreground notification ----------
    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Floating menu", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Poppy Menu running")
            .setContentText("Tap the ⚙️ icon to open the menu")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

    // ---------- Floating draggable icon ----------
    private fun createIcon() {
        val themed = ContextThemeWrapper(this, android.R.style.Theme_Material)
        val view = LayoutInflater.from(themed).inflate(R.layout.layout_floating_button, null)
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = abs(event.rawX - touchX)
                        val dy = abs(event.rawY - touchY)
                        if (dx < 10 && dy < 10) togglePanel()
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(view, params)
        iconView = view
    }

    // ---------- Dark menu panel ----------
    private fun createPanel() {
        val themed = ContextThemeWrapper(this, android.R.style.Theme_Material)
        val view = LayoutInflater.from(themed).inflate(R.layout.layout_menu_panel, null)

        val maxHeight = (resources.displayMetrics.heightPixels * 0.75f).toInt()
        val params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, maxHeight).apply {
            gravity = Gravity.CENTER
        }

        // Sliders
        bindSlider(view, R.id.sbSpeed, R.id.tvSpeed, KEY_SPEED, 10, "Player Speed") {
            "%.1fx".format(1f + it / 100f * 9f)          // 1x - 10x
        }
        bindSlider(view, R.id.sbEnemySlow, R.id.tvEnemySlow, KEY_ENEMY_SLOW, 0, "Enemy Slow") {
            "$it%"                                        // 0% - 100%
        }
        bindSlider(view, R.id.sbGameSpeed, R.id.tvGameSpeed, KEY_GAME_SPEED, 31, "Game Speed") {
            "%.2fx".format(0.1f + it / 100f * 2.9f)       // 0.1x - 3x
        }

        // Switches
        val swFly = view.findViewById<Switch>(R.id.swFly)
        val swNoclip = view.findViewById<Switch>(R.id.swNoclip)
        val swEsp = view.findViewById<Switch>(R.id.swEsp)
        val swFreeze = view.findViewById<Switch>(R.id.swFreeze)
        swFly.isChecked = prefs.getBoolean(KEY_FLY, false)
        swNoclip.isChecked = prefs.getBoolean(KEY_NOCLIP, false)
        swEsp.isChecked = prefs.getBoolean(KEY_ESP, false)
        swFreeze.isChecked = prefs.getBoolean(KEY_FREEZE, false)

        // Save
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putInt(KEY_SPEED, view.findViewById<SeekBar>(R.id.sbSpeed).progress)
                .putBoolean(KEY_FLY, swFly.isChecked)
                .putBoolean(KEY_NOCLIP, swNoclip.isChecked)
                .putBoolean(KEY_ESP, swEsp.isChecked)
                .putInt(KEY_ENEMY_SLOW, view.findViewById<SeekBar>(R.id.sbEnemySlow).progress)
                .putBoolean(KEY_FREEZE, swFreeze.isChecked)
                .putInt(KEY_GAME_SPEED, view.findViewById<SeekBar>(R.id.sbGameSpeed).progress)
                .apply()
            Toast.makeText(this, "Settings saved ✅", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnClose).setOnClickListener { togglePanel() }

        view.visibility = View.GONE
        windowManager.addView(view, params)
        panelView = view
    }

    private fun bindSlider(
        root: View, sbId: Int, tvId: Int, key: String, default: Int,
        title: String, format: (Int) -> String
    ) {
        val sb = root.findViewById<SeekBar>(sbId)
        val tv = root.findViewById<TextView>(tvId)
        val saved = prefs.getInt(key, default)
        sb.progress = saved
        tv.text = "$title: ${format(saved)}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                tv.text = "$title: ${format(p)}"
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun togglePanel() {
        panelVisible = !panelVisible
        panelView?.visibility = if (panelVisible) View.VISIBLE else View.GONE
    }
}
