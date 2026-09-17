package com.tuusuario.gameturbo

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

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
}
