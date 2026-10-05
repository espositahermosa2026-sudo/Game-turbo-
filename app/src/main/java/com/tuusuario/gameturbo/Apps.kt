package com.tuusuario.gameturbo

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class HudFrame(fillColor: Int, strokeColor: Int) : Drawable() {
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
class HudStats(
context: Context,
private val s: Float,
private val accent: Int,
private val gray: Int
) : View(context) {
  var ram = 0
  var fps = "--"
  var cpu = "--"
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#55000000")
    style = Paint.Style.FILL
  }
  private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#991DE9B6")
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
    color = Color.WHITE
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

class WinIcon : Drawable() {
  private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 1.8f
  }

  override fun draw(c: Canvas) {
    c.save()
    c.translate(bounds.left.toFloat(), bounds.top.toFloat())
    c.scale(bounds.width() / 24f, bounds.height() / 24f)
    c.drawRoundRect(RectF(3f, 3f, 16f, 13f), 2f, 2f, p)
    c.drawRoundRect(RectF(8f, 9f, 21f, 21f), 2f, 2f, p)
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

class AppsManager(private val ctx: Context, private val wm: WindowManager) {
  private val d = ctx.resources.displayMetrics.density
  private val prefs = ctx.getSharedPreferences("apps", Context.MODE_PRIVATE)
  private val teal = Color.parseColor("#1DE9B6")
  private val hd = Handler(Looper.getMainLooper())
  private var win: View? = null
  private var msg: View? = null
  private var list: LinearLayout? = null
  private var title: TextView? = null
  private var addBtn: TextView? = null
  private var adding = false

  private fun dp(v: Int): Int = (v * d).toInt()

  private fun saved(): List<String> =
    (prefs.getString("list", "") ?: "").split(",").filter { it.isNotEmpty() }

  private fun store(l: List<String>) {
    prefs.edit().putString("list", l.joinToString(",")).apply()
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

  fun destroy() {
    hide()
    msg?.let { try { wm.removeView(it) } catch (e: Exception) { } }
    msg = null
  }

  private fun hide() {
    win?.let { try { wm.removeView(it) } catch (e: Exception) { } }
    win = null
  }

  private fun flash(text: String) {
    hd.post {
      msg?.let { try { wm.removeView(it) } catch (e: Exception) { } }
      val t = TextView(ctx)
      t.text = text
      t.setTextColor(Color.WHITE)
      t.textSize = 14f
      t.setPadding(dp(14), dp(8), dp(14), dp(8))
      t.background = GradientDrawable().apply {
        setColor(Color.parseColor("#EE0B1412"))
        setStroke(dp(1), teal)
        cornerRadius = dp(10).toFloat()
      }
      val p = WindowManager.LayoutParams(
        -2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
      )
      p.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      p.y = dp(40)
      msg = t
      wm.addView(t, p)
      hd.postDelayed({
        try { wm.removeView(t) } catch (e: Exception) { }
        if (msg === t) msg = null
      }, 3500)
    }
  }

  private fun launch(pkg: String) {
    hide()
    val comp = ctx.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()
    if (comp == null) {
      flash("No pude abrir esa app")
      return
    }
    val (w, h) = screen()
    Thread {
      ShizukuHelper.runCommand("settings put global enable_freeform_support 1; settings put global force_resizable_activities 1")
      val r = ShizukuHelper.runCommand("am start -n $comp --windowingMode 5").trim()
      Thread.sleep(900)
      val out = ShizukuHelper.runCommand(
        "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n 3"
      )
      val id = Regex("""u\d+ ${Regex.escape(pkg)}/\S+ t(\d+)""").find(out)?.groupValues?.get(1)
      if (id != null) {
        ShizukuHelper.runCommand("am task resize $id ${w * 28 / 100} ${h * 6 / 100} ${w * 72 / 100} ${h * 55 / 100}")
        flash("Ventana flotante abierta")
      } else if (r.contains("Error") || r.contains("Warning")) {
        flash(r.lines().first().take(80))
      } else {
        flash("Abierta, pero no pude ajustar la ventana")
      }
    }.start()
  }

  private fun addApp(pkg: String) {
    val s = saved().toMutableList()
    if (pkg !in s) s.add(pkg)
    store(s)
    adding = false
    refresh()
  }

  private fun removeApp(pkg: String) {
    store(saved().filter { it != pkg })
    refresh()
  }

  private fun row(icon: Drawable, name: String, onClick: () -> Unit, onLong: (() -> Unit)?): View {
    val r = LinearLayout(ctx)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(dp(8), dp(6), dp(8), dp(6))
    val iv = ImageView(ctx)
    iv.setImageDrawable(icon)
    r.addView(iv, LinearLayout.LayoutParams(dp(36), dp(36)))
    val tv = TextView(ctx)
    tv.text = name
    tv.setTextColor(Color.WHITE)
    tv.textSize = 14f
    tv.maxLines = 1
    tv.setPadding(dp(10), 0, 0, 0)
    r.addView(tv)
    r.setOnClickListener { onClick() }
    if (onLong != null) {
      r.setOnLongClickListener {
        onLong()
        true
      }
    }
    return r
  }

  private fun hint(text: String) {
    val t = TextView(ctx)
    t.text = text
    t.setTextColor(Color.parseColor("#8A9498"))
    t.textSize = 12f
    t.setPadding(dp(8), dp(8), dp(8), dp(8))
    list?.addView(t)
  }

  @Suppress("DEPRECATION")
  private fun refresh() {
    val l = list ?: return
    l.removeAllViews()
    title?.text = if (adding) "Elige una app" else "Apps flotantes"
    addBtn?.text = if (adding) "←" else "＋"
    val pm = ctx.packageManager
    if (adding) {
      val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      val all = pm.queryIntentActivities(q, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }
      if (all.isEmpty()) hint("No pude listar las apps")
      for (ri in all) {
        val pkg = ri.activityInfo.packageName
        l.addView(row(ri.loadIcon(pm), ri.loadLabel(pm).toString(), { addApp(pkg) }, null))
      }
    } else {
      val pkgs = saved()
      if (pkgs.isEmpty()) hint("Toca ＋ para añadir una app")
      for (pkg in pkgs) {
        try {
          val ic = pm.getApplicationIcon(pkg)
          val nm = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
          l.addView(row(ic, nm, { launch(pkg) }, { removeApp(pkg) }))
        } catch (e: Exception) { }
      }
      if (pkgs.isNotEmpty()) hint("Toca para abrir · mantén para quitar")
    }
  }

  private fun btn(label: String, onClick: () -> Unit): TextView {
    val b = TextView(ctx)
    b.text = label
    b.textSize = 20f
    b.setTextColor(teal)
    b.setPadding(dp(12), dp(4), dp(12), dp(4))
    b.setOnClickListener { onClick() }
    return b
  }

  fun show() {
    if (win != null) return
    adding = false
    val box = LinearLayout(ctx)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(dp(10), dp(8), dp(10), dp(8))
    box.background = GradientDrawable().apply {
      setColor(Color.parseColor("#F00B1412"))
      setStroke(dp(2), teal)
      cornerRadius = dp(12).toFloat()
    }
    val head = LinearLayout(ctx)
    head.orientation = LinearLayout.HORIZONTAL
    head.gravity = Gravity.CENTER_VERTICAL
    val tt = TextView(ctx)
    tt.setTextColor(Color.WHITE)
    tt.textSize = 15f
    tt.setPadding(dp(8), 0, 0, 0)
    head.addView(tt, LinearLayout.LayoutParams(0, -2, 1f))
    val ab = btn("＋") {
      adding = !adding
      refresh()
    }
    head.addView(ab)
    head.addView(btn("✕") { hide() })
    val sv = ScrollView(ctx)
    val l = LinearLayout(ctx)
    l.orientation = LinearLayout.VERTICAL
    sv.addView(l)
    box.addView(head)
    box.addView(sv, LinearLayout.LayoutParams(-1, dp(230)))
    list = l
    title = tt
    addBtn = ab
    val p = WindowManager.LayoutParams(
      dp(300), -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    )
    p.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    p.x = dp(16)
    win = box
    wm.addView(box, p)
    refresh()
  }
}
