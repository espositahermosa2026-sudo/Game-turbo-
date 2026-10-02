package com.tuusuario.gameturbo

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var panelVisible = false

    // ---- Colores (tema verde/teal) ----
    private val tealInt = AColor.parseColor("#1DE9B6")
    private val panelBg = AColor.parseColor("#E60F1A18")
    private val grayText = AColor.parseColor("#CFD8DC")

    // Columnas de cada grupo de iconos (2 = cuadro 2x2, 4 = una sola fila, mas delgado)
    private val GRID_COLS = 2

    // Funciones que se quedan "encendidas" (se resaltan en verde)
    private val toggleLabels = setOf(
        "Alto rend.",
        "Bloq. llamadas",
        "Bloq. notif.",
        "Sin vibración",
        "Bloq. gestos",
        "Info real"
    )

    private val toggleStates = mutableMapOf(
        "Liberar RAM" to false,
        "Alto rend." to false,
        "Bloq. llamadas" to false,
        "Bloq. notif." to false,
        "Sin vibración" to false
    )

    // Valores del medidor central. FPS y CPU se conectan despues con Shizuku.
    private var currentFps = "--"
    private var currentCpu = "--"

    private val handler = Handler(Looper.getMainLooper())
    private var statsView: StatsView? = null
    private val statsRunnable = object : Runnable {
        override fun run() {
            refreshStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

    private fun refreshStats() {
        statsView?.let {
            it.ram = getRamUsagePercent()
            it.fps = currentFps
            it.cpu = currentCpu
            it.invalidate()
        }
    }

    // ------------------------------------------------------------------
    // Burbuja (cuadrito de 1cm)
    // ------------------------------------------------------------------
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
                setColor(tealInt)
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
            handler.removeCallbacks(statsRunnable)
            statsView = null
            panelView?.let { windowManager.removeView(it) }
            panelVisible = false
        } else {
            showPanel()
            panelVisible = true
            handler.post(statsRunnable)
        }
    }

    // ------------------------------------------------------------------
    // Logica de funciones (sin cambios)
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // Fondo con esquinas cortadas (como la referencia)
    // ------------------------------------------------------------------
    private class ChamferDrawable(
        fillColor: Int,
        strokeColor: Int,
        private val cut: Float,
        private val strokeW: Float
    ) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = strokeW
        }
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val half = strokeW / 2f
            val l = bounds.left + half
            val t = bounds.top + half
            val r = bounds.right - half
            val b = bounds.bottom - half
            path.reset()
            path.moveTo(l + cut, t)
            path.lineTo(r - cut, t)
            path.lineTo(r, t + cut)
            path.lineTo(r, b - cut)
            path.lineTo(r - cut, b)
            path.lineTo(l + cut, b)
            path.lineTo(l, b - cut)
            path.lineTo(l, t + cut)
            path.close()
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ------------------------------------------------------------------
    // Medidor central: RAM / FPS / CPU
    // ------------------------------------------------------------------
    private inner class StatsView(context: Context) : View(context) {
        var ram = 0
        var fps = "--"
        var cpu = "--"

        private val d = resources.displayMetrics.density

        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.parseColor("#661DE9B6")
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealInt
            style = Paint.Style.FILL
        }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealInt
            textSize = 22f * d
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.WHITE
            textSize = 30f * d
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = grayText
            textSize = 11f * d
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // marco redondeado
            canvas.drawRoundRect(
                2f * d, 6f * d, w - 2f * d, h - 6f * d,
                16f * d, 16f * d, framePaint
            )

            // barritas laterales (5 por lado, se llenan segun la RAM)
            val filled = ((ram / 100f) * 5f).toInt().coerceIn(0, 5)
            val barW = 6f * d
            val barH = 10f * d
            val gap = 4f * d
            val totalH = 5 * barH + 4 * gap
            val startY = (h - totalH) / 2f
            for (i in 0 until 5) {
                val top = startY + (4 - i) * (barH + gap)
                barPaint.alpha = if (i < filled) 255 else 60
                canvas.drawRoundRect(0f, top, barW, top + barH, 3f * d, 3f * d, barPaint)
                canvas.drawRoundRect(w - barW, top, w, top + barH, 3f * d, 3f * d, barPaint)
            }

            val c1 = w * 0.2f
            val c2 = w * 0.5f
            val c3 = w * 0.8f
            val valueY = h * 0.55f
            val labelY = h * 0.78f

            canvas.drawText("$ram%", c1, valueY, valuePaint)
            canvas.drawText(fps, c2, valueY + 3f * d, fpsPaint)
            canvas.drawText(if (cpu == "--") cpu else "$cpu%", c3, valueY, valuePaint)

            canvas.drawText("RAM", c1, labelY, labelPaint)
            canvas.drawText("FPS", c2, labelY, labelPaint)
            canvas.drawText("CPU", c3, labelY, labelPaint)
        }
    }

    // ------------------------------------------------------------------
    // Item de funcion (circulo + icono + texto)
    // ------------------------------------------------------------------
    private fun circleBg(active: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) AColor.parseColor("#331DE9B6") else AColor.parseColor("#22FFFFFF"))
            setStroke(
                dp(2),
                if (active) tealInt else AColor.parseColor("#66FFFFFF")
            )
        }

    private fun buildItem(iconRes: Int, label: String): View {
        val canToggle = label in toggleLabels
        val startActive = canToggle && (toggleStates[label] == true)

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(62)
                height = dp(62)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }

        val circle = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            background = circleBg(startActive)
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(if (startActive) tealInt else grayText)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        }
        circle.addView(icon)

        val text = TextView(this).apply {
            this.text = label
            textSize = 9f
            setTextColor(grayText)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(2), 0, 0)
        }

        item.addView(circle)
        item.addView(text)

        item.setOnClickListener {
            runFunction(label)
            if (canToggle) {
                val active = toggleStates[label] == true
                circle.background = circleBg(active)
                icon.setColorFilter(if (active) tealInt else grayText)
            }
            if (label == "Liberar RAM") {
                handler.postDelayed({ refreshStats() }, 1500)
            }
        }
        return item
    }

    private fun buildGrid(items: List<Pair<Int, String>>): GridLayout {
        val grid = GridLayout(this).apply {
            columnCount = GRID_COLS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        items.forEach { (iconRes, label) -> grid.addView(buildItem(iconRes, label)) }
        return grid
    }

    // ------------------------------------------------------------------
    // Sliders (brillo y volumen)
    // ------------------------------------------------------------------
    private fun buildSlider(
        symbol: String,
        maxValue: Int,
        startValue: Int,
        onChange: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(dp(4), 0, dp(4), 0) }
        }
        val sym = TextView(this).apply {
            text = symbol
            textSize = 14f
            setTextColor(tealInt)
        }
        val bar = SeekBar(this).apply {
            this.max = maxValue
            this.progress = startValue
            progressTintList = ColorStateList.valueOf(tealInt)
            thumbTintList = ColorStateList.valueOf(tealInt)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        row.addView(sym)
        row.addView(bar)
        return row
    }

    // ------------------------------------------------------------------
    // Panel
    // ------------------------------------------------------------------
    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ChamferDrawable(
                panelBg, tealInt, dp(14).toFloat(), dp(2).toFloat()
            )
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }

        // ---- Fila principal: izquierda | medidor | derecha ----
        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val leftGrid = buildGrid(
            listOf(
                R.drawable.ic_ram to "Liberar RAM",
                R.drawable.ic_cpu to "Alto rend.",
                R.drawable.ic_call_off to "Bloq. llamadas",
                R.drawable.ic_bell_off to "Bloq. notif."
            )
        )

        val stats = StatsView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(84)).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
        }
        statsView = stats

        val rightGrid = buildGrid(
            listOf(
                R.drawable.ic_vibrate_off to "Sin vibración",
                R.drawable.ic_gesture_off to "Bloq. gestos",
                R.drawable.ic_chart to "Info real",
                R.drawable.ic_record to "Grabar"
            )
        )

        mainRow.addView(leftGrid)
        mainRow.addView(stats)
        mainRow.addView(rightGrid)
        root.addView(mainRow)

        // ---- Fila de abajo: brillo | volumen | cerrar ----
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val curBright = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (e: Exception) {
            128
        }

        val sliders = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        sliders.addView(buildSlider("☀", 255, curBright) { v ->
            // Necesita el permiso "Modificar ajustes del sistema"; si no lo tiene, no hace nada
            try {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
            } catch (e: Exception) {
            }
        })

        sliders.addView(buildSlider("🔊", maxVol, curVol) { v ->
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            } catch (e: Exception) {
            }
        })

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(tealInt)
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener { togglePanel() }
        }
        sliders.addView(closeBtn)
        root.addView(sliders)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = dp(4)

        panelView = root
        windowManager.addView(root, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statsRunnable)
        bubbleView?.let { windowManager.removeView(it) }
        if (panelVisible) panelView?.let { windowManager.removeView(it) }
    }
}
