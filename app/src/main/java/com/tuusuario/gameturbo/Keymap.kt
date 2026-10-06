package com.tuusuario.gameturbo

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class KmTarget(var x: Int, var y: Int, var type: Int, var interval: Int, var duration: Int) {
 var v: KmCircle? = null
 var p: WindowManager.LayoutParams? = null
}

class KmTrigger(var x: Int, var y: Int, var size: Int, var block: Boolean) {
 val targets = ArrayList<KmTarget>()
 var v: KmCircle? = null
 var p: WindowManager.LayoutParams? = null
 @Volatile var busy = false
 @Volatile var holding = false
}

class KmManager(private val ctx: Context, private val wm: WindowManager) {
 private val d = ctx.resources.displayMetrics.density
 private val prefs = ctx.getSharedPreferences("km", Context.MODE_PRIVATE)
 private val hd = Handler(Looper.getMainLooper())
 private val trigs = ArrayList<KmTrigger>()
 private val typeNames = arrayOf("Toque", "Repetir", "Doble", "Soltar")
 private val ts = (56 * d).toInt()
 private val settings = KmSettings(ctx, wm)
 private var shown = false
 private var editing = false
 private var selT = -1
 private var selK = -1
 private var guide: KmLines? = null
 private var bar: View? = null
 private var panel: View? = null
 private var nameBtn: TextView? = null
 private var seek: SeekBar? = null
 private var snapshot = ""
 private var panelLp: WindowManager.LayoutParams? = null
 private var panelTop = true

 private fun dp(v: Int): Int = (v * d).toInt()

 // ---------- datos ----------
 private fun load(raw: String) {
  trigs.clear()
  for (blk in raw.split("|")) {
   val parts = blk.split(";")
   val h = parts[0].split(",").mapNotNull { it.toIntOrNull() }
   if (h.size != 4) continue
   val t = KmTrigger(h[0], h[1], h[2], h[3] != 0)
   for (i in 1 until parts.size) {
    val g = parts[i].split(",").mapNotNull { it.toIntOrNull() }
    if (g.size == 5) t.targets.add(KmTarget(g[0], g[1], g[2].coerceIn(0, 3), g[3], g[4]))
    if (g.size == 3) t.targets.add(KmTarget(g[0], g[1], 0, 50, 70))
   }
   trigs.add(t)
  }
 }

 private fun serialize(): String = trigs.joinToString("|") { t ->
  "${t.x},${t.y},${t.size},${if (t.block) 1 else 0}" +
   t.targets.joinToString("") { ";${it.x},${it.y},${it.type},${it.interval},${it.duration}" }
 }

 private fun save() {
  prefs.edit().putString("data", serialize()).apply()
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

 private fun lp(cx: Int, cy: Int, size: Int, touch: Boolean): WindowManager.LayoutParams {
  var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
   WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
  if (!touch) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
  val p = WindowManager.LayoutParams(
   size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, f, PixelFormat.TRANSLUCENT
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

 // ---------- ejecutar ----------
 private fun tap(g: KmTarget) {
  ShizukuHelper.runCommand("input swipe ${g.x} ${g.y} ${g.x} ${g.y} ${g.duration}")
 }

 private fun press(t: KmTrigger, down: Boolean) {
  t.holding = down
  if (!down || t.busy || t.targets.isEmpty()) return
  t.busy = true
  Thread {
   val all = t.targets.toList()
   for (g in all) {
    if (g.type == 0) {
     tap(g)
    } else if (g.type == 2) {
     tap(g)
     Thread.sleep(60)
     tap(g)
    }
   }
   val reps = all.filter { it.type == 1 }
   while (t.holding) {
    if (reps.isEmpty()) {
     Thread.sleep(10)
    } else {
     for (g in reps) {
      if (!t.holding) break
      tap(g)
      Thread.sleep(g.interval.toLong())
     }
    }
   }
   for (g in all) {
    if (g.type == 3) tap(g)
   }
   t.busy = false
  }.start()
 }

 // ---------- ventanas ----------
 private fun addTrigger(i: Int) {
  val t = trigs[i]
  val v = KmCircle(ctx, 0, d)
  v.title = "Trigger" + (i + 1)
  v.quiet = !editing
  val p = lp(t.x, t.y, t.size, true)
  var ix = 0
  var iy = 0
  var tx = 0f
  var ty = 0f
  var down = false
  v.setOnTouchListener { _, e ->
   if (editing) {
    when (e.action) {
     MotionEvent.ACTION_DOWN -> {
      ix = t.x
      iy = t.y
      tx = e.rawX
      ty = e.rawY
      pick(i, -1)
     }
     MotionEvent.ACTION_MOVE -> {
      t.x = ix + (e.rawX - tx).toInt()
      t.y = iy + (e.rawY - ty).toInt()
      p.x = t.x - t.size / 2
      p.y = t.y - t.size / 2
      wm.updateViewLayout(v, p)
      moveGuide()
     }
    }
   } else {
    when (e.action) {
     MotionEvent.ACTION_DOWN -> {
      down = true
      press(t, true)
     }
     MotionEvent.ACTION_MOVE -> {
      val m = dp(12)
      if (down && t.block && (e.x < -m || e.y < -m || e.x > v.width + m || e.y > v.height + m)) {
       down = false
       press(t, false)
      }
     }
     MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
      if (down) {
       down = false
       press(t, false)
      }
     }
    }
   }
   true
  }
  t.v = v
  t.p = p
  wm.addView(v, p)
 }

 private fun addTarget(i: Int, k: Int) {
  val g = trigs[i].targets[k]
  val v = KmCircle(ctx, 1, d)
  v.title = "Target" + (k + 1)
  v.sub = "${g.x},${g.y}"
  val p = lp(g.x, g.y, ts, true)
  var ix = 0
  var iy = 0
  var tx = 0f
  var ty = 0f
  v.setOnTouchListener { _, e ->
   when (e.action) {
    MotionEvent.ACTION_DOWN -> {
     ix = g.x
     iy = g.y
     tx = e.rawX
     ty = e.rawY
     pick(i, k)
    }
    MotionEvent.ACTION_MOVE -> {
     g.x = ix + (e.rawX - tx).toInt()
     g.y = iy + (e.rawY - ty).toInt()
     p.x = g.x - ts / 2
     p.y = g.y - ts / 2
     v.sub = "${g.x},${g.y}"
     wm.updateViewLayout(v, p)
     v.invalidate()
     moveGuide()
    }
   }
   true
  }
  g.v = v
  g.p = p
  wm.addView(v, p)
 }

 private fun clearViews() {
  for (t in trigs) {
   t.v?.let { try { wm.removeView(it) } catch (e: Exception) { } }
   t.v = null
   for (g in t.targets) {
    g.v?.let { try { wm.removeView(it) } catch (e: Exception) { } }
    g.v = null
   }
  }
 }

 private fun buildAll() {
  clearViews()
  for (i in trigs.indices) {
   addTrigger(i)
   if (editing) {
    for (k in trigs[i].targets.indices) addTarget(i, k)
   }
  }
 }

 // ---------- selección ----------
 private fun cur(): Pair<Int, Int>? {
  val t = trigs.getOrNull(selT) ?: return null
  val g = t.targets.getOrNull(selK)
  return if (g != null) Pair(g.x, g.y) else Pair(t.x, t.y)
 }

 private fun moveGuide() {
  val gd = guide ?: return
  val c = cur()
  if (c == null) {
   gd.gx = -1f
  } else {
   gd.gx = c.first.toFloat()
   gd.gy = c.second.toFloat()
  }
  gd.invalidate()
  placePanel()
 }

 private fun placePanel() {
  val v = panel ?: return
  val p = panelLp ?: return
  val c = cur() ?: return
  val ph = if (v.height > 0) v.height else dp(140)
  val pw = if (v.width > 0) v.width else dp(440)
  val (w, h) = screen()
  val left = (w - pw) / 2
  val inX = c.first in (left - ts / 2)..(left + pw + ts / 2)
  val topBand = dp(62)..(dp(62) + ph + ts / 2)
  val botBand = (h - dp(16) - ph - ts / 2)..h
  var top = panelTop
  if (panelTop && inX && c.second in topBand) {
   top = false
  } else if (!panelTop && inX && c.second in botBand) {
   top = true
  }
  if (top != panelTop) {
   panelTop = top
   p.gravity = if (top) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
   p.y = if (top) dp(62) else dp(16)
   wm.updateViewLayout(v, p)
  }
 }

 private fun refreshName() {
  val t = trigs.getOrNull(selT)
  val g = t?.targets?.getOrNull(selK)
  nameBtn?.text = when {
   t == null -> "-"
   g != null -> "Target" + (selK + 1) + " · " + typeNames[g.type]
   else -> "Trigger" + (selT + 1)
  }
  seek?.progress = if (t == null) 0 else (t.size - dp(40)) * 100 / dp(100)
 }

 private fun pick(ti: Int, ki: Int) {
  selT = ti
  selK = ki
  for (i in trigs.indices) {
   val t = trigs[i]
   t.v?.picked = (i == ti && ki < 0)
   t.v?.invalidate()
   for (k in t.targets.indices) {
    t.targets[k].v?.picked = (i == ti && k == ki)
    t.targets[k].v?.invalidate()
   }
  }
  moveGuide()
  refreshName()
 }

 // ---------- acciones del editor ----------
 private fun nudge(dx: Int, dy: Int) {
  val t = trigs.getOrNull(selT) ?: return
  val g = t.targets.getOrNull(selK)
  if (g != null) {
   g.x += dx
   g.y += dy
   val v = g.v
   val p = g.p
   if (v != null && p != null) {
    p.x = g.x - ts / 2
    p.y = g.y - ts / 2
    v.sub = "${g.x},${g.y}"
    wm.updateViewLayout(v, p)
    v.invalidate()
   }
  } else {
   t.x += dx
   t.y += dy
   val v = t.v
   val p = t.p
   if (v != null && p != null) {
    p.x = t.x - t.size / 2
    p.y = t.y - t.size / 2
    wm.updateViewLayout(v, p)
   }
  }
  moveGuide()
 }

 private fun setSize(value: Int) {
  val t = trigs.getOrNull(selT) ?: return
  t.size = value.coerceIn(dp(40), dp(140))
  val v = t.v
  val p = t.p
  if (v != null && p != null) {
   p.width = t.size
   p.height = t.size
   p.x = t.x - t.size / 2
   p.y = t.y - t.size / 2
   wm.updateViewLayout(v, p)
   v.invalidate()
  }
  seek?.progress = (t.size - dp(40)) * 100 / dp(100)
 }

 private fun cmdAddTrigger() {
  val (w, h) = screen()
  val t = KmTrigger(w / 2 - dp(150), h / 2, dp(72), true)
  t.targets.add(KmTarget(w / 2 + dp(120), h / 2, 0, 50, 70))
  trigs.add(t)
  buildAll()
  pick(trigs.size - 1, -1)
 }

 private fun cmdAddTarget() {
  if (trigs.isEmpty()) {
   cmdAddTrigger()
   return
  }
  val i = if (selT in trigs.indices) selT else trigs.size - 1
  val t = trigs[i]
  val (w, h) = screen()
  val n = t.targets.size
  t.targets.add(KmTarget(w / 2 + dp(120) + n * dp(30), h / 2 + (n + 1) * dp(40), 0, 50, 70))
  buildAll()
  pick(i, t.targets.size - 1)
 }

 private fun deleteTarget(ti: Int, k: Int) {
  val t = trigs.getOrNull(ti) ?: return
  clearViews()
  if (k in t.targets.indices) t.targets.removeAt(k)
  buildAll()
  pick(ti, -1)
 }

 private fun cmdTrash() {
  val t = trigs.getOrNull(selT) ?: return
  if (selK in t.targets.indices) {
   deleteTarget(selT, selK)
   return
  }
  clearViews()
  trigs.removeAt(selT)
  if (selT >= trigs.size) selT = trigs.size - 1
  buildAll()
  pick(selT, -1)
 }

 private fun cmdCycle() {
  val all = ArrayList<Pair<Int, Int>>()
  for (i in trigs.indices) {
   all.add(Pair(i, -1))
   for (k in trigs[i].targets.indices) all.add(Pair(i, k))
  }
  if (all.isEmpty()) return
  val n = all[(all.indexOf(Pair(selT, selK)) + 1) % all.size]
  pick(n.first, n.second)
 }

 // ---------- ajustes (panel de la derecha) ----------
 private fun openSettings() {
  val t = trigs.getOrNull(selT) ?: return
  if (selK in t.targets.indices) showTargetSettings(selT, selK) else showTriggerSettings(selT)
 }

 private fun showTriggerSettings(ti: Int) {
  val t = trigs.getOrNull(ti) ?: return
  pick(ti, -1)
  val rows = t.targets.mapIndexed { k, g ->
   Pair("Target " + (k + 1), "x=${g.x}  y=${g.y} | ${typeNames[g.type]}")
  }
  settings.showTrigger(
   "Ajustes del Trigger " + (ti + 1), t.block, rows,
   { on -> t.block = on },
   {
    selT = ti
    cmdAddTarget()
    showTriggerSettings(ti)
   },
   { k -> showTargetSettings(ti, k) },
   { k ->
    deleteTarget(ti, k)
    showTriggerSettings(ti)
   }
  )
 }

 private fun showTargetSettings(ti: Int, ki: Int) {
  val g = trigs.getOrNull(ti)?.targets?.getOrNull(ki) ?: return
  pick(ti, ki)
  settings.showTarget(
   "Ajustes del Target " + (ki + 1), g.type, g.interval, g.duration,
   { type, interval, duration ->
    g.type = type
    g.interval = interval
    g.duration = duration
    showTriggerSettings(ti)
   },
   { showTriggerSettings(ti) }
  )
 }

 // ---------- botones ----------
 private fun tb(label: String, onClick: () -> Unit): TextView {
  val b = TextView(ctx)
  b.text = label
  b.textSize = 15f
  b.setTextColor(Color.WHITE)
  b.gravity = Gravity.CENTER
  b.setPadding(dp(12), dp(9), dp(12), dp(9))
  b.background = GradientDrawable().apply {
   setColor(Color.parseColor("#CC1B5E50"))
   cornerRadius = dp(10).toFloat()
  }
  b.setOnClickListener { onClick() }
  return b
 }

 private fun put(box: LinearLayout, v: View, w: Int = -2, h: Int = -2) {
  val lp = LinearLayout.LayoutParams(w, h)
  lp.setMargins(dp(3), dp(3), dp(3), dp(3))
  box.addView(v, lp)
 }

 private fun arrow(dir: Int, dx: Int, dy: Int): View {
  val b = KmPad(ctx, dir, d)
  val run = object : Runnable {
   override fun run() {
    nudge(dx, dy)
    hd.postDelayed(this, 60)
   }
  }
  b.setOnTouchListener { _, e ->
   when (e.action) {
    MotionEvent.ACTION_DOWN -> {
     nudge(dx, dy)
     hd.postDelayed(run, 400)
    }
    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hd.removeCallbacks(run)
   }
   true
  }
  return b
 }

 private fun box(horizontal: Boolean): LinearLayout {
  val b = LinearLayout(ctx)
  b.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
  b.gravity = Gravity.CENTER
  return b
 }

 private fun overlay(v: View, y: Int): WindowManager.LayoutParams {
  val p = WindowManager.LayoutParams(
   -2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
   PixelFormat.TRANSLUCENT
  )
  p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
  p.y = y
  wm.addView(v, p)
  return p
 }

 private fun buildEditorUi() {
  val (w, h) = screen()
  val g = KmLines(ctx)
  val gp = WindowManager.LayoutParams(
   w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
   PixelFormat.TRANSLUCENT
  )
  gp.gravity = Gravity.TOP or Gravity.START
  if (Build.VERSION.SDK_INT >= 28) {
   gp.layoutInDisplayCutoutMode =
    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }
  guide = g
  wm.addView(g, gp)

  val top = box(true)
  put(top, tb("＋ Trigger") { cmdAddTrigger() })
  put(top, tb("＋ Target") { cmdAddTarget() })
  put(top, tb("✕") { cancel() })
  put(top, tb("✓") { done() })
  bar = top
  overlay(top, dp(6))

  val nb = tb("-") { cmdCycle() }
  nb.minWidth = dp(150)
  nameBtn = nb
  val row1 = box(true)
  put(row1, nb)
  put(row1, tb("🗑") { cmdTrash() })
  put(row1, tb("⚙") { openSettings() })

  val sk = SeekBar(ctx)
  sk.max = 100
  sk.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
   override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
    if (fromUser) setSize(dp(40) + p * dp(100) / 100)
   }
   override fun onStartTrackingTouch(s: SeekBar?) {}
   override fun onStopTrackingTouch(s: SeekBar?) {}
  })
  seek = sk
  val row2 = box(true)
  put(row2, tb("−") { setSize((trigs.getOrNull(selT)?.size ?: 0) - dp(6)) })
  put(row2, sk, dp(120))
  put(row2, tb("+") { setSize((trigs.getOrNull(selT)?.size ?: 0) + dp(6)) })

  val left = box(false)
  left.addView(row1)
  left.addView(row2)

  val cell = dp(40)
  val r1 = box(true)
  put(r1, View(ctx), cell, cell)
  put(r1, arrow(0, 0, -1), cell, cell)
  put(r1, View(ctx), cell, cell)
  val r2 = box(true)
  put(r2, arrow(2, -1, 0), cell, cell)
  put(r2, View(ctx), cell, cell)
  put(r2, arrow(3, 1, 0), cell, cell)
  val r3 = box(true)
  put(r3, View(ctx), cell, cell)
  put(r3, arrow(1, 0, 1), cell, cell)
  put(r3, View(ctx), cell, cell)
  val pad = box(false)
  pad.addView(r1)
  pad.addView(r2)
  pad.addView(r3)

  val pn = box(true)
  pn.setPadding(dp(8), dp(6), dp(8), dp(6))
  pn.background = GradientDrawable().apply {
   setColor(Color.parseColor("#E60B1412"))
   setStroke(dp(2), Color.parseColor("#1DE9B6"))
   cornerRadius = dp(12).toFloat()
  }
  pn.addView(left)
  pn.addView(pad)
  panel = pn
  panelTop = true
  panelLp = overlay(pn, dp(62))
 }

 // ---------- público ----------
 fun setVisible(on: Boolean) {
  if (on) {
   if (!shown) {
    load(prefs.getString("data", "") ?: "")
    shown = true
    buildAll()
   }
  } else {
   if (editing) done()
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
   load(prefs.getString("data", "") ?: "")
   shown = true
  }
  snapshot = serialize()
  editing = true
  buildAll()
  buildEditorUi()
  pick(if (trigs.isEmpty()) -1 else 0, -1)
 }

 private fun closeEditor() {
  editing = false
  settings.close()
  for (v in listOf(bar, panel, guide)) {
   v?.let { try { wm.removeView(it) } catch (e: Exception) { } }
  }
  bar = null
  panel = null
  panelLp = null
  guide = null
  nameBtn = null
  seek = null
  selT = -1
  selK = -1
  buildAll()
 }

 private fun done() {
  save()
  closeEditor()
 }

 private fun cancel() {
  clearViews()
  load(snapshot)
  closeEditor()
 }
}