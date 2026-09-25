package com.tuusuario.gameturbo

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var panelVisible = false

    private val orange = "#FFA726"
    private val darkBg = "#EE141414"
    private val darkCard = "#B3222222"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun cmToPx(cm: Float): Int {
        val density = resources.displayMetrics.density
        return (cm * (160f / 2.54f) * density).toInt()
    }

    private fun getRamUsagePercent(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        return ((used.toFloat() / info.totalMem.toFloat()) * 100).toInt()
    }

    private fun showBubble() {
        val sizePx = cmToPx(1f)

        val bubble = TextView(this).apply {
            text = "⚡"
            textSize = 14f
            setTextColor(AColor.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(0f, 0f, 20f, 20f, 20f, 20f, 0f, 0f)
                setColor(AColor.parseColor(orange))
            }
        }

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
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
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(bubble, params)
                        moved = true
                    }
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

    private fun runFunction(label: String) {
        Thread {
            when (label) {
                "Liberar RAM" -> ShizukuHelper.freeRam()
                "Alto rend." -> ShizukuHelper.highPerformance(true)
                "Bloq. llamadas" -> ShizukuHelper.blockCalls(true)
                "Bloq. notif." -> ShizukuHelper.blockNotifications(true)
            }
        }.start()
    }

    private inner class GaugeView(context: Context, private val percent: Int) : View(context) {
        private val bgPaint = Paint().apply {
            color = AColor.parseColor("#333333")
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        private val fgPaint = Paint().apply {
            color = AColor.parseColor(orange)
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint().apply {
            color = AColor.WHITE
            textSize = 26f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        private val labelPaint = Paint().apply {
            color = AColor.parseColor(orange)
            textSize = 14f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = 14f
            val rect = RectF(stroke, stroke, width - stroke, height - stroke)
            canvas.drawArc(rect, 135f, 270f, false, bgPaint)
            canvas.drawArc(rect, 135f, 270f * (percent / 100f), false, fgPaint)
            canvas.drawText("$percent%", width / 2f, height / 2f, textPaint)
            canvas.drawText("RAM", width / 2f, height / 2f + 28f, labelPaint)
        }
    }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(AColor.parseColor(darkBg))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "GAMEPLAY"
            textSize = 14f
            setTextColor(AColor.parseColor(orange))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(AColor.parseColor(orange))
            setPadding(16, 8, 16, 8)
            setOnClickListener { togglePanel() }
        }
        titleRow.addView(title)
        titleRow.addView(closeBtn)
        root.addView(titleRow)

        val gaugeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 160
            )
        }
        val gauge = GaugeView(this, getRamUsagePercent()).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160)
        }
        gaugeContainer.addView(gauge)
        root.addView(gaugeContainer)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val leftList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val listFunctions = listOf(
            R.drawable.ic_ram to "Liberar RAM",
            R.drawable.ic_cpu to "Alto rend.",
            R.drawable.ic_call_off to "Bloq. llamadas",
            R.drawable.ic_bell_off to "Bloq. notif."
        )

        listFunctions.forEach { (iconRes, label) ->
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 18, 8, 18)
                setOnClickListener { runFunction(label) }
            }
            val iconView = ImageView(this).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 20
                }
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(AColor.parseColor(orange))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val dotsView = ImageView(this).apply {
                setImageResource(R.drawable.ic_dots)
                layoutParams = LinearLayout.LayoutParams(32, 32)
            }
            itemRow.addView(iconView)
            itemRow.addView(labelView)
            itemRow.addView(dotsView)
            leftList.addView(itemRow)
        }

        val rightGrid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val gridFunctions = listOf(
            R.drawable.ic_vibrate_off to "Sin vibración",
            R.drawable.ic_gesture_off to "Bloq. gestos",
            R.drawable.ic_chart to "Info real",
            R.drawable.ic_record to "Grabar"
        )

        gridFunctions.forEach { (iconRes, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 140
                    height = 140
                    setMargins(6, 6, 6, 6)
                }
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(AColor.parseColor(darkCard))
                }
                setOnClickListener { runFunction(label) }
            }
            val iconView = ImageView(this).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(40, 40)
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 8f
                setTextColor(AColor.parseColor(orange))
                gravity = Gravity.CENTER
                setPadding(2, 6, 2, 0)
            }
            item.addView(iconView)
            item.addView(labelView)
            rightGrid.addView(item)
        }

        row.addView(leftList)
        row.addView(rightGrid)
        root.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        panelView = root
        windowManager.addView(root, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        if (panelVisible) panelView?.let { windowManager.removeView(it) }
    }
}
cat > ~/GameTurbo/app/src/main/java/com/tuusuario/gameturbo/OverlayService.kt << 'EOF'
package com.tuusuario.gameturbo

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var panelVisible = false

    private val orange = "#FFA726"
    private val darkBg = "#EE141414"
    private val darkCard = "#B3222222"

    private val toggleStates = mutableMapOf(
        "Liberar RAM" to false,
        "Alto rend." to false,
        "Bloq. llamadas" to false,
        "Bloq. notif." to false,
        "Sin vibración" to false
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun cmToPx(cm: Float): Int {
        val density = resources.displayMetrics.density
        return (cm * (160f / 2.54f) * density).toInt()
    }

    private fun getRamUsagePercent(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        return ((used.toFloat() / info.totalMem.toFloat()) * 100).toInt()
    }

    private fun showBubble() {
        val sizePx = cmToPx(1f)

        val bubble = TextView(this).apply {
            text = "⚡"
            textSize = 14f
            setTextColor(AColor.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(0f, 0f, 20f, 20f, 20f, 20f, 0f, 0f)
                setColor(AColor.parseColor(orange))
            }
        }

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
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
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(bubble, params)
                        moved = true
                    }
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

    private fun runFunction(label: String) {
        val newState = !(toggleStates[label] ?: false)
        toggleStates[label] = newState
        Thread {
            when (label) {
                "Liberar RAM" -> ShizukuHelper.freeRam()
                "Alto rend." -> ShizukuHelper.highPerformance(newState)
                "Bloq. llamadas" -> ShizukuHelper.blockCalls(newState)
                "Bloq. notif." -> ShizukuHelper.blockNotifications(newState)
                "Sin vibración" -> ShizukuHelper.toggleVibration(newState)
            }
        }.start()
    }

    private inner class GaugeView(context: Context, private val percent: Int) : View(context) {
        private val bgPaint = Paint().apply {
            color = AColor.parseColor("#333333")
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        private val fgPaint = Paint().apply {
            color = AColor.parseColor(orange)
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint().apply {
            color = AColor.WHITE
            textSize = 26f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        private val labelPaint = Paint().apply {
            color = AColor.parseColor(orange)
            textSize = 14f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = 14f
            val rect = RectF(stroke, stroke, width - stroke, height - stroke)
            canvas.drawArc(rect, 135f, 270f, false, bgPaint)
            canvas.drawArc(rect, 135f, 270f * (percent / 100f), false, fgPaint)
            canvas.drawText("$percent%", width / 2f, height / 2f, textPaint)
            canvas.drawText("RAM", width / 2f, height / 2f + 28f, labelPaint)
        }
    }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(AColor.parseColor(darkBg))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "GAMEPLAY"
            textSize = 14f
            setTextColor(AColor.parseColor(orange))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(AColor.parseColor(orange))
            setPadding(16, 8, 16, 8)
            setOnClickListener { togglePanel() }
        }
        titleRow.addView(title)
        titleRow.addView(closeBtn)
        root.addView(titleRow)

        val gaugeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 160
            )
        }
        val gauge = GaugeView(this, getRamUsagePercent()).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160)
        }
        gaugeContainer.addView(gauge)
        root.addView(gaugeContainer)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val leftList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val listFunctions = listOf(
            R.drawable.ic_ram to "Liberar RAM",
            R.drawable.ic_cpu to "Alto rend.",
            R.drawable.ic_call_off to "Bloq. llamadas",
            R.drawable.ic_bell_off to "Bloq. notif."
        )

        listFunctions.forEach { (iconRes, label) ->
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 18, 8, 18)
                setOnClickListener { runFunction(label) }
            }
            val iconView = ImageView(this).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 20
                }
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(AColor.parseColor(orange))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val dotsView = ImageView(this).apply {
                setImageResource(R.drawable.ic_dots)
                layoutParams = LinearLayout.LayoutParams(32, 32)
            }
            itemRow.addView(iconView)
            itemRow.addView(labelView)
            itemRow.addView(dotsView)
            leftList.addView(itemRow)
        }

        val rightGrid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val gridFunctions = listOf(
            R.drawable.ic_vibrate_off to "Sin vibración",
            R.drawable.ic_gesture_off to "Bloq. gestos",
            R.drawable.ic_chart to "Info real",
            R.drawable.ic_record to "Grabar"
        )

        gridFunctions.forEach { (iconRes, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 140
                    height = 140
                    setMargins(6, 6, 6, 6)
                }
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(AColor.parseColor(darkCard))
                }
                setOnClickListener { runFunction(label) }
            }
            val iconView = ImageView(this).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(40, 40)
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 8f
                setTextColor(AColor.parseColor(orange))
                gravity = Gravity.CENTER
                setPadding(2, 6, 2, 0)
            }
            item.addView(iconView)
            item.addView(labelView)
            rightGrid.addView(item)
        }

        row.addView(leftList)
        row.addView(rightGrid)
        root.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        panelView = root
        windowManager.addView(root, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        if (panelVisible) panelView?.let { windowManager.removeView(it) }
    }
}
EOF
