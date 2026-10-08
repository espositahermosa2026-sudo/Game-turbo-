package com.tuusuario.gameturbo

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class KmCircle(context: Context, private val kind: Int, private val d: Float) : View(context) {
 var title = ""
 var sub = ""
 var picked = false
 var quiet = false
 private val p = Paint(Paint.ANTI_ALIAS_FLAG)

 override fun onDraw(c: Canvas) {
  if (quiet) return
  val cx = width / 2f
  val cy = height / 2f
  val r = minOf(cx, cy) - 4f
  p.style = Paint.Style.FILL
  p.color = Color.parseColor(if (kind == 0) (if (quiet) "#221DE9B6" else "#551DE9B6") else "#77000000")
  c.drawCircle(cx, cy, r, p)
  p.style = Paint.Style.STROKE
  p.strokeWidth = if (picked) 6f else 3f
  p.color = Color.parseColor(if (kind == 0) "#1DE9B6" else "#FFFFFF")
  c.drawCircle(cx, cy, r, p)
  if (!quiet) {
   p.style = Paint.Style.FILL
   p.color = Color.WHITE
   p.textAlign = Paint.Align.CENTER
   p.textSize = (if (kind == 0) 14f else 10f) * d
   c.drawText(title, cx, if (sub.isEmpty()) cy + 5f * d else cy - 1f * d, p)
   if (sub.isNotEmpty()) {
    p.textSize = 9f * d
    c.drawText(sub, cx, cy + 11f * d, p)
   }
  }
 }
}
class KmLines(context: Context) : View(context) {
 var gx = -1f
 var gy = -1f
 private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
  color = Color.WHITE
  style = Paint.Style.STROKE
  strokeWidth = 2f
  pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
 }

 override fun onDraw(c: Canvas) {
  if (gx < 0f) return
  c.drawLine(gx, 0f, gx, height.toFloat(), p)
  c.drawLine(0f, gy, width.toFloat(), gy, p)
 }
}
class KmPad(context: Context, private val dir: Int, private val d: Float) : View(context) {
 private val p = Paint(Paint.ANTI_ALIAS_FLAG)
 private val path = Path()

 override fun onDraw(c: Canvas) {
  val w = width.toFloat()
  val h = height.toFloat()
  p.style = Paint.Style.FILL
  p.color = Color.parseColor("#CC1B5E50")
  c.drawRoundRect(0f, 0f, w, h, 10f * d, 10f * d, p)
  p.color = Color.parseColor("#1DE9B6")
  val cx = w / 2f
  val cy = h / 2f
  val s = 9f * d
  path.reset()
  when (dir) {
   0 -> {
    path.moveTo(cx, cy - s)
    path.lineTo(cx - s, cy + s * 0.7f)
    path.lineTo(cx + s, cy + s * 0.7f)
   }
   1 -> {
    path.moveTo(cx, cy + s)
    path.lineTo(cx - s, cy - s * 0.7f)
    path.lineTo(cx + s, cy - s * 0.7f)
   }
   2 -> {
    path.moveTo(cx - s, cy)
    path.lineTo(cx + s * 0.7f, cy - s)
    path.lineTo(cx + s * 0.7f, cy + s)
   }
   else -> {
    path.moveTo(cx + s, cy)
    path.lineTo(cx - s * 0.7f, cy - s)
    path.lineTo(cx - s * 0.7f, cy + s)
   }
  }
  path.close()
  c.drawPath(path, p)
 }
}

class KmSettings(private val ctx: Context, private val wm: WindowManager) {
  private val d = ctx.resources.displayMetrics.density
  private val teal = Color.parseColor("#1DE9B6")
  private val gray = Color.parseColor("#AAB4B8")
  private var win: LinearLayout? = null

  private fun dp(v: Int): Int = (v * d).toInt()

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

  fun close() {
    win?.let { try { wm.removeView(it) } catch (e: Exception) { } }
    win = null
  }

  private fun content(): LinearLayout {
    var w = win
    if (w == null) {
      w = LinearLayout(ctx)
      w.orientation = LinearLayout.VERTICAL
      w.setPadding(dp(18), dp(14), dp(18), dp(14))
      w.setBackgroundColor(Color.parseColor("#F2161D1F"))
      val (sw, _) = screen()
      val p = WindowManager.LayoutParams(
        sw * 47 / 100, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
      )
      p.gravity = Gravity.TOP or Gravity.END
      if (Build.VERSION.SDK_INT >= 28) {
        p.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
      win = w
      wm.addView(w, p)
    }
    w.removeAllViews()
    return w
  }

  private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = text
    t.textSize = sp
    t.setTextColor(color)
    if (bold) t.setTypeface(null, Typeface.BOLD)
    return t
  }

  private fun btn(label: String, onClick: () -> Unit): TextView {
    val b = tv(label, 16f, Color.WHITE)
    b.gravity = Gravity.CENTER
    b.setPadding(dp(18), dp(12), dp(18), dp(12))
    b.background = GradientDrawable().apply {
      setColor(Color.parseColor("#CC1B5E50"))
      cornerRadius = dp(10).toFloat()
    }
    b.setOnClickListener { onClick() }
    return b
  }

  private fun lin(horizontal: Boolean): LinearLayout {
    val l = LinearLayout(ctx)
    l.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
    l.gravity = Gravity.CENTER_VERTICAL
    return l
  }

  private fun gap(): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(-2, -2)
    p.setMargins(dp(4), 0, dp(4), 0)
    return p
  }

  fun showTrigger(
    title: String,
    block: Boolean,
    rows: List<Pair<String, String>>,
    onBlock: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onGear: (Int) -> Unit,
    onDel: (Int) -> Unit,
    multi: Boolean = true,
    onMulti: (Boolean) -> Unit = {}
  ) {
    val c = content()
    c.addView(tv(title, 22f, Color.WHITE, true))
    val r1 = lin(true)
    r1.setPadding(0, dp(14), 0, dp(6))
    r1.addView(tv("Bloquear toque accidental", 16f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
    val sw = Switch(ctx)
    sw.isChecked = block
    val checked = intArrayOf(android.R.attr.state_checked)
    sw.thumbTintList = ColorStateList(arrayOf(checked, intArrayOf()), intArrayOf(teal, Color.GRAY))
    sw.trackTintList = ColorStateList(
      arrayOf(checked, intArrayOf()),
      intArrayOf(Color.parseColor("#661DE9B6"), Color.parseColor("#44FFFFFF"))
    )
    sw.setOnCheckedChangeListener { _, on -> onBlock(on) }
    r1.addView(sw)
    c.addView(r1)
    val rm = lin(true)
    rm.setPadding(0, dp(6), 0, dp(6))
    rm.addView(tv("Tocar mientras te mueves", 16f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
    val sm = Switch(ctx)
    sm.isChecked = multi
    sm.thumbTintList = sw.thumbTintList
    sm.trackTintList = sw.trackTintList
    sm.setOnCheckedChangeListener { _, on -> onMulti(on) }
    rm.addView(sm)
    c.addView(rm)
    val r2 = lin(true)
    r2.addView(tv("Total de targets: " + rows.size, 15f, gray), LinearLayout.LayoutParams(0, -2, 1f))
    r2.addView(btn("＋") { onAdd() })
    c.addView(r2)
    val sv = ScrollView(ctx)
    val list = LinearLayout(ctx)
    list.orientation = LinearLayout.VERTICAL
    rows.forEachIndexed { k, r ->
      val row = lin(true)
      row.setPadding(dp(6), dp(10), dp(6), dp(10))
      val col = LinearLayout(ctx)
      col.orientation = LinearLayout.VERTICAL
      col.addView(tv(r.first, 18f, Color.WHITE, true))
      col.addView(tv(r.second, 14f, gray))
      row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
      row.addView(btn("⚙") { onGear(k) }, gap())
      row.addView(btn("🗑") { onDel(k) }, gap())
      list.addView(row)
    }
    sv.addView(list)
    c.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
    val bottom = lin(true)
    bottom.gravity = Gravity.END
    bottom.addView(btn("SALIR") { close() })
    c.addView(bottom, LinearLayout.LayoutParams(-1, -2))
  }

  fun showTarget(
    title: String,
    type0: Int,
    interval0: Int,
    duration0: Int,
    onSave: (Int, Int, Int) -> Unit,
    onCancel: () -> Unit,
    delay0: Int = 0,
    onDelay: (Int) -> Unit = {}
  ) {
    val c = content()
    var type = type0
    var interval = interval0
    var duration = duration0
    var delay = delay0
    c.addView(tv(title, 22f, Color.WHITE, true))

    val seg = lin(true)
    seg.setPadding(0, dp(12), 0, dp(10))
    val segs = ArrayList<TextView>()
    fun paintSeg() {
      segs.forEachIndexed { i, s ->
        s.setTextColor(if (i == type) Color.parseColor("#06251F") else Color.WHITE)
        s.background = GradientDrawable().apply {
          setColor(if (i == type) teal else Color.TRANSPARENT)
          cornerRadius = dp(10).toFloat()
        }
      }
    }
    arrayOf("Toque", "Repetir", "Doble", "Soltar").forEachIndexed { i, n ->
      val s = tv(n, 15f, Color.WHITE)
      s.gravity = Gravity.CENTER
      s.setPadding(dp(8), dp(12), dp(8), dp(12))
      s.setOnClickListener {
        type = i
        paintSeg()
      }
      segs.add(s)
      seg.addView(s, LinearLayout.LayoutParams(0, -2, 1f))
    }
    paintSeg()
    c.addView(seg)

    fun fmt(v: Int): String = if (v % 10 == 0) "${v / 10} ms" else "${v / 10}.${v % 10} ms"
    val body = LinearLayout(ctx)
    body.orientation = LinearLayout.VERTICAL
    fun slider(label: String, start: Int, lo: Int, onChange: (Int) -> Unit) {
      val head = lin(true)
      head.addView(tv(label, 16f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
      val vt = tv(fmt(start.coerceIn(lo, 10000)), 16f, Color.WHITE, true)
      head.addView(vt)
      body.addView(head)
      val sk = SeekBar(ctx)
      sk.max = 10000 - lo
      sk.progress = start.coerceIn(lo, 10000) - lo
      fun apply(p: Int) {
        val q = p.coerceIn(0, 10000 - lo)
        sk.progress = q
        vt.text = fmt(q + lo)
        onChange(q + lo)
      }
      fun step(v: Int): Int = if (v < 100) 1 else if (v < 1000) 10 else 100
      sk.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
          if (fromUser) apply(p)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
      })
      val row = lin(true)
      row.setPadding(0, dp(2), 0, dp(8))
      row.addView(btn("−") { apply(sk.progress - step(sk.progress + lo)) })
      row.addView(sk, LinearLayout.LayoutParams(0, -2, 1f))
      row.addView(btn("＋") { apply(sk.progress + step(sk.progress + lo + 1)) })
      body.addView(row)
    }
    slider("Espera antes del toque:", delay, 0) { delay = it }
    slider("Intervalo de toque:", interval, 1) { interval = it }
    slider("Duración del toque:", duration, 1) { duration = it }
    body.addView(tv("Rango: de 0.1 ms a 1000 ms", 13f, teal))
    val sv = ScrollView(ctx)
    sv.addView(body)
    c.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
    val bottom = lin(true)
    bottom.gravity = Gravity.END
    bottom.addView(btn("Cancelar") { onCancel() }, gap())
    bottom.addView(btn("Guardar") {
      onDelay(delay)
      onSave(type, interval, duration)
    }, gap())
    c.addView(bottom, LinearLayout.LayoutParams(-1, -2))
  }
}