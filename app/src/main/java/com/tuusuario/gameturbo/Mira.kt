package com.tuusuario.gameturbo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
