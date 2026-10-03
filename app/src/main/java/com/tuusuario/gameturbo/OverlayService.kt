package com.tuusuario.gameturbo
import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
private val tealInt = AColor.parseColor("#1DE9B6")
private val panelBg = AColor.parseColor("#D90B1412")
private val grayText = AColor.parseColor("#CFD8DC")
private val BAR_WIDTH_DP = 4
private val BAR_HEIGHT_CM = 2f
private val HUD_WIDTH_FRACTION = 0.8f
private val toggleLabels = setOf(
"Alto rend.",
"Girar pantalla",
"Crosshair Assistant",
"Bloq. notif.",
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
mira = MiraManager(this, windowManager)
showBubble()
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
intArrayOf(0x001DE9B6, tealInt, tealInt, 0x001DE9B6)
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
private fun runFunction(label: String) {
val newState = !(toggleStates[label] ?: false)
toggleStates[label] = newState
if (label == "Crosshair Assistant") {
mira?.setVisible(newState)
return
}
Thread {
when (label) {
"Girar pantalla" -> ShizukuHelper.rotateScreen(newState, if (curRotation() == 1) 3 else 1)
"Limpiador de RAM" -> ShizukuHelper.cleanRam()
"Alto rend." -> ShizukuHelper.highPerformance(newState)
"Bloq. notif." -> ShizukuHelper.blockNotifications(newState)
}
}.start()
}
@Suppress("DEPRECATION")
private fun curRotation(): Int = windowManager.defaultDisplay.rotation
private fun circleBg(active: Boolean, s: Float): GradientDrawable =
GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(if (active) AColor.parseColor("#331DE9B6") else AColor.parseColor("#22FFFFFF"))
setStroke(
(3f * s).toInt().coerceAtLeast(1),
if (active) tealInt else AColor.parseColor("#66FFFFFF")
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
if (iconRes < 0) setImageDrawable(IconDrawable(-iconRes)) else setImageResource(iconRes)
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
runFunction(label)
if (canToggle) {
val active = toggleStates[label] == true
circle.background = circleBg(active, s)
icon.setColorFilter(if (active) tealInt else grayText)
}
if (label.endsWith("RAM")) {
handler.postDelayed({ refreshStats() }, 1500)
}
}
if (label == "Crosshair Assistant") {
item.setOnLongClickListener {
toggleStates[label] = true
circle.background = circleBg(true, s)
icon.setColorFilter(tealInt)
if (panelVisible) togglePanel()
mira?.showMenu()
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
setColor(0x801DE9B6.toInt()); cornerRadius = th / 2f
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
thumb = DiamondDrawable((22f * s).toInt(), tealInt)
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
val frame = View(this).apply { background = HudDrawable(panelBg, tealInt) }
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
val stats = StatsView(this, s, tealInt, grayText)
statsView = stats
root.addView(stats, place(s, 440f, 64f, 400f, 156f))
val leftItems = listOf(
-3 to "Girar pantalla",
R.drawable.ic_cpu to "Alto rend.",
-1 to "Crosshair Assistant",
R.drawable.ic_bell_off to "Bloq. notif."
)
val rightItems = listOf(
-2 to "Limpiador de RAM",
R.drawable.ic_gesture_off to "Bloq. gestos",
R.drawable.ic_chart to "Info real",
R.drawable.ic_record to "Grabar"
)
val leftCols = floatArrayOf(110f, 270f)
val rightCols = floatArrayOf(1010f, 1170f)
val rowTops = floatArrayOf(18f, 146f)
leftItems.forEachIndexed { i, (iconRes, label) ->
val v = buildItem(iconRes, label, s)
root.addView(v, place(s, leftCols[i % 2] - 75f, rowTops[i / 2], 150f, 116f))
}
rightItems.forEachIndexed { i, (iconRes, label) ->
val v = buildItem(iconRes, label, s)
root.addView(v, place(s, rightCols[i % 2] - 75f, rowTops[i / 2], 150f, 116f))
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
val params = WindowManager.LayoutParams(
panelW,
(362f * s).toInt(),
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
bubbleView?.let { windowManager.removeView(it) }
if (panelVisible) panelView?.let { windowManager.removeView(it) }
}
}
class HudDrawable(fillColor: Int, strokeColor: Int) : Drawable() {
private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = fillColor
style = Paint.Style.FILL
}
private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = strokeColor
style = Paint.Style.STROKE
strokeWidth = 3f
strokeJoin = Paint.Join.ROUND
}
private val path = Path().apply {
moveTo(45f, 0f)
lineTo(260f, 0f)
lineTo(385f, 30f)
lineTo(895f, 30f)
lineTo(1020f, 0f)
lineTo(1235f, 0f)
lineTo(1280f, 45f)
lineTo(1280f, 255f)
lineTo(1235f, 300f)
lineTo(1020f, 300f)
lineTo(895f, 270f)
lineTo(740f, 270f)
lineTo(715f, 300f)
lineTo(565f, 300f)
lineTo(540f, 270f)
lineTo(385f, 270f)
lineTo(260f, 300f)
lineTo(45f, 300f)
lineTo(0f, 255f)
lineTo(0f, 45f)
close()
}
override fun draw(canvas: Canvas) {
val unit = bounds.width() / 1280f
val pad = 2f * unit
val sw = (bounds.width() - 2 * pad) / 1280f
val sh = (bounds.height() - 2 * pad) / 300f
canvas.save()
canvas.translate(bounds.left + pad, bounds.top + pad)
canvas.scale(sw, sh)
canvas.drawPath(path, fillPaint)
canvas.drawPath(path, strokePaint)
canvas.restore()
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
class DiamondDrawable(private val size: Int, color: Int) : Drawable() {
private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
this.color = color
style = Paint.Style.FILL
}
private val path = Path()
override fun draw(canvas: Canvas) {
val cx = bounds.exactCenterX()
val cy = bounds.exactCenterY()
val r = size / 2f
path.reset()
path.moveTo(cx, cy - r)
path.lineTo(cx + r, cy)
path.lineTo(cx, cy + r)
path.lineTo(cx - r, cy)
path.close()
canvas.drawPath(path, paint)
}
override fun getIntrinsicWidth(): Int = size
override fun getIntrinsicHeight(): Int = size
override fun setAlpha(alpha: Int) {
paint.alpha = alpha
}
override fun setColorFilter(colorFilter: ColorFilter?) {
paint.colorFilter = colorFilter
}
@Suppress("OVERRIDE_DEPRECATION")
override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
class StatsView(
context: Context,
private val s: Float,
private val accent: Int,
private val gray: Int
) : View(context) {
var ram = 0
var fps = "--"
var cpu = "--"
private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = AColor.parseColor("#55000000")
style = Paint.Style.FILL
}
private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = AColor.parseColor("#991DE9B6")
style = Paint.Style.STROKE
strokeWidth = 2f
}
private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = accent
style = Paint.Style.FILL
}
private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = accent
textSize = 32f
typeface = Typeface.DEFAULT_BOLD
textAlign = Paint.Align.CENTER
}
private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = AColor.WHITE
textSize = 56f
typeface = Typeface.DEFAULT_BOLD
textAlign = Paint.Align.CENTER
}
private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = gray
textSize = 20f
textAlign = Paint.Align.CENTER
}
private val shape = Path().apply {
moveTo(60f, 12f)
lineTo(340f, 12f)
lineTo(360f, 32f)
lineTo(360f, 128f)
lineTo(340f, 148f)
lineTo(60f, 148f)
lineTo(40f, 128f)
lineTo(40f, 32f)
close()
}
override fun onDraw(canvas: Canvas) {
super.onDraw(canvas)
canvas.save()
canvas.scale(s, s)
canvas.drawPath(shape, fillPaint)
canvas.drawPath(shape, framePaint)
val filledL = (ram * 7 / 100).coerceIn(0, 7)
val cpuVal = cpu.toIntOrNull()
val filledR = if (cpuVal != null) (cpuVal * 7 / 100).coerceIn(0, 7) else 0
for (i in 0 until 7) {
val t = (i - 3) / 3f
val off = 12f * t * t
val y = 8f + i * 20f
val level = 6 - i
barPaint.alpha = if (level < filledL) 255 else 70
canvas.drawRoundRect(6f + off, y, 6f + off + 22f, y + 14f, 4f, 4f, barPaint)
barPaint.alpha = if (level < filledR) 255 else 70
canvas.drawRoundRect(372f - off - 22f, y, 372f - off, y + 14f, 4f, 4f, barPaint)
}
canvas.drawText("$ram%", 97f, 78f, valuePaint)
canvas.drawText(fps, 200f, 84f, fpsPaint)
canvas.drawText(if (cpu == "--") cpu else "$cpu%", 303f, 78f, valuePaint)
canvas.drawText("RAM", 97f, 100f, labelPaint)
canvas.drawText("FPS", 200f, 110f, labelPaint)
canvas.drawText("CPU", 303f, 100f, labelPaint)
canvas.restore()
}
}
class IconDrawable(private val kind: Int) : Drawable() {
private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = AColor.WHITE
strokeWidth = 1.8f
strokeCap = Paint.Cap.ROUND
}
override fun draw(c: Canvas) {
c.save()
c.translate(bounds.left.toFloat(), bounds.top.toFloat())
c.scale(bounds.width() / 24f, bounds.height() / 24f)
p.style = Paint.Style.STROKE
if (kind == 3) {
c.drawArc(RectF(4f, 4f, 20f, 20f), 200f, 280f, false, p)
c.drawLine(8f, 18.9f, 9.4f, 22.7f, p)
c.drawLine(8f, 18.9f, 11.9f, 18.2f, p)
} else if (kind == 1) {
c.drawCircle(12f, 12f, 6f, p)
c.drawLine(12f, 2f, 12f, 8f, p)
c.drawLine(12f, 16f, 12f, 22f, p)
c.drawLine(2f, 12f, 8f, 12f, p)
c.drawLine(16f, 12f, 22f, 12f, p)
p.style = Paint.Style.FILL
c.drawCircle(12f, 12f, 1.3f, p)
} else {
c.drawLine(5f, 6f, 19f, 6f, p)
c.drawLine(9f, 6f, 9f, 4f, p)
c.drawLine(9f, 4f, 15f, 4f, p)
c.drawLine(15f, 4f, 15f, 6f, p)
c.drawLine(7f, 6f, 8f, 20f, p)
c.drawLine(8f, 20f, 16f, 20f, p)
c.drawLine(16f, 20f, 17f, 6f, p)
c.drawLine(10f, 10f, 10f, 17f, p)
c.drawLine(14f, 10f, 14f, 17f, p)
}
c.restore()
}
override fun setAlpha(alpha: Int) {
p.alpha = alpha
}
override fun setColorFilter(colorFilter: ColorFilter?) {
p.colorFilter = colorFilter
}
@Suppress("OVERRIDE_DEPRECATION")
override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
