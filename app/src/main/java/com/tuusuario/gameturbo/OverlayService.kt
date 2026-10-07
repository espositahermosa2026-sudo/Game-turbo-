package com.tuusuario.gameturbo
import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
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
import android.widget.TextClock
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
private val tealInt = AColor.parseColor("#E8B65A")
private val violet = AColor.parseColor("#B04DFF")
private val panelBg = AColor.parseColor("#D9230A45")
private val grayText = AColor.parseColor("#EBCB8B")
private val BAR_WIDTH_DP = 4
private val BAR_HEIGHT_CM = 2f
private val HUD_WIDTH_FRACTION = 0.8f
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
private var statsView: HudStats? = null
private val statsRunnable = object : Runnable {
override fun run() {
refreshStats()
handler.postDelayed(this, 1000)
}
}
override fun onBind(intent: Intent?): IBinder? = null
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
override fun onCreate() {
super.onCreate()
windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
mira = MiraManager(this, windowManager)
info = InfoManager(this, windowManager)
km = KmManager(this, windowManager)
apps = AppsManager(this, windowManager)
showBubble()
Thread {
ShizukuHelper.runCommand("cmd appops set $packageName RUN_IN_BACKGROUND allow; cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow; cmd deviceidle whitelist +$packageName; am set-standby-bucket $packageName active")
}.start()
}
private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
private fun cmToPx(cm: Float): Int {
val density = resources.displayMetrics.density
return (cm * (160f / 2.54f) * density).toInt()
}
@Suppress("DEPRECATION")
private fun screenSize(): Pair<Int, Int> {
return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
val b = windowManager.currentWindowMetrics.bounds
Pair(b.width(), b.height())
} else {
val dm = DisplayMetrics()
windowManager.defaultDisplay.getRealMetrics(dm)
Pair(dm.widthPixels, dm.heightPixels)
}
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
it.fps = ShizukuHelper.fps
it.cpu = ShizukuHelper.cpu
it.invalidate()
}
}
private fun showBubble() {
val touchW = dp(22)
val barW = dp(BAR_WIDTH_DP)
val barH = cmToPx(BAR_HEIGHT_CM)
val container = FrameLayout(this)
val bar = View(this).apply {
background = GradientDrawable(
GradientDrawable.Orientation.TOP_BOTTOM,
intArrayOf(0x00E8B65A, tealInt, tealInt, 0x00E8B65A)
).apply {
shape = GradientDrawable.RECTANGLE
val r = dp(3).toFloat()
cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
}
}
container.addView(
bar,
FrameLayout.LayoutParams(barW, FrameLayout.LayoutParams.MATCH_PARENT).apply {
gravity = Gravity.START
}
)
val params = WindowManager.LayoutParams(
touchW, barH,
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
PixelFormat.TRANSLUCENT
)
params.gravity = Gravity.TOP or Gravity.START
params.x = 0
params.y = 300
var initialY = 0
var touchY = 0f
var moved = false
container.setOnTouchListener { _, event ->
when (event.action) {
MotionEvent.ACTION_DOWN -> {
initialY = params.y
touchY = event.rawY
moved = false
true
}
MotionEvent.ACTION_MOVE -> {
val dy = event.rawY - touchY
if (kotlin.math.abs(dy) > 10) {
params.y = initialY + dy.toInt()
windowManager.updateViewLayout(container, params)
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
bubbleView = container
windowManager.addView(container, params)
}
private fun togglePanel() {
if (panelVisible) {
handler.removeCallbacks(statsRunnable)
ShizukuHelper.stopStats()
msgView = null
statsView = null
panelView?.let { windowManager.removeView(it) }
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
msgView?.text = if (newState) "Edit Keymap activo (mantén presionado para editar)" else "Edit Keymap apagado"
return
}
if (label == "Info real") {
info?.setVisible(newState)
return
}
if (label == "Bloq. gestos") {
setBackBlock(newState)
msgView?.text = if (newState) "Bloq. gestos: gesto de regresar bloqueado" else "Bloq. gestos: desactivado"
if (newState) Thread { ShizukuHelper.blockGestures(false) }.start()
return
}
if (label == "Girar pantalla") {
setRotation(newState)
return
}
msgView?.text = label + "..."
Thread {
val msg: String? = when (label) {
"Limpiador de RAM" -> ShizukuHelper.cleanRam()
"Alto rend." -> ShizukuHelper.highPerformance(newState)
"Bloq. notif." -> ShizukuHelper.blockNotifications(newState)
"Grabar" -> ShizukuHelper.recordScreen(newState)
else -> null
}
if (msg != null) handler.post {
msgView?.text = label + ": " + msg
done()
}
}.start()
}
private fun setBackBlock(on: Boolean) {
if (on && backView == null) {
val v = BackBlock(this)
val p = WindowManager.LayoutParams(
1, 1,
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
PixelFormat.TRANSLUCENT
)
p.gravity = Gravity.TOP or Gravity.START
backView = v
windowManager.addView(v, p)
} else if (!on) {
backView?.let { windowManager.removeView(it) }
backView = null
}
}
private fun setRotation(on: Boolean) {
if (on && rotView == null) {
val v = View(this)
val p = WindowManager.LayoutParams(
1, 1,
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
PixelFormat.TRANSLUCENT
)
p.gravity = Gravity.TOP or Gravity.START
p.screenOrientation = if (curRotation() == 1) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
rotView = v
windowManager.addView(v, p)
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
setColor(if (active) AColor.parseColor("#44E8B65A") else AColor.parseColor("#22D9A441"))
setStroke(
(3f * s).toInt().coerceAtLeast(1),
if (active) tealInt else AColor.parseColor("#B38B3A")
)
}
private fun buildItem(iconRes: Int, label: String, s: Float): View {
val canToggle = label in toggleLabels
val startActive = canToggle && (toggleStates[label] == true)
val circleSize = (64f * s).toInt()
val item = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER_HORIZONTAL
}
val circle = LinearLayout(this).apply {
gravity = Gravity.CENTER
background = circleBg(startActive, s)
}
val icon = ImageView(this).apply {
if (iconRes == -5) setImageDrawable(WinIcon()) else if (iconRes < 0) setImageDrawable(GtIcon(-iconRes)) else setImageResource(iconRes)
setColorFilter(if (startActive) tealInt else grayText)
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
).apply { topMargin = (4f * s).toInt() }
)
item.setOnClickListener {
runFunction(label) {
if (label == "Limpiador de RAM") handler.postDelayed({
circle.background = circleBg(false, s)
icon.setColorFilter(grayText)
}, 1500)
}
if (canToggle) {
val active = toggleStates[label] == true
circle.background = circleBg(active, s)
icon.setColorFilter(if (active) tealInt else grayText)
} else if (label == "Limpiador de RAM") {
circle.background = circleBg(true, s)
icon.setColorFilter(tealInt)
}
if (label.endsWith("RAM")) {
handler.postDelayed({ refreshStats() }, 1500)
}
}
if (label == "Crosshair Assistant" || label == "Edit Keymap") {
item.setOnLongClickListener {
toggleStates[label] = true
circle.background = circleBg(true, s)
icon.setColorFilter(tealInt)
if (panelVisible) togglePanel()
if (label == "Edit Keymap") km?.showEditor() else mira?.showMenu()
true
}
}
return item
}
private fun buildSlider(
symbol: String,
symbolFirst: Boolean,
maxValue: Int,
startValue: Int,
s: Float,
onChange: (Int) -> Unit
): LinearLayout {
val row = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
}
val sym = TextView(this).apply {
text = symbol
setTextSize(TypedValue.COMPLEX_UNIT_PX, 26f * s)
setTextColor(tealInt)
}
val bar = SeekBar(this).apply {
this.max = maxValue
this.progress = startValue
val th = dp(BAR_WIDTH_DP)
val ts = (22f * s).toInt()
val inset = ((ts - th) / 2).coerceAtLeast(0)
val trackBg = GradientDrawable().apply {
setColor(0x80E8B65A.toInt()); cornerRadius = th / 2f
}
val trackFg = GradientDrawable().apply {
setColor(tealInt); cornerRadius = th / 2f
}
progressDrawable = LayerDrawable(arrayOf(trackBg, ClipDrawable(trackFg, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
setId(0, android.R.id.background)
setId(1, android.R.id.progress)
setLayerInset(0, 0, inset, 0, inset)
setLayerInset(1, 0, inset, 0, inset)
}
thumb = GtDiamond((22f * s).toInt(), tealInt)
setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
if (fromUser) onChange(p)
}
override fun onStartTrackingTouch(sb: SeekBar?) {}
override fun onStopTrackingTouch(sb: SeekBar?) {}
})
}
val barLp = LinearLayout.LayoutParams(0, (22f * s).toInt(), 1f)
val symLp = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT,
LinearLayout.LayoutParams.WRAP_CONTENT
)
if (symbolFirst) {
row.addView(sym, symLp)
row.addView(bar, barLp)
} else {
row.addView(bar, barLp)
row.addView(sym, symLp)
}
return row
}
private fun place(s: Float, x: Float, y: Float, w: Float, h: Float): FrameLayout.LayoutParams =
FrameLayout.LayoutParams((w * s).toInt(), (h * s).toInt()).apply {
gravity = Gravity.TOP or Gravity.START
leftMargin = (x * s).toInt()
topMargin = (y * s).toInt()
}
private fun showPanel() {
val (sw, sh) = screenSize()
val panelW = if (sw > sh) (sw * HUD_WIDTH_FRACTION).toInt() else (sw * 0.96f).toInt()
val s = panelW / 1280f
val root = FrameLayout(this)
val frame = View(this).apply { background = HudFrame(panelBg, violet) }
root.addView(frame, place(s, 0f, 0f, 1280f, 300f))
val clock = TextClock(this).apply {
format12Hour = "hh:mm a"
format24Hour = "HH:mm"
setTextColor(AColor.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * s)
typeface = Typeface.DEFAULT_BOLD
gravity = Gravity.CENTER
setShadowLayer(4f, 0f, 0f, AColor.BLACK)
}
root.addView(clock, place(s, 440f, 2f, 400f, 28f))
val stats = HudStats(this, s, tealInt, grayText)
statsView = stats
root.addView(stats, place(s, 440f, 64f, 400f, 156f))
val leftItems = listOf(
-4 to "Edit Keymap",
-3 to "Girar pantalla",
R.drawable.ic_cpu to "Alto rend.",
-1 to "Crosshair Assistant",
R.drawable.ic_bell_off to "Bloq. notif."
)
val rightItems = listOf(
-2 to "Limpiador de RAM",
R.drawable.ic_gesture_off to "Bloq. gestos",
R.drawable.ic_chart to "Info real",
R.drawable.ic_record to "Grabar",
-5 to "Apps flotantes"
)
val leftCols = floatArrayOf(74f, 205f, 336f)
val rightCols = floatArrayOf(944f, 1075f, 1206f)
val rowTops = floatArrayOf(24f, 148f)
leftItems.forEachIndexed { i, (iconRes, label) ->
val v = buildItem(iconRes, label, s)
root.addView(v, place(s, leftCols[i % 3] - 65f, rowTops[i / 3], 130f, 116f))
}
rightItems.forEachIndexed { i, (iconRes, label) ->
val v = buildItem(iconRes, label, s)
root.addView(v, place(s, rightCols[i % 3] - 65f, rowTops[i / 3], 130f, 116f))
}
val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
val curBright = try {
Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
} catch (e: Exception) {
128
}
val brightness = buildSlider("☀", true, 255, curBright, s) { v -> ShizukuHelper.setBrightness(v) }
root.addView(brightness, place(s, 20f, 312f, 330f, 46f))
val volume = buildSlider("🔊", false, maxVol, curVol, s) { v ->
try {
audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
} catch (e: Exception) {
}
}
root.addView(volume, place(s, 930f, 312f, 330f, 46f))
val closeBtn = TextView(this).apply {
text = "✕"
setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * s)
setTextColor(tealInt)
gravity = Gravity.CENTER
setOnClickListener { togglePanel() }
}
root.addView(closeBtn, place(s, 590f, 312f, 100f, 46f))
val msg = TextView(this).apply {
setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * s)
setTextColor(tealInt)
gravity = Gravity.CENTER
setShadowLayer(4f, 0f, 0f, AColor.BLACK)
}
msgView = msg
root.addView(msg, place(s, 160f, 364f, 960f, 40f))
val params = WindowManager.LayoutParams(
panelW,
(406f * s).toInt(),
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
ShizukuHelper.stopStats()
mira?.destroy()
info?.destroy()
km?.destroy()
apps?.destroy()
setBackBlock(false)
rotView?.let { windowManager.removeView(it) }
bubbleView?.let { windowManager.removeView(it) }
if (panelVisible) panelView?.let { windowManager.removeView(it) }
}
}
class BackBlock(context: Context) : View(context) {
override fun dispatchKeyEvent(e: KeyEvent): Boolean {
return if (e.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(e)
}
}