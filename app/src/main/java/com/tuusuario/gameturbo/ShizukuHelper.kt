package com.tuusuario.gameturbo

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

    // ---- Datos en vivo para el medidor (los lee OverlayService) ----
    @Volatile var fps: String = "--"
    @Volatile var cpu: String = "--"
    @Volatile private var running = false
    @Volatile private var generation = 0
    private var layerName: String? = null
    private var lastFind = 0L
    private var lastTotal = 0L
    private var lastIdle = 0L

    fun isReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        if (Shizuku.isPreV11()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1001)
        }
    }

    fun runCommand(command: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun freeRam() {
        runCommand("am kill-all")
    }

    private fun brief(out: String): String =
        out.trim().lines().firstOrNull { it.isNotBlank() }?.take(70) ?: "Listo"

    fun highPerformance(enable: Boolean): String {
        val governor = if (enable) "performance" else "schedutil"
        val a = runCommand("cmd power set-fixed-performance-mode-enabled $enable")
        runCommand("settings put global low_power 0")
        runCommand("for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $governor > \$cpu; done")
        return if (a.isBlank()) {
            if (enable) "modo rendimiento activado" else "modo rendimiento desactivado"
        } else {
            brief(a)
        }
    }

    fun blockCalls(enable: Boolean) {
        runCommand("cmd notification set_dnd ${if (enable) "alarms" else "off"}")
    }

    fun blockNotifications(enable: Boolean): String {
        return brief(runCommand("cmd notification set_dnd ${if (enable) "alarms" else "off"}"))
    }

    // Cierra todas las apps de terceros menos el juego, el teclado, el launcher, esta app y Shizuku
    fun cleanRam(): String {
        val keep = mutableSetOf("com.tuusuario.gameturbo", "moe.shizuku.privileged.api")
        keep.addAll(knownGames)
        foregroundPackage()?.let { keep.add(it) }
        val ime = runCommand("settings get secure default_input_method").trim().substringBefore("/")
        if (ime.isNotEmpty() && ime != "null") keep.add(ime)
        val home = runCommand("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .trim().lines().lastOrNull()?.trim()?.substringBefore("/") ?: ""
        if (home.isNotEmpty()) keep.add(home)
        val pkgs = runCommand("pm list packages -3")
            .lines()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotEmpty() && it !in keep }
        if (pkgs.isNotEmpty()) {
            runCommand(pkgs.joinToString("; ") { "am force-stop $it" })
        }
        runCommand("am kill-all")
        runCommand("pm trim-caches 999G")
        return "${pkgs.size} apps cerradas"
    }

    // Bloquea el gesto de regresar de los bordes izquierdo y derecho (deja la sensibilidad en 0)
    private var gBlocked = false
    private var prevL = ""
    private var prevR = ""

    private fun restoreKey(key: String, prev: String) {
        if (prev.isEmpty() || prev == "null") {
            runCommand("settings delete secure $key")
        } else {
            runCommand("settings put secure $key $prev")
        }
    }

    fun blockGestures(enable: Boolean): String {
        val kL = "back_gesture_inset_scale_left"
        val kR = "back_gesture_inset_scale_right"
        if (enable) {
            if (!gBlocked) {
                prevL = runCommand("settings get secure $kL").trim()
                prevR = runCommand("settings get secure $kR").trim()
                gBlocked = true
            }
            runCommand("settings put secure $kL 0; settings put secure $kR 0")
            val now = runCommand("settings get secure $kL").trim()
            return "sensibilidad de bordes en $now"
        }
        restoreKey(kL, prevL)
        restoreKey(kR, prevR)
        gBlocked = false
        return "sensibilidad de bordes restaurada"
    }

    // Graba la pantalla en partes de 3 minutos en Películas/GameTurbo (sin audio)
    fun recordScreen(enable: Boolean): String {
        val flag = "/data/local/tmp/gt_rec"
        if (enable) {
            runCommand(
                "rm -f $flag; pkill -2 screenrecord; sleep 1; mkdir -p /sdcard/Movies/GameTurbo; touch $flag; " +
                    "nohup sh -c 'while [ -f $flag ]; do screenrecord --bit-rate 8000000 --time-limit 180 " +
                    "/sdcard/Movies/GameTurbo/rec_\$(date +%Y%m%d_%H%M%S).mp4; done' > /dev/null 2>&1 &"
            )
            val pid = runCommand("sleep 2; pidof screenrecord").trim()
            return if (pid.isEmpty()) "no se pudo iniciar la grabación" else "grabando (partes de 3 min)"
        }
        runCommand("rm -f $flag; pkill -2 screenrecord || kill -2 \$(pidof screenrecord); sleep 2")
        return "guardado en Películas/GameTurbo"
    }

    // ---- Toques simulados para Edit Keymap ----
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var motionOk = true

    fun touchDown(x: Int, y: Int) {
        exec.execute {
            if (motionOk) {
                val r = runCommand("input motionevent DOWN $x $y")
                if (r.isNotBlank()) motionOk = false
            }
        }
    }

    fun touchUp(x: Int, y: Int) {
        exec.execute {
            if (motionOk) {
                runCommand("input motionevent UP $x $y")
            } else {
                runCommand("input tap $x $y")
            }
        }
    }

    // ---- Toque multitáctil: escribe en el dispositivo de la pantalla táctil como un dedo más ----
    private var tDev = ""
    private var tMaxX = 0
    private var tMaxY = 0
    private var tSlot = 9
    private var tMajor = false
    private var tPress = false
    private var tReady = false
    private var tTried = false
    private var tid = 2000

    private fun initTouch() {
        if (tTried) return
        tTried = true
        var dev = ""
        var maxX = 0
        var maxY = 0
        var slot = 9
        var major = false
        var press = false
        var direct = false
        var hasX = false
        fun commit() {
            if (hasX && direct && dev.isNotEmpty() && !tReady) {
                tDev = dev
                tMaxX = maxX
                tMaxY = maxY
                tSlot = slot
                tMajor = major
                tPress = press
                tReady = true
            }
        }
        val axis = Regex("""^(?:ABS \(0003\):\s*)?([0-9a-fA-F]{4})\s*:\s*value.*?max (\d+)""")
        for (raw in runCommand("getevent -p").lines()) {
            val l = raw.trim()
            if (l.startsWith("add device")) {
                commit()
                dev = l.substringAfter(": ").trim()
                maxX = 0
                maxY = 0
                slot = 9
                major = false
                press = false
                direct = false
                hasX = false
            } else if (l.contains("INPUT_PROP_DIRECT")) {
                direct = true
            } else {
                val m = axis.find(l)
                if (m != null) {
                    val mx = m.groupValues[2].toIntOrNull() ?: 0
                    when (m.groupValues[1].lowercase()) {
                        "0035" -> {
                            hasX = true
                            maxX = mx
                        }
                        "0036" -> maxY = mx
                        "002f" -> slot = mx
                        "0030" -> major = true
                        "003a" -> press = true
                    }
                }
            }
        }
        commit()
    }

    // Toca en (sx, sy) de la pantalla sin cortar los dedos que ya estén tocando. Devuelve false si no se pudo.
    fun rawTap(sx: Int, sy: Int, ms: Int, w: Int, h: Int, rot: Int): Boolean {
        initTouch()
        if (!tReady) return false
        val land = rot == 1 || rot == 3
        val wn = if (land) h else w
        val hn = if (land) w else h
        val nx: Int
        val ny: Int
        when (rot) {
            1 -> {
                nx = wn - 1 - sy
                ny = sx
            }
            2 -> {
                nx = wn - 1 - sx
                ny = hn - 1 - sy
            }
            3 -> {
                nx = sy
                ny = hn - 1 - sx
            }
            else -> {
                nx = sx
                ny = sy
            }
        }
        val rx = (nx.toLong() * (tMaxX + 1) / wn).toInt().coerceIn(0, tMaxX)
        val ry = (ny.toLong() * (tMaxY + 1) / hn).toInt().coerceIn(0, tMaxY)
        val slot = minOf(tSlot, 9)
        tid = if (tid > 60000) 2000 else tid + 1
        val e = "sendevent $tDev"
        val sb = StringBuilder()
        sb.append("$e 3 47 $slot; $e 3 57 $tid; $e 3 53 $rx; $e 3 54 $ry; ")
        if (tMajor) sb.append("$e 3 48 6; ")
        if (tPress) sb.append("$e 3 58 60; ")
        sb.append("$e 0 0 0; sleep ${String.format(java.util.Locale.US, "%.3f", ms / 1000.0)}; ")
        sb.append("$e 3 47 $slot; $e 3 57 -1; $e 0 0 0")
        val out = runCommand("( $sb ) 2>&1")
        if (out.isNotBlank()) {
            tReady = false
            return false
        }
        return true
    }

    fun rawRelease() {
        if (!tReady) return
        val e = "sendevent $tDev"
        runCommand("( $e 3 47 ${minOf(tSlot, 9)}; $e 3 57 -1; $e 0 0 0 ) 2>&1")
    }

    private val lock = Any()
    private var pendingBright = -1
    private var brightBusy = false

    fun setBrightness(v: Int) {
        synchronized(lock) {
            pendingBright = v
            if (brightBusy) return
            brightBusy = true
        }
        Thread {
            while (true) {
                val b = synchronized(lock) {
                    val x = pendingBright
                    if (x < 0) brightBusy = false else pendingBright = -1
                    x
                }
                if (b < 0) return@Thread
                runCommand("settings put system screen_brightness_mode 0; settings put system screen_brightness $b")
            }
        }.start()
    }

    fun rotateScreen(enable: Boolean, rotation: Int) {
        if (enable) {
            runCommand("cmd window user-rotation lock $rotation || wm set-user-rotation lock $rotation")
        } else {
            runCommand("cmd window user-rotation free || wm set-user-rotation free")
        }
    }

    fun toggleVibration(enable: Boolean) {
        val mode = if (enable) 0 else 2
        runCommand("settings put system vibrate_when_ringing $mode")
        runCommand("settings put system haptic_feedback_enabled ${if (enable) 0 else 1}")
    }

    // ---- Medición de CPU y FPS ----

    private val knownGames = listOf("com.dts.freefiremax", "com.dts.freefireth")

    private var users = 0

    fun startStats() {
        users++
        if (running) return
        running = true
        val gen = ++generation
        lastTotal = 0L
        Thread {
            while (running && gen == generation) {
                if (isReady()) {
                    try { cpu = readCpu() } catch (e: Exception) { cpu = "--" }
                    try { fps = readFps() } catch (e: Exception) { fps = "--" }
                    try { maybeFindLayer() } catch (e: Exception) { }
                }
                Thread.sleep(1000)
            }
        }.start()
    }

    fun stopStats() {
        if (users > 0) users--
        if (users == 0) running = false
    }

    private fun readCpu(): String {
        val line = runCommand("head -n1 /proc/stat").trim()
        val p = line.split(Regex("\\s+"))
        if (p.size < 8 || p[0] != "cpu") return "--"
        val v = p.drop(1).mapNotNull { it.toLongOrNull() }
        if (v.size < 7) return "--"
        val idle = v[3] + v[4]
        val total = v.take(8).sum()
        val dTotal = total - lastTotal
        val dIdle = idle - lastIdle
        val result = if (lastTotal > 0L && dTotal > 0L) {
            (100L * (dTotal - dIdle) / dTotal).toInt().coerceIn(0, 100).toString()
        } else {
            "--"
        }
        lastTotal = total
        lastIdle = idle
        return result
    }

    private fun parseTimes(lines: List<String>): List<Long> {
        return lines.mapNotNull { line ->
            val c = line.trim().split(Regex("\\s+"))
            if (c.size >= 3) c[1].toLongOrNull() else null
        }.filter { it > 0L && it < Long.MAX_VALUE }
    }

    private fun latencyTimes(layer: String): List<Long> {
        val out = runCommand("dumpsys SurfaceFlinger --latency '$layer'")
        return parseTimes(out.trim().split("\n").drop(1))
    }

    private fun foregroundPackage(): String? {
        val out = runCommand(
            "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n 3"
        )
        return Regex("""u\d+ ([A-Za-z0-9_.]+)/""").find(out)?.groupValues?.get(1)
    }

    private fun fpsOf(times: List<Long>): Int? {
        if (times.size < 2) return null
        val newest = times.maxOrNull() ?: return null
        val window = times.filter { it > newest - 1_000_000_000L }
        val oldest = window.minOrNull() ?: return null
        val span = newest - oldest
        if (window.size < 2 || span <= 0L) return null
        return ((window.size - 1) * 1_000_000_000.0 / span).toInt().coerceIn(0, 240)
    }

    // Busca la capa del juego que dibuja más cuadros por segundo (solo 2 comandos en total)
    private fun findBestLayer(): String? {
        val all = runCommand("dumpsys SurfaceFlinger --list")
            .split("\n")
            .map { it.trim() }
            .filter {
                it.isNotEmpty() && !it.contains("'") &&
                    !it.startsWith("Background for") && !it.startsWith("Bounds for")
            }
        var cands = all.filter { l -> knownGames.any { l.contains(it) } }
        if (cands.isEmpty()) {
            val pkg = foregroundPackage() ?: return null
            cands = all.filter { it.contains(pkg) }
        }
        if (cands.isEmpty()) return null
        val cmd = cands.joinToString("; ") {
            "echo '@@" + it + "'; dumpsys SurfaceFlinger --latency '" + it + "'"
        }
        val data = runCommand(cmd).split("@@").drop(1).mapNotNull { block ->
            val lines = block.split("\n")
            val times = parseTimes(lines.drop(2))
            if (times.size >= 5) lines[0].trim() to times else null
        }
        if (data.isEmpty()) return null
        val newestAll = data.maxOf { it.second.maxOrNull() ?: 0L }
        var best: String? = null
        var bestFps = -1
        for ((name, times) in data) {
            val newest = times.maxOrNull() ?: continue
            if (newest < newestAll - 2_000_000_000L) continue
            val f = fpsOf(times) ?: continue
            if (f > bestFps) {
                bestFps = f
                best = name
            }
        }
        return best
    }

    private fun maybeFindLayer() {
        val now = System.currentTimeMillis()
        val due = if (layerName == null) now - lastFind > 2000L else now - lastFind > 15000L
        if (due) {
            lastFind = now
            findBestLayer()?.let { layerName = it }
        }
    }

    private fun readFps(): String {
        val name = layerName ?: return "--"
        val f = fpsOf(latencyTimes(name))
        if (f == null) {
            layerName = null
            lastFind = 0L
            return "--"
        }
        return f.toString()
    }
}