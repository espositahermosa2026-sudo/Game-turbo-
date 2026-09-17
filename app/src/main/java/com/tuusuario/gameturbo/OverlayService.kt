package com.tuusuario.gameturbo

import android.app.Service
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
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
            textSize = 20f
            setTextColor(AColor.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AColor.parseColor("#CC9D4EDD"))
            }
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }

        val params = WindowManager.LayoutParams(
            110, 110,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

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
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(AColor.parseColor("#CC0A0A0F"))
            }
            setPadding(20, 20, 20, 20)
        }

        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
        }

        val functions = listOf(
            "⚡" to "Liberar RAM",
            "🚀" to "Alto rend.",
            "📵" to "Bloq. llamadas",
            "🔕" to "Bloq. notif.",
            "📳" to "Sin vibración",
            "✋" to "Bloq. gestos",
            "📊" to "Info tiempo real",
            "⏺" to "Grabar pantalla"
        )

        functions.forEach { (icon, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 130
                    height = 130
                    setMargins(6, 6, 6, 6)
                }
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(AColor.parseColor("#B317121F"))
                }
                setOnClickListener {
                    Thread {
                        when (label) {
                            "Liberar RAM" -> ShizukuHelper.freeRam()
                            "Alto rend." -> ShizukuHelper.highPerformance(true)
                            "Bloq. llamadas" -> ShizukuHelper.blockCalls(true)
                            "Bloq. notif." -> ShizukuHelper.blockNotifications(true)
                        }
                    }.start()
                }
            }

            val iconView = TextView(this).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 8f
                setTextColor(AColor.parseColor("#E0AAFF"))
                gravity = Gravity.CENTER
                setPadding(2, 4, 2, 0)
            }

            item.addView(iconView)
            item.addView(labelView)
            grid.addView(item)
        }

        outer.addView(grid)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 250

        panelView = outer
        windowManager.addView(outer, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        if (panelVisible) panelView?.let { windowManager.removeView(it) }
    }
}
