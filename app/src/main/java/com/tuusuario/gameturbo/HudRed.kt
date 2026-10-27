package com.tuusuario.gameturbo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView

val HRed = Color.parseColor("#FF102A")
val HMetal = Color.parseColor("#15151C")
val HGray = Color.parseColor("#292932")

// Marco de cada panel: relleno oscuro, borde rojo neón con brillo y esquinas cortadas
class HudPanel(private val cut: Float) : Drawable() {
  private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#EB15151C")
    style = Paint.Style.FILL
  }
  private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#55FF102A")
    style = Paint.Style.STROKE
    strokeWidth = cut * 0.45f
  }
  private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#FF102A")
    style = Paint.Style.STROKE
    strokeWidth = cut * 0.14f
    strokeJoin = Paint.Join.ROUND
  }
  private val path = Path()

  override fun draw(c: Canvas) {
    val m = cut * 0.3f
    val l = bounds.left + m
    val t = bounds.top + m
    val r = bounds.right - m
    val b = bounds.bottom - m
    val k = cut
    path.reset()
    path.moveTo(l + k, t)
    path.lineTo(r - k / 3, t)
    path.lineTo(r, t + k / 3)
    path.lineTo(r, b - k)
    path.lineTo(r - k, b)
    path.lineTo(l + k / 3, b)
    path.lineTo(l, b - k / 3)
    path.lineTo(l, t + k)
    path.close()
    c.drawPath(path, fill)
    c.drawPath(path, glow)
    c.drawPath(path, edge)
  }

  override fun setAlpha(alpha: Int) {
    fill.alpha = alpha
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    fill.colorFilter = colorFilter
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// Iconos sencillos dibujados en código: 0 sol, 1 bocina, 2 control, 3 rayo en hexágono
class HudGlyph(private val kind: Int, private val tint: Int) : Drawable() {
  private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = tint
    style = Paint.Style.STROKE
    strokeWidth = 1.8f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val path = Path()

  override fun draw(c: Canvas) {
    c.save()
    c.translate(bounds.left.toFloat(), bounds.top.toFloat())
    c.scale(bounds.width() / 24f, bounds.height() / 24f)
    p.style = Paint.Style.STROKE
    when (kind) {
      0 -> {
        c.drawCircle(12f, 12f, 4.5f, p)
        for (i in 0 until 8) {
          val a = i * 45.0 * Math.PI / 180.0
          val dx = Math.cos(a).toFloat()
          val dy = Math.sin(a).toFloat()
          c.drawLine(12f + 7f * dx, 12f + 7f * dy, 12f + 10f * dx, 12f + 10f * dy, p)
        }
      }
      1 -> {
        path.reset()
        path.moveTo(3f, 9f)
        path.lineTo(7f, 9f)
        path.lineTo(12f, 5f)
        path.lineTo(12f, 19f)
        path.lineTo(7f, 15f)
        path.lineTo(3f, 15f)
        path.close()
        c.drawPath(path, p)
        c.drawArc(RectF(10f, 7f, 18f, 17f), -50f, 100f, false, p)
        c.drawArc(RectF(9f, 3f, 23f, 21f), -50f, 100f, false, p)
      }
      2 -> {
        c.drawRoundRect(RectF(2f, 7f, 22f, 18f), 5f, 5f, p)
        c.drawLine(7f, 10f, 7f, 15f, p)
        c.drawLine(4.5f, 12.5f, 9.5f, 12.5f, p)
        p.style = Paint.Style.FILL
        c.drawCircle(15.5f, 12f, 1.3f, p)
        c.drawCircle(18.5f, 14f, 1.3f, p)
      }
      else -> {
        path.reset()
        for (i in 0 until 6) {
          val a = (i * 60.0 - 90.0) * Math.PI / 180.0
          val x = 12f + 10f * Math.cos(a).toFloat()
          val y = 12f + 10f * Math.sin(a).toFloat()
          if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(13f, 5f)
        path.lineTo(8f, 13f)
        path.lineTo(12f, 13f)
        path.lineTo(11f, 19f)
        path.lineTo(16f, 10f)
        path.lineTo(12f, 10f)
        path.close()
        p.style = Paint.Style.FILL
        c.drawPath(path, p)
      }
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


// Botón hexagonal para el HUD futurista: negro translúcido y contorno rojo neón.
class HudHexButton(private val active: Boolean, private val scale: Float) : Drawable() {
  private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (active) Color.parseColor("#CCFF102A") else Color.parseColor("#EE101016")
    style = Paint.Style.FILL
  }
  private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor(if (active) "#AAFF102A" else "#66FF102A")
    style = Paint.Style.STROKE
    strokeWidth = 4f * scale
  }
  private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (active) Color.WHITE else HRed
    style = Paint.Style.STROKE
    strokeWidth = 1.8f * scale
  }
  private val path = Path()
  override fun draw(canvas: Canvas) {
    val b = bounds
    val inset = 3f * scale
    val l = b.left + inset; val t = b.top + inset
    val r = b.right - inset; val bot = b.bottom - inset
    val cut = (r - l) * 0.22f
    path.reset()
    path.moveTo(l + cut, t); path.lineTo(r - cut, t)
    path.lineTo(r, t + (bot - t) * 0.25f)
    path.lineTo(r, bot - (bot - t) * 0.25f)
    path.lineTo(r - cut, bot); path.lineTo(l + cut, bot)
    path.lineTo(l, bot - (bot - t) * 0.25f)
    path.lineTo(l, t + (bot - t) * 0.25f); path.close()
    canvas.drawPath(path, fill); canvas.drawPath(path, glow); canvas.drawPath(path, edge)
  }
  override fun setAlpha(alpha: Int) { fill.alpha = alpha; glow.alpha = alpha; edge.alpha = alpha }
  override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter }
  @Suppress("OVERRIDE_DEPRECATION")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// Panel central: tres medidores circulares RAM / FPS / CPU
class HudGauges(context: Context, private val s: Float) : View(context) {
  var ram = 0
  var fps = "--"
  var cpu = "--"
  private val p = Paint(Paint.ANTI_ALIAS_FLAG)
  private val oval = RectF()
  private val path = Path()

  override fun onDraw(c: Canvas) {
    c.scale(s, s)
    val pct = floatArrayOf(
      ram / 100f,
      ((fps.toIntOrNull() ?: 0) / 120f).coerceIn(0f, 1f),
      ((cpu.toIntOrNull() ?: 0) / 100f).coerceIn(0f, 1f)
    )
    val texts = arrayOf("$ram%", fps, if (cpu == "--") cpu else "$cpu%")
    val names = arrayOf("RAM", "FPS", "CPU")
    for (i in 0 until 3) {
      val cx = 117f + i * 170f
      val cy = 135f
      oval.set(cx - 75f, cy - 75f, cx + 75f, cy + 75f)
      p.style = Paint.Style.STROKE
      p.strokeCap = Paint.Cap.ROUND
      p.strokeWidth = 12f
      p.color = HGray
      c.drawArc(oval, 140f, 260f, false, p)
      p.color = HRed
      c.drawArc(oval, 140f, 260f * pct[i], false, p)
      p.style = Paint.Style.FILL
      p.textAlign = Paint.Align.CENTER
      p.typeface = Typeface.DEFAULT_BOLD
      p.color = Color.WHITE
      p.textSize = 44f
      c.drawText(texts[i], cx, cy + 14f, p)
      p.color = HRed
      p.textSize = 24f
      c.drawText(names[i], cx, cy + 56f, p)
      p.color = HMetal
      c.drawCircle(cx, 232f, 30f, p)
      p.style = Paint.Style.STROKE
      p.strokeWidth = 3f
      p.color = HRed
      if (i == 1) {
        c.drawRoundRect(RectF(cx - 15f, 220f, cx + 15f, 240f), 3f, 3f, p)
        c.drawLine(cx, 240f, cx, 246f, p)
        c.drawLine(cx - 8f, 246f, cx + 8f, 246f, p)
      } else {
        c.drawRoundRect(RectF(cx - 11f, 221f, cx + 11f, 243f), 3f, 3f, p)
        c.drawRect(cx - 4f, 228f, cx + 4f, 236f, p)
        for (k in 0 until 3) {
          val o = -7f + k * 7f
          c.drawLine(cx + o, 215f, cx + o, 221f, p)
          c.drawLine(cx + o, 243f, cx + o, 249f, p)
        }
      }
    }
    // emblema inferior
    path.reset()
    for (i in 0 until 6) {
      val a = (i * 60.0 - 90.0) * Math.PI / 180.0
      val x = 287f + 34f * Math.cos(a).toFloat()
      val y = 330f + 34f * Math.sin(a).toFloat()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3f
    p.color = HRed
    c.drawPath(path, p)
    path.reset()
    path.moveTo(293f, 308f)
    path.lineTo(277f, 332f)
    path.lineTo(287f, 332f)
    path.lineTo(281f, 352f)
    path.lineTo(299f, 324f)
    path.lineTo(289f, 324f)
    path.close()
    p.style = Paint.Style.FILL
    c.drawPath(path, p)
  }
}

// Tira vertical de la izquierda con el texto girado
class HudSide(context: Context, private val s: Float) : View(context) {
  private val p = Paint(Paint.ANTI_ALIAS_FLAG)

  override fun onDraw(c: Canvas) {
    c.scale(s, s)
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    p.strokeWidth = 5f
    p.color = HRed
    for (k in 0 until 3) {
      val y = 40f + k * 26f
      c.drawLine(26f, y + 14f, 46f, y, p)
      c.drawLine(46f, y, 66f, y + 14f, p)
    }
    c.save()
    c.rotate(90f, 46f, 250f)
    p.style = Paint.Style.FILL
    p.color = Color.WHITE
    p.textAlign = Paint.Align.CENTER
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = 27f
    p.letterSpacing = 0.22f
    c.drawText("GAME PLAY", 46f, 259f, p)
    c.restore()
    p.color = HRed
    c.drawRoundRect(40f, 400f, 52f, 432f, 5f, 5f, p)
  }
}

// Banner de la derecha con un diseño original
class HudBanner(context: Context, private val s: Float) : View(context) {
  private val p = Paint(Paint.ANTI_ALIAS_FLAG)
  private val path = Path()

  private fun poly(c: Canvas, vararg v: Float) {
    path.reset()
    path.moveTo(v[0], v[1])
    var i = 2
    while (i < v.size) {
      path.lineTo(v[i], v[i + 1])
      i += 2
    }
    path.close()
    c.drawPath(path, p)
  }

  override fun onDraw(c: Canvas) {
    c.scale(s, s)
    p.style = Paint.Style.FILL
    p.color = Color.parseColor("#0B0B10")
    c.drawRect(0f, 0f, 584f, 240f, p)
    p.color = Color.parseColor("#66FF102A")
    poly(c, 0f, 0f, 60f, 0f, 20f, 110f, 0f, 110f)
    poly(c, 80f, 0f, 120f, 0f, 80f, 90f, 40f, 90f)
    p.color = Color.parseColor("#14141C")
    poly(c, 340f, 240f, 352f, 120f, 400f, 52f, 470f, 18f, 540f, 32f, 584f, 92f, 584f, 240f)
    p.color = Color.parseColor("#CCFF102A")
    poly(c, 420f, 112f, 510f, 94f, 574f, 120f, 502f, 152f, 432f, 142f)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3f
    p.color = HRed
    path.reset()
    path.moveTo(340f, 240f)
    path.lineTo(352f, 120f)
    path.lineTo(400f, 52f)
    path.lineTo(470f, 18f)
    path.lineTo(540f, 32f)
    path.lineTo(584f, 92f)
    c.drawPath(path, p)
    p.style = Paint.Style.FILL
    p.textAlign = Paint.Align.LEFT
    p.typeface = Typeface.DEFAULT_BOLD
    p.letterSpacing = 0.1f
    p.color = HRed
    p.textSize = 38f
    c.drawText("GAME PLAY", 22f, 150f, p)
    p.color = Color.WHITE
    p.textSize = 28f
    p.letterSpacing = 0.3f
    c.drawText("PLAY", 28f, 196f, p)
    c.drawText("HARDER", 28f, 232f, p)
  }
}

// Arma toda la pantalla del panel con la estructura rojo y negro
class HudRed(private val ctx: Context, private val s: Float) {
  lateinit var gauges: HudGauges
  lateinit var msg: TextView

  private fun u(v: Float): Int = (v * s).toInt()

  private fun at(root: FrameLayout, v: View, x: Float, y: Float, w: Float, h: Float) {
    val lp = FrameLayout.LayoutParams(u(w), u(h))
    lp.gravity = Gravity.TOP or Gravity.START
    lp.leftMargin = u(x)
    lp.topMargin = u(y)
    root.addView(v, lp)
  }

  private fun panel(root: FrameLayout, x: Float, y: Float, w: Float, h: Float, cut: Float = 20f) {
    val v = View(ctx)
    v.background = HudPanel(cut * s)
    v.isClickable = true
    at(root, v, x, y, w, h)
  }

  private fun text(t: String, size: Float, color: Int, spacing: Float = 0f, center: Boolean = false): TextView {
    val v = TextView(ctx)
    v.text = t
    v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * s)
    v.setTextColor(color)
    v.typeface = Typeface.DEFAULT_BOLD
    v.letterSpacing = spacing
    v.includeFontPadding = false
    v.gravity = if (center) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)
    return v
  }

  private fun glyph(kind: Int, color: Int): ImageView {
    val v = ImageView(ctx)
    v.setImageDrawable(HudGlyph(kind, color))
    return v
  }

  private fun line(root: FrameLayout, x: Float, y: Float, w: Float) {
    val v = View(ctx)
    v.setBackgroundColor(HRed)
    at(root, v, x, y, w, 3f)
  }

  fun build(items: Map<String, View>, bright: SeekBar, vol: SeekBar, onClose: () -> Unit): FrameLayout {
    val root = FrameLayout(ctx)
    root.setOnClickListener { onClose() }

    // ---- barra de arriba ----
    line(root, 470f, 92f, 220f)
    line(root, 1190f, 92f, 250f)
    panel(root, 20f, 45f, 450f, 95f)
    val g = text("G", 58f, HRed)
    g.setTypeface(Typeface.DEFAULT_BOLD, Typeface.ITALIC)
    at(root, g, 52f, 58f, 60f, 70f)
    val clock = TextClock(ctx)
    clock.format12Hour = "hh:mm a"
    clock.format24Hour = "HH:mm"
    clock.setTextColor(Color.WHITE)
    clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 40f * s)
    clock.typeface = Typeface.DEFAULT_BOLD
    clock.includeFontPadding = false
    clock.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    at(root, clock, 125f, 60f, 330f, 65f)

    panel(root, 690f, 32f, 500f, 120f, 24f)
    at(root, glyph(3, HRed), 722f, 62f, 60f, 60f)
    at(root, text("GAME PLAY", 46f, Color.WHITE, 0.12f, true), 790f, 52f, 380f, 80f)

    panel(root, 1440f, 55f, 420f, 92f)
    at(root, text("GAME SPACE", 26f, HRed, 0.2f), 1490f, 70f, 270f, 62f)
    at(root, glyph(2, HRed), 1770f, 68f, 70f, 66f)

    // ---- tira izquierda ----
    panel(root, 22f, 140f, 93f, 450f)
    at(root, HudSide(ctx, s), 22f, 150f, 93f, 440f)

    // ---- botones de funciones (3 x 3) ----
    panel(root, 128f, 140f, 527f, 442f)
    val grid = listOf(
      "Edit Keymap", "Girar pantalla", "Alto rend.",
      "Crosshair Assistant", "Bloq. notif.", "Limpiador de RAM",
      "Bloq. gestos", "Info real"
    )
    val colX = floatArrayOf(138f, 309f, 476f)
    val rowY = floatArrayOf(148f, 290f, 435f)
    grid.forEachIndexed { i, name ->
      items[name]?.let { at(root, it, colX[i % 3], rowY[i / 3], 165f, 138f) }
    }

    // ---- medidores ----
    panel(root, 668f, 165f, 564f, 375f, 26f)
    val gg = HudGauges(ctx, s)
    gauges = gg
    at(root, gg, 668f, 165f, 564f, 375f)

    // ---- panel derecho ----
    panel(root, 1238f, 140f, 624f, 442f)
    at(root, HudBanner(ctx, s), 1258f, 160f, 584f, 240f)
    items["Grabar"]?.let { at(root, it, 1262f, 418f, 265f, 150f) }
    items["Apps flotantes"]?.let { at(root, it, 1540f, 418f, 290f, 150f) }

    // ---- brillo y volumen ----
    panel(root, 30f, 605f, 720f, 125f)
    at(root, glyph(0, Color.WHITE), 105f, 640f, 62f, 62f)
    at(root, text("Brillo", 24f, Color.WHITE), 215f, 625f, 300f, 36f)
    at(root, bright, 200f, 662f, 470f, 50f)
    panel(root, 1140f, 605f, 720f, 125f)
    at(root, glyph(1, Color.WHITE), 1226f, 640f, 66f, 62f)
    at(root, text("Volumen", 24f, Color.WHITE), 1335f, 625f, 300f, 36f)
    at(root, vol, 1318f, 662f, 470f, 50f)

    // ---- mensajes ----
    val m = text("", 24f, HRed, 0f, true)
    msg = m
    at(root, m, 300f, 745f, 1282f, 50f)
    return root
  }
}
