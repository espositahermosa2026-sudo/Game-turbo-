package com.tuusuario.gameturbo

import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.app.ActivityManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class MiraView(context: Context, private val d: Float) : View(context) {
 var style = 0
 var moving = false

 private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
  color = Color.parseColor("#FF1744")
  strokeWidth = 2f * d
  strokeCap = Paint.Cap.ROUND
 }
 private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
  color = Color.parseColor("#331DE9B6")
 }

 override fun onDraw(canvas: Canvas) {
  val cx = width / 2f
  val cy = height / 2f
  if (moving) canvas.drawCircle(cx, cy, 44f * d, bg)
  paint.style = Paint.Style.STROKE
  when (style) {
   0 -> {
    canvas.drawLine(cx, cy - 20f * d, cx, cy - 6f * d, paint)
    canvas.drawLine(cx, cy + 6f * d, cx, cy + 20f * d, paint)
    canvas.drawLine(cx - 20f * d, cy, cx - 6f * d, cy, paint)
    canvas.drawLine(cx + 6f * d, cy, cx + 20f * d, cy, paint)
   }
   1 -> canvas.drawCircle(cx, cy, 10f * d, paint)
   2 -> canvas.drawRect(cx - 10f * d, cy - 10f * d, cx + 10f * d, cy + 10f * d, paint)
  }
  paint.style = Paint.Style.FILL
  canvas.drawCircle(cx, cy, if (style == 3) 3.5f * d else 1.5f * d, paint)
 }
}

class MiraManager(private val ctx: Context, private val wm: WindowManager) {
 private val d = ctx.resources.displayMetrics.density
 private val prefs = ctx.getSharedPreferences("mira", Context.MODE_PRIVATE)
 private val teal = Color.parseColor("#1DE9B6")
 private val flagsTouch = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
  WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
  WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
 private val flagsNoTouch = flagsTouch or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

 private var view: MiraView? = null
 private var params: WindowManager.LayoutParams? = null
 private var menu: View? = null
 private var moving = false

 private fun dp(v: Int): Int = (v * d).toInt()

 fun setVisible(on: Boolean) {
  if (on) {
   show()
  } else {
   closeMenu()
   hide()
  }
 }

 fun destroy() {
  closeMenu()
  hide()
 }

 private fun show() {
  if (view != null) return
  val v = MiraView(ctx, d)
  v.style = prefs.getInt("style", 0)
  val p = WindowManager.LayoutParams(
   dp(48), dp(48),
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   flagsNoTouch,
   PixelFormat.TRANSLUCENT
  )
  p.gravity = Gravity.CENTER
  p.x = prefs.getInt("x", 0)
  p.y = prefs.getInt("y", 0)
  if (Build.VERSION.SDK_INT >= 28) {
   p.layoutInDisplayCutoutMode =
    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }
  var ix = 0
  var iy = 0
  var tx = 0f
  var ty = 0f
  v.setOnTouchListener { _, e ->
   if (!moving) {
    false
   } else {
    when (e.action) {
     MotionEvent.ACTION_DOWN -> {
      ix = p.x
      iy = p.y
      tx = e.rawX
      ty = e.rawY
     }
     MotionEvent.ACTION_MOVE -> {
      p.x = ix + (e.rawX - tx).toInt()
      p.y = iy + (e.rawY - ty).toInt()
      wm.updateViewLayout(v, p)
     }
     MotionEvent.ACTION_UP -> {
      prefs.edit().putInt("x", p.x).putInt("y", p.y).apply()
     }
    }
    true
   }
  }
  view = v
  params = p
  wm.addView(v, p)
 }

 private fun hide() {
  view?.let { wm.removeView(it) }
  view = null
  params = null
  moving = false
 }

 private fun setMoving(on: Boolean) {
  val v = view ?: return
  val p = params ?: return
  moving = on
  v.moving = on
  val size = dp(if (on) 96 else 48)
  p.width = size
  p.height = size
  p.flags = if (on) flagsTouch else flagsNoTouch
  wm.updateViewLayout(v, p)
  v.invalidate()
 }

 private fun closeMenu() {
  if (moving) setMoving(false)
  menu?.let { wm.removeView(it) }
  menu = null
 }

 fun showMenu() {
  if (menu != null) return
  show()
  val box = LinearLayout(ctx).apply {
   orientation = LinearLayout.HORIZONTAL
   gravity = Gravity.CENTER_VERTICAL
   setPadding(dp(10), dp(6), dp(10), dp(6))
   background = GradientDrawable().apply {
    setColor(Color.parseColor("#EE0B1412"))
    setStroke(dp(2), teal)
    cornerRadius = dp(12).toFloat()
   }
  }
  val styleBtns = ArrayList<TextView>()

  fun paint() {
   val sel = prefs.getInt("style", 0)
   styleBtns.forEachIndexed { i, b ->
    b.setTextColor(if (i == sel) teal else Color.WHITE)
    b.setTypeface(null, if (i == sel) Typeface.BOLD else Typeface.NORMAL)
   }
  }

  fun btn(label: String, onClick: (TextView) -> Unit): TextView {
   val b = TextView(ctx)
   b.text = label
   b.textSize = 14f
   b.setTextColor(Color.WHITE)
   b.setPadding(dp(10), dp(8), dp(10), dp(8))
   b.setOnClickListener { onClick(b) }
   box.addView(b)
   return b
  }

  listOf("Cruz", "Círculo", "Cuadrado", "Punto").forEachIndexed { i, name ->
   styleBtns.add(btn(name) {
    prefs.edit().putInt("style", i).apply()
    view?.style = i
    view?.invalidate()
    paint()
   })
  }
  paint()

  btn("Mover") { b ->
   setMoving(!moving)
   b.text = if (moving) "Fijar" else "Mover"
   b.setTextColor(if (moving) teal else Color.WHITE)
  }
  btn("Centrar") {
   val p = params
   val v = view
   if (p != null && v != null) {
    p.x = 0
    p.y = 0
    wm.updateViewLayout(v, p)
    prefs.edit().putInt("x", 0).putInt("y", 0).apply()
   }
  }
  btn("✕") { closeMenu() }

  val mp = WindowManager.LayoutParams(
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
   PixelFormat.TRANSLUCENT
  )
  mp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
  mp.y = dp(16)
  menu = box
  wm.addView(box, mp)
 }
}

class InfoManager(private val ctx: Context, private val wm: WindowManager) {
 private val h = Handler(Looper.getMainLooper())
 private var tv: TextView? = null
 private val tick = object : Runnable {
  override fun run() {
   tv?.text = text()
   h.postDelayed(this, 1000)
  }
 }

 private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

 private fun text(): String {
  val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  val mi = ActivityManager.MemoryInfo()
  am.getMemoryInfo(mi)
  val ram = ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt()
  val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
  val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10
  val bat = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
  val cpu = ShizukuHelper.cpu
  return "FPS " + ShizukuHelper.fps + "  CPU " + (if (cpu == "--") cpu else cpu + "%") +
   "  RAM " + ram + "%  " + temp + "°C  BAT " + bat + "%"
 }

 fun setVisible(on: Boolean) {
  if (on) show() else hide()
 }

 fun destroy() {
  hide()
 }

 private fun show() {
  if (tv != null) return
  val v = TextView(ctx)
  v.setTextColor(Color.parseColor("#1DE9B6"))
  v.textSize = 11f
  v.setPadding(dp(6), dp(2), dp(6), dp(2))
  v.background = GradientDrawable().apply {
   setColor(Color.parseColor("#99000000"))
   cornerRadius = dp(6).toFloat()
  }
  val p = WindowManager.LayoutParams(
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
   PixelFormat.TRANSLUCENT
  )
  p.gravity = Gravity.TOP or Gravity.START
  p.x = dp(30)
  p.y = dp(2)
  if (Build.VERSION.SDK_INT >= 28) {
   p.layoutInDisplayCutoutMode =
    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }
  tv = v
  wm.addView(v, p)
  ShizukuHelper.startStats()
  h.post(tick)
 }

 private fun hide() {
  val v = tv ?: return
  h.removeCallbacks(tick)
  wm.removeView(v)
  tv = null
  ShizukuHelper.stopStats()
 }
}

class GtIcon(private val kind: Int) : Drawable() {
 private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
  color = Color.WHITE
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
  } else if (kind == 4) {
  c.drawCircle(12f, 12f, 9f, p)
  c.drawLine(10f, 8f, 10f, 16f, p)
  c.drawLine(10f, 16f, 16.5f, 12f, p)
  c.drawLine(16.5f, 12f, 10f, 8f, p)
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

class GtDiamond(private val size: Int, color: Int) : Drawable() {
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

class MacroStep(val gap: Long, val x1: Int, val y1: Int, val x2: Int, val y2: Int, val dur: Long)

class MacroBtn(context: Context) : View(context) {
 var active = false
 private val p = Paint(Paint.ANTI_ALIAS_FLAG)

 override fun onDraw(c: Canvas) {
  val cx = width / 2f
  val cy = height / 2f
  val r = minOf(cx, cy) - 4f
  p.style = Paint.Style.FILL
  p.color = Color.parseColor(if (active) "#88FF1744" else "#551DE9B6")
  c.drawCircle(cx, cy, r, p)
  p.style = Paint.Style.STROKE
  p.strokeWidth = 3f
  p.color = Color.parseColor(if (active) "#FF1744" else "#1DE9B6")
  c.drawCircle(cx, cy, r, p)
  p.style = Paint.Style.FILL
  p.color = Color.WHITE
  if (active) {
   c.drawRect(cx - r / 4, cy - r / 4, cx + r / 4, cy + r / 4, p)
  } else {
   val t = Path()
   t.moveTo(cx - r / 4, cy - r / 3)
   t.lineTo(cx - r / 4, cy + r / 3)
   t.lineTo(cx + r / 3, cy)
   t.close()
   c.drawPath(t, p)
  }
 }
}

class RecView(context: Context, private val d: Float, private val onStep: (MacroStep) -> Unit) : View(context) {
 private val pts = ArrayList<Pair<Float, Float>>()
 private val p = Paint(Paint.ANTI_ALIAS_FLAG)
 private var t0 = 0L
 private var last = 0L
 private var x1 = 0
 private var y1 = 0

 override fun onTouchEvent(e: MotionEvent): Boolean {
  when (e.action) {
   MotionEvent.ACTION_DOWN -> {
    t0 = SystemClock.uptimeMillis()
    x1 = e.rawX.toInt()
    y1 = e.rawY.toInt()
    pts.add(Pair(e.rawX, e.rawY))
    invalidate()
   }
   MotionEvent.ACTION_UP -> {
    val now = SystemClock.uptimeMillis()
    val gap = if (last == 0L) 0L else t0 - last
    last = now
    onStep(MacroStep(gap, x1, y1, e.rawX.toInt(), e.rawY.toInt(), maxOf(40L, now - t0)))
   }
  }
  return true
 }

 override fun onDraw(c: Canvas) {
  for (i in pts.indices) {
   p.style = Paint.Style.FILL
   p.color = Color.parseColor("#66FF1744")
   c.drawCircle(pts[i].first, pts[i].second, 18f * d, p)
   p.color = Color.WHITE
   p.textSize = 14f * d
   p.textAlign = Paint.Align.CENTER
   c.drawText("${i + 1}", pts[i].first, pts[i].second + 5f * d, p)
  }
 }
}

class MacroManager(private val ctx: Context, private val wm: WindowManager) {
 private val d = ctx.resources.displayMetrics.density
 private val prefs = ctx.getSharedPreferences("macro", Context.MODE_PRIVATE)
 private val teal = Color.parseColor("#1DE9B6")
 private val hd = Handler(Looper.getMainLooper())
 private val steps = ArrayList<MacroStep>()
 private var loop = prefs.getBoolean("loop", false)
 private var btn: MacroBtn? = null
 private var rec: RecView? = null
 private var menu: View? = null
 private var menuLp: WindowManager.LayoutParams? = null
 @Volatile private var running = false

 private fun dp(v: Int): Int = (v * d).toInt()

 private fun load() {
  steps.clear()
  for (part in (prefs.getString("steps", "") ?: "").split(";")) {
   val n = part.split(",").mapNotNull { it.toLongOrNull() }
   if (n.size == 6) steps.add(MacroStep(n[0], n[1].toInt(), n[2].toInt(), n[3].toInt(), n[4].toInt(), n[5]))
  }
 }

 private fun save() {
  val raw = steps.joinToString(";") { "${it.gap},${it.x1},${it.y1},${it.x2},${it.y2},${it.dur}" }
  prefs.edit().putString("steps", raw).putBoolean("loop", loop).apply()
 }

 @Suppress("DEPRECATION")
 private fun screen(): Pair<Int, Int> {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
   val b = wm.currentWindowMetrics.bounds
   Pair(b.width(), b.height())
  } else {
   val dm = DisplayMetrics()
   wm.defaultDisplay.getRealMetrics(dm)
   Pair(dm.widthPixels, dm.heightPixels)
  }
 }

 private fun lp(w: Int, hh: Int): WindowManager.LayoutParams {
  val p = WindowManager.LayoutParams(
   w, hh,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
   PixelFormat.TRANSLUCENT
  )
  p.gravity = Gravity.TOP or Gravity.START
  if (Build.VERSION.SDK_INT >= 28) {
   p.layoutInDisplayCutoutMode =
    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }
  return p
 }

 fun setVisible(on: Boolean) {
  if (on) {
   showBtn()
  } else {
   running = false
   closeEditor()
   btn?.let { try { wm.removeView(it) } catch (e: Exception) { } }
   btn = null
  }
 }

 fun destroy() {
  setVisible(false)
 }

 private fun showBtn() {
  if (btn != null) return
  load()
  val size = dp(60)
  val v = MacroBtn(ctx)
  val p = lp(size, size)
  p.x = prefs.getInt("bx", dp(200))
  p.y = prefs.getInt("by", dp(120))
  var ix = 0
  var iy = 0
  var tx = 0f
  var ty = 0f
  var moved = false
  v.setOnTouchListener { _, e ->
   when (e.action) {
    MotionEvent.ACTION_DOWN -> {
     ix = p.x
     iy = p.y
     tx = e.rawX
     ty = e.rawY
     moved = false
    }
    MotionEvent.ACTION_MOVE -> {
     val dx = e.rawX - tx
     val dy = e.rawY - ty
     if (moved || kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8)) {
      moved = true
      p.x = ix + dx.toInt()
      p.y = iy + dy.toInt()
      wm.updateViewLayout(v, p)
     }
    }
    MotionEvent.ACTION_UP -> {
     if (moved) {
      prefs.edit().putInt("bx", p.x).putInt("by", p.y).apply()
     } else {
      toggleRun()
     }
    }
   }
   true
  }
  btn = v
  wm.addView(v, p)
 }

 private fun toggleRun() {
  if (running) {
   running = false
   return
  }
  if (steps.isEmpty()) return
  running = true
  btn?.active = true
  btn?.invalidate()
  Thread {
   do {
    for (s in steps.toList()) {
     if (!running) break
     var left = s.gap - 100
     while (left > 0 && running) {
      Thread.sleep(minOf(left, 50L))
      left -= 50
     }
     if (!running) break
     ShizukuHelper.runCommand("input swipe ${s.x1} ${s.y1} ${s.x2} ${s.y2} ${s.dur}")
    }
   } while (running && loop)
   running = false
   hd.post {
    btn?.active = false
    btn?.invalidate()
   }
  }.start()
 }

 private fun front() {
  val m = menu ?: return
  try {
   wm.removeView(m)
   wm.addView(m, menuLp)
  } catch (e: Exception) { }
 }

 private fun startRec() {
  steps.clear()
  val (w, hh) = screen()
  val v = RecView(ctx, d) { steps.add(it) }
  val p = lp(w, hh)
  p.x = 0
  p.y = 0
  rec = v
  wm.addView(v, p)
  front()
 }

 private fun stopRec() {
  rec?.let { try { wm.removeView(it) } catch (e: Exception) { } }
  rec = null
  save()
 }

 private fun closeEditor() {
  stopRec()
  menu?.let { try { wm.removeView(it) } catch (e: Exception) { } }
  menu = null
 }

 fun showEditor() {
  if (menu != null) return
  showBtn()
  val box = LinearLayout(ctx).apply {
   orientation = LinearLayout.HORIZONTAL
   gravity = Gravity.CENTER_VERTICAL
   setPadding(dp(10), dp(6), dp(10), dp(6))
   background = GradientDrawable().apply {
    setColor(Color.parseColor("#EE0B1412"))
    setStroke(dp(2), teal)
    cornerRadius = dp(12).toFloat()
   }
  }
  val hint = TextView(ctx)
  hint.text = "Graba: toca, mantén\no desliza los pasos"
  hint.textSize = 11f
  hint.setTextColor(teal)
  hint.setPadding(0, 0, dp(8), 0)
  box.addView(hint)

  fun mk(label: String, onClick: (TextView) -> Unit) {
   val b = TextView(ctx)
   b.text = label
   b.textSize = 14f
   b.setTextColor(Color.WHITE)
   b.setPadding(dp(10), dp(8), dp(10), dp(8))
   b.setOnClickListener { onClick(b) }
   box.addView(b)
  }
  mk("● Grabar") { b ->
   if (rec == null) {
    startRec()
    b.text = "■ Parar"
    b.setTextColor(teal)
   } else {
    stopRec()
    b.text = "● Grabar"
    b.setTextColor(Color.WHITE)
   }
  }
  mk(if (loop) "Repetir: Sí" else "Repetir: No") { b ->
   loop = !loop
   save()
   b.text = if (loop) "Repetir: Sí" else "Repetir: No"
  }
  mk("Borrar") {
   steps.clear()
   save()
  }
  mk("Listo") { closeEditor() }

  val mp = WindowManager.LayoutParams(
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
   PixelFormat.TRANSLUCENT
  )
  mp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
  mp.y = dp(16)
  menu = box
  menuLp = mp
  wm.addView(box, mp)
 }
}