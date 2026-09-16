package com.tuusuario.gameturbo

import android.app.Service
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var panelVisible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun showBubble() {
        val bubble = TextView(this).apply {
            text = "⚡"
            textSize = 22f
            setTextColor(AColor.WHITE)
            setBackgroundColor(AColor.parseColor("#9D4EDD"))
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        val params = WindowManager.LayoutParams(
            130, 130,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(bubble, params)
                    moved = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        windowManager.addView(bubble, params)
    }

    private fun togglePanel() {
        if (panelVisible) {
            panelView?.let { windowManager.removeView(it) }
            panelVisible = false
        } else {
            showPanel()
            panelVisible = true
        }
    }

    private fun showPanel() {
        val grid = GridLayout(this).apply {
            columnCount = 4
            setBackgroundColor(AColor.parseColor("#E60A0A0F"))
            setPadding(24, 24, 24, 24)
        }

        val functions = listOf(
            "Liberar RAM", "Alto rendimiento", "Bloquear llamadas", "Bloquear notif.",
            "Sin vibracion", "Bloquear gestos", "Info tiempo real", "Grabar pantalla"
        )

        functions.forEach { label ->
            val item = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(AColor.parseColor("#E0AAFF"))
                gravity = Gravity.CENTER
                setPadding(16, 24, 16, 24)
                setBackgroundColor(AColor.parseColor("#17121F"))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 150
                    height = 150
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    if (label == "Liberar RAM") {
                        Thread {
                            ShizukuHelper.freeRam()
                        }.start()
                    }
                }
            }
            grid.addView(item)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 350

        panelView = grid
        windowManager.addView(grid, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        if (panelVisible) panelView?.let { windowManager.removeView(it) }
    }
}
