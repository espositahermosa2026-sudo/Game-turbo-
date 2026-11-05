package com.tuusuario.gameturbo

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var panelVisible = false
    private var mira: MiraManager? = null
    private var rotView: View? = null
    private var info: InfoManager? = null
    private var km: KmManager? = null
    private var apps: AppsManager? = null
    private var backView: View? = null
    private var msgView: TextView? = null

    private val tealInt = AColor.parseColor("#FF102A")
    private val grayText = AColor.parseColor("#FFFFFF")

    // La pestaña ya no es una línea de 4 dp: ahora es un botón visible.
    private val BAR_WIDTH_DP = 46
    private val BAR_HEIGHT_CM = 2.8f

    private val toggleLabels = setOf(
        "Alto rend.",
        "Girar pantalla",
        "Crosshair Assistant",
        "Bloq. notif.",
        "Bloq. gestos",
        "Info real",
        "Grabar",
        "Edit Keymap"
    )
    private val toggleStates = mutableMapOf<String, Boolean>()
    private val handler = Handler(Looper.getMainLooper())
    private var statsView: HudGauges? = null

    private val statsRunnable = object : Runnable {
        override fun run() {
            refreshStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mira = MiraManager(this, windowManager)
        info = InfoManager(this, windowManager)
        km = KmManager(this, windowManager)
        apps = AppsManager(this, windowManager)
        showBubble()

        Thread {
            ShizukuHelper.runCommand(
                "cmd appops set $packageName RUN_IN_BACKGROUND allow; " +
                    "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow; " +
                    "cmd deviceidle whitelist +$packageName; " +
                    "am set-standby-bucket $packageName active"
            )
        }.start()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun cmToPx(cm: Float): Int {
        val density = resources.displayMetrics.density
        return (cm * (160f / 2.54f) * density).toInt()
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun getRamUsagePercent(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memoryInfo)
        val used = memoryInfo.totalMem - memoryInfo.availMem
        return ((used.toFloat() / memoryInfo.totalMem.toFloat()) * 100).toInt()
    }

    private fun refreshStats() {
        statsView?.let {
            it.ram = getRamUsagePercent()
            it.fps = ShizukuHelper.fps
            it.cpu = ShizukuHelper.cpu
            it.invalidate()
        }
    }

    /**
     * Pestaña flotante visible. Se puede arrastrar vertical y horizontalmente;
     * un toque sin arrastre abre o cierra el HUD.
     */
    private fun showBubble() {
        val bubbleWidth = dp(BAR_WIDTH_DP)
        val bubbleHeight = cmToPx(BAR_HEIGHT_CM)

        val container = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(17).toFloat()
                setColor(AColor.parseColor("#E9101016"))
                setStroke(dp(2), tealInt)
            }
            elevation = dp(10).toFloat()
            clipToOutline = true
            contentDescription = "Abrir panel GAME PLAY"
        }

        // Emblema provisional visible; se puede sustituir luego por el dragón.
        val logo = TextView(this).apply {
            text = "G"
            gravity = Gravity.CENTER
            setTextColor(AColor.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            typeface = android.graphics.Typeface.create(
                "sans-serif-black",
                android.graphics.Typeface.BOLD
            )
            setShadowLayer(dp(7).toFloat(), 0f, 0f, tealInt)
        }

        val accent = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(tealInt)
            }
        }

        container.addView(
            logo,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(
            accent,
            FrameLayout.LayoutParams(
                dp(22),
                dp(3),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(8)
            }
        )

        val params = WindowManager.LayoutParams(
            bubbleWidth,
            bubbleHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(180)
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
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

                    if (kotlin.math.abs(dx) > dp(5) ||
                        kotlin.math.abs(dy) > dp(5)
                    ) {
                        moved = true
                    }

                    if (moved) {
                        val (screenW, screenH) = screenSize()
                        params.x = (initialX + dx.toInt())
                            .coerceIn(0, (screenW - bubbleWidth).coerceAtLeast(0))
                        params.y = (initialY + dy.toInt())
                            .coerceIn(0, (screenH - bubbleHeight).coerceAtLeast(0))
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (_: Exception) {
                            // La vista pudo haberse retirado al mismo tiempo.
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        bubbleView = container
        windowManager.addView(container, params)
    }

    private fun togglePanel() {
        if (panelVisible) {
            handler.removeCallbacks(statsRunnable)
            ShizukuHelper.stopStats()
            msgView = null
            statsView = null
            panelView?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {
                }
            }
            panelView = null
            panelVisible = false
        } else {
            showPanel()
            panelVisible = true
            ShizukuHelper.startStats()
            handler.post(statsRunnable)
        }
    }

    private fun runFunction(label: String, done: () -> Unit = {}) {
        val newState = !(toggleStates[label] ?: false)
        toggleStates[label] = newState

        if (label == "Crosshair Assistant") {
            mira?.setVisible(newState)
            return
        }
        if (label == "Apps flotantes") {
            if (panelVisible) togglePanel()
            apps?.show()
            return
        }
        if (label == "Edit Keymap") {
            km?.setVisible(newState)
            msgView?.text = if (newState) {
                "Edit Keymap activo (mantén presionado para editar)"
            } else {
                "Edit Keymap apagado"
            }
            return
        }
        if (label == "Info real") {
            info?.setVisible(newState)
            return
        }
        if (label == "Bloq. gestos") {
            setBackBlock(newState)
            msgView?.text = if (newState) {
                "Bloq. gestos: gesto de regresar bloqueado"
            } else {
                "Bloq. gestos: desactivado"
            }
            if (newState) Thread {
                ShizukuHelper.blockGestures(false)
            }.start()
            return
        }
        if (label == "Girar pantalla") {
            setRotation(newState)
            return
        }

        msgView?.text = "$label..."
        Thread {
            val msg: String? = when (label) {
                "Limpiador de RAM" -> ShizukuHelper.cleanRam()
                "Alto rend." -> ShizukuHelper.highPerformance(newState)
                "Bloq. notif." -> ShizukuHelper.blockNotifications(newState)
                "Grabar" -> ShizukuHelper.recordScreen(newState)
                else -> null
            }
            if (msg != null) handler.post {
                msgView?.text = "$label: $msg"
                done()
            }
        }.start()
    }

    private fun setBackBlock(on: Boolean) {
        if (on && backView == null) {
            val view = BackBlock(this)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            backView = view
            windowManager.addView(view, params)
        } else if (!on) {
            backView?.let { windowManager.removeView(it) }
            backView = null
        }
    }

    private fun setRotation(on: Boolean) {
        if (on && rotView == null) {
            val view = View(this)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.screenOrientation =
                if (curRotation() == 1) {
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            rotView = view
            windowManager.addView(view, params)
        } else if (!on) {
            rotView?.let { windowManager.removeView(it) }
            rotView = null
        }
    }

    @Suppress("DEPRECATION")
    private fun curRotation(): Int = windowManager.defaultDisplay.rotation

    private fun circleBg(active: Boolean, s: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (active) AColor.parseColor("#FF102A")
                else AColor.parseColor("#B315151C")
            )
            setStroke(
                (3f * s).toInt().coerceAtLeast(1),
                if (active) AColor.WHITE else AColor.parseColor("#FF102A")
            )
        }

    private fun buildItem(iconRes: Int, label: String, s: Float): View {
        val canToggle = label in toggleLabels
        val startActive = canToggle && (toggleStates[label] == true)
        val circleSize = (64f * s).toInt()

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (12f * s).toInt(), 0, 0)
            background = GradientDrawable().apply {
                setColor(AColor.parseColor("#3315151C"))
                setStroke(
                    (2f * s).toInt().coerceAtLeast(1),
                    AColor.parseColor("#292932")
                )
                cornerRadius = 14f * s
            }
        }

        val circle = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = circleBg(startActive, s)
        }

        val icon = ImageView(this).apply {
            if (iconRes == -5) {
                setImageDrawable(WinIcon())
            } else if (iconRes < 0) {
                setImageDrawable(GtIcon(-iconRes))
            } else {
                setImageResource(iconRes)
            }
            setColorFilter(if (startActive) AColor.WHITE else tealInt)
        }

        val iconSize = (34f * s).toInt()
        circle.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))

        val text = TextView(this).apply {
            this.text = label
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * s)
            setTextColor(grayText)
            gravity = Gravity.CENTER
            maxLines = 2
        }

        item.addView(circle, LinearLayout.LayoutParams(circleSize, circleSize))
        item.addView(
            text,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4f * s).toInt()
            }
        )

        item.setOnClickListener {
            runFunction(label) {
                if (label == "Limpiador de RAM") {
                    handler.postDelayed({
                        circle.background = circleBg(false, s)
                        icon.setColorFilter(tealInt)
                    }, 1500)
                }
            }

            if (canToggle) {
                val active = toggleStates[label] == true
                circle.background = circleBg(active, s)
                icon.setColorFilter(if (active) AColor.WHITE else tealInt)
            } else if (label == "Limpiador de RAM") {
                circle.background = circleBg(true, s)
                icon.setColorFilter(AColor.WHITE)
            }

            if (label.endsWith("RAM")) {
                handler.postDelayed({ refreshStats() }, 1500)
            }
        }

        if (label == "Crosshair Assistant" || label == "Edit Keymap") {
            item.setOnLongClickListener {
                toggleStates[label] = true
                circle.background = circleBg(true, s)
                icon.setColorFilter(AColor.WHITE)
                if (panelVisible) togglePanel()
                if (label == "Edit Keymap") km?.showEditor() else mira?.showMenu()
                true
            }
        }
        return item
    }

    private fun redSeek(
        maxV: Int,
        start: Int,
        s: Float,
        onChange: (Int) -> Unit
    ): SeekBar {
        val seekBar = SeekBar(this)
        seekBar.max = maxV
        seekBar.progress = start

        val thickness = (8f * s).toInt().coerceAtLeast(2)
        val thumbSize = (30f * s).toInt()
        val inset = ((thumbSize - thickness) / 2).coerceAtLeast(0)

        val background = GradientDrawable().apply {
            setColor(AColor.parseColor("#292932"))
            cornerRadius = thickness / 2f
        }
        val foreground = GradientDrawable().apply {
            setColor(tealInt)
            cornerRadius = thickness / 2f
        }

        seekBar.progressDrawable = LayerDrawable(
            arrayOf(
                background,
                ClipDrawable(foreground, Gravity.START, ClipDrawable.HORIZONTAL)
            )
        ).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            setLayerInset(0, 0, inset, 0, inset)
            setLayerInset(1, 0, inset, 0, inset)
        }
        seekBar.thumb = GtDiamond(thumbSize, tealInt)

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) onChange(progress)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            }
        )
        return seekBar
    }

    private fun showPanel() {
        val (screenW, screenH) = screenSize()
        val s = minOf(screenW / 1882f, screenH / 836f) * 0.70f

        val defs = listOf(
            -4 to "Edit Keymap",
            -3 to "Girar pantalla",
            R.drawable.ic_cpu to "Alto rend.",
            -1 to "Crosshair Assistant",
            R.drawable.ic_bell_off to "Bloq. notif.",
            -2 to "Limpiador de RAM",
            R.drawable.ic_gesture_off to "Bloq. gestos",
            R.drawable.ic_chart to "Info real",
            R.drawable.ic_record to "Grabar",
            -5 to "Apps flotantes"
        )

        val items = HashMap<String, View>()
        for ((icon, name) in defs) {
            items[name] = buildItem(icon, name, s)
        }

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

        val curBright = try {
            Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
        } catch (_: Exception) {
            128
        }

        val bright = redSeek(255, curBright, s) { value ->
            ShizukuHelper.setBrightness(value)
        }
        val vol = redSeek(maxVol, curVol, s) { value ->
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            } catch (_: Exception) {
            }
        }

        val hud = HudRed(this, s)
        val root = hud.build(items, bright, vol) { togglePanel() }
        statsView = hud.gauges
        msgView = hud.msg

        val params = WindowManager.LayoutParams(
            (1882f * s).toInt(),
            (836f * s).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        panelView = root
        windowManager.addView(root, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statsRunnable)
        ShizukuHelper.stopStats()
        mira?.destroy()
        info?.destroy()
        km?.destroy()
        apps?.destroy()
        setBackBlock(false)
        rotView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        if (panelVisible) {
            panelView?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {
                }
            }
        }
    }
}

class BackBlock(context: Context) : View(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            true
        } else {
            super.dispatchKeyEvent(event)
        }
    }
}
