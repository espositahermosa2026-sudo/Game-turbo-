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

    fun highPerformance(enable: Boolean) {
        val governor = if (enable) "performance" else "schedutil"
        runCommand("for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $governor > \$cpu; done")
    }

    fun blockCalls(enable: Boolean) {
        val mode = if (enable) 3 else 0
        runCommand("cmd notification set_dnd $mode")
    }

    fun blockNotifications(enable: Boolean) {
        val mode = if (enable) 3 else 0
        runCommand("cmd notification set_dnd $mode")
    }

    fun toggleVibration(enable: Boolean) {
        val mode = if (enable) 0 else 2
        runCommand("settings put system vibrate_when_ringing $mode")
        runCommand("settings put system haptic_feedback_enabled ${if (enable) 0 else 1}")
    }

    // ---- Medición de CPU y FPS ----

    private val knownGames = listOf("com.dts.freefiremax", "com.dts.freefireth")

    fun startStats() {
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
        running = false
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
