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
   c.drawRoundRect(RectF(3f, 7f, 21f, 17f), 4f, 4f, p)
   c.drawLine(7f, 10f, 7f, 14f, p)
   c.drawLine(5f, 12f, 9f, 12f, p)
   p.style = Paint.Style.FILL
   c.drawCircle(15f, 12f, 1.2f, p)
   c.drawCircle(18f, 12f, 1.2f, p)
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

class KView(context: Context, private val marker: Boolean) : View(context) {
 var selected = false
 private val p = Paint(Paint.ANTI_ALIAS_FLAG)

 override fun onDraw(c: Canvas) {
  val cx = width / 2f
  val cy = height / 2f
  val r = minOf(cx, cy) - 4f
  p.style = Paint.Style.FILL
  p.color = Color.parseColor(if (marker) "#44FF1744" else "#331DE9B6")
  c.drawCircle(cx, cy, r, p)
  p.style = Paint.Style.STROKE
  p.strokeWidth = if (selected) 7f else 3f
  p.color = Color.parseColor(if (marker) "#FF1744" else "#1DE9B6")
  c.drawCircle(cx, cy, r, p)
  if (marker) {
   c.drawLine(cx - r / 2, cy, cx + r / 2, cy, p)
   c.drawLine(cx, cy - r / 2, cx, cy + r / 2, p)
  }
 }
}

class KeyData(var x: Int, var y: Int, var tx: Int, var ty: Int) {
 var v: KView? = null
 var m: KView? = null
}

class KeymapManager(private val ctx: Context, private val wm: WindowManager) {
 private val d = ctx.resources.displayMetrics.density
 private val prefs = ctx.getSharedPreferences("keymap", Context.MODE_PRIVATE)
 private val teal = Color.parseColor("#1DE9B6")
 private val keys = ArrayList<KeyData>()
 private var size = prefs.getInt("size", (56 * d).toInt())
 private var shown = false
 private var editing = false
 private var menu: View? = null
 private var sel = -1

 private fun dp(v: Int): Int = (v * d).toInt()

 private fun load() {
  keys.clear()
  val raw = prefs.getString("keys", "") ?: ""
  for (part in raw.split(";")) {
   val n = part.split(",").mapNotNull { it.toIntOrNull() }
   if (n.size == 4) keys.add(KeyData(n[0], n[1], n[2], n[3]))
  }
 }

 private fun save() {
  val raw = keys.joinToString(";") { "${it.x},${it.y},${it.tx},${it.ty}" }
  prefs.edit().putString("keys", raw).putInt("size", size).apply()
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

 private fun lp(cx: Int, cy: Int): WindowManager.LayoutParams {
  val p = WindowManager.LayoutParams(
   size, size,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
   PixelFormat.TRANSLUCENT
  )
  p.gravity = Gravity.TOP or Gravity.START
  p.x = cx - size / 2
  p.y = cy - size / 2
  if (Build.VERSION.SDK_INT >= 28) {
   p.layoutInDisplayCutoutMode =
    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }
  return p
 }

 private fun select(i: Int) {
  sel = i
  keys.forEachIndexed { j, k ->
   k.v?.selected = (j == i)
   k.m?.selected = (j == i)
   k.v?.invalidate()
   k.m?.invalidate()
  }
 }

 private fun addKey(k: KeyData) {
  val v = KView(ctx, false)
  val p = lp(k.x, k.y)
  var ix = 0
  var iy = 0
  var tx = 0f
  var ty = 0f
  v.setOnTouchListener { _, e ->
   if (editing) {
    when (e.action) {
     MotionEvent.ACTION_DOWN -> {
      ix = k.x
      iy = k.y
      tx = e.rawX
      ty = e.rawY
      select(keys.indexOf(k))
     }
     MotionEvent.ACTION_MOVE -> {
      k.x = ix + (e.rawX - tx).toInt()
      k.y = iy + (e.rawY - ty).toInt()
      p.x = k.x - size / 2
      p.y = k.y - size / 2
      wm.updateViewLayout(v, p)
     }
     MotionEvent.ACTION_UP -> save()
    }
   } else {
    when (e.action) {
     MotionEvent.ACTION_DOWN -> ShizukuHelper.touchDown(k.tx, k.ty)
     MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ShizukuHelper.touchUp(k.tx, k.ty)
    }
   }
   true
  }
  k.v = v
  wm.addView(v, p)
 }

 private fun addMarker(k: KeyData) {
  val v = KView(ctx, true)
  val p = lp(k.tx, k.ty)
  var ix = 0
  var iy = 0
  var tx = 0f
  var ty = 0f
  v.setOnTouchListener { _, e ->
   when (e.action) {
    MotionEvent.ACTION_DOWN -> {
     ix = k.tx
     iy = k.ty
     tx = e.rawX
     ty = e.rawY
     select(keys.indexOf(k))
    }
    MotionEvent.ACTION_MOVE -> {
     k.tx = ix + (e.rawX - tx).toInt()
     k.ty = iy + (e.rawY - ty).toInt()
     p.x = k.tx - size / 2
     p.y = k.ty - size / 2
     wm.updateViewLayout(v, p)
    }
    MotionEvent.ACTION_UP -> save()
   }
   true
  }
  k.m = v
  wm.addView(v, p)
 }

 private fun clearViews() {
  for (k in keys) {
   k.v?.let { try { wm.removeView(it) } catch (e: Exception) { } }
   k.m?.let { try { wm.removeView(it) } catch (e: Exception) { } }
   k.v = null
   k.m = null
  }
 }

 private fun buildAll() {
  clearViews()
  for (k in keys) {
   addKey(k)
   if (editing) addMarker(k)
  }
 }

 fun setVisible(on: Boolean) {
  if (on) {
   if (!shown) {
    load()
    shown = true
    buildAll()
   }
  } else {
   closeEditor()
   clearViews()
   shown = false
  }
 }

 fun destroy() {
  setVisible(false)
 }

 fun showEditor() {
  if (editing) return
  if (!shown) {
   load()
   shown = true
  }
  editing = true
  buildAll()
  showMenu()
 }

 private fun closeEditor() {
  if (!editing) return
  editing = false
  menu?.let { try { wm.removeView(it) } catch (e: Exception) { } }
  menu = null
  sel = -1
  save()
  buildAll()
 }

 private fun addNew() {
  val (w, h) = screen()
  keys.add(KeyData(w / 2 - dp(120), h / 2, w / 2 + dp(120), h / 2))
  save()
  buildAll()
  select(keys.size - 1)
 }

 private fun resize(delta: Int) {
  size = (size + delta).coerceIn(dp(32), dp(120))
  save()
  buildAll()
  select(sel)
 }

 private fun deleteSel() {
  if (sel !in keys.indices) return
  val k = keys[sel]
  k.v?.let { try { wm.removeView(it) } catch (e: Exception) { } }
  k.m?.let { try { wm.removeView(it) } catch (e: Exception) { } }
  keys.removeAt(sel)
  sel = -1
  save()
  buildAll()
 }

 private fun showMenu() {
  if (menu != null) return
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
  hint.text = "Verde = botón\nRojo = dónde toca"
  hint.textSize = 11f
  hint.setTextColor(teal)
  hint.setPadding(0, 0, dp(8), 0)
  box.addView(hint)

  fun btn(label: String, onClick: () -> Unit) {
   val b = TextView(ctx)
   b.text = label
   b.textSize = 14f
   b.setTextColor(Color.WHITE)
   b.setPadding(dp(10), dp(8), dp(10), dp(8))
   b.setOnClickListener { onClick() }
   box.addView(b)
  }
  btn("+ Botón") { addNew() }
  btn("Tam +") { resize(dp(8)) }
  btn("Tam -") { resize(-dp(8)) }
  btn("Borrar") { deleteSel() }
  btn("Listo") { closeEditor() }

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