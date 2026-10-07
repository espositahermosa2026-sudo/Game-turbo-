package com.tuusuario.gameturbo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

val BgDark = Color(0xFF0A0A0F)
val VioletNeon = Color(0xFF9D4EDD)
val VioletDeep = Color(0xFF5A189A)

val PanelFill = Color(0xFF1A0B33)
val PanelBorder = Color(0xFFB04DFF)
val RowHighlight = Color(0xFF6A32B8)
val IconTint = Color(0xFFD6A5FF)
val SoftText = Color(0xFFB9A8D9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContent {
            MainScreen()
        }
        ShizukuHelper.requestPermission()
    }
}

fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}

fun startGame(context: Context, pkg: String) {
    if (!Settings.canDrawOverlays(context)) {
        openOverlaySettings(context)
        return
    }
    context.startService(Intent(context, OverlayService::class.java))
    context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
}

fun stopGameplay(context: Context) {
    context.stopService(Intent(context, OverlayService::class.java))
}

fun batteryPercent(context: Context): Int {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (scale > 0) level * 100 / scale else 0
}

fun nowText(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

// Líneas tipo circuito en el fondo
fun Modifier.circuit(): Modifier = this.drawBehind {
    val rnd = Random(11)
    val line = Color(0x33B04DFF)
    for (i in 0 until 28) {
        var x = rnd.nextFloat() * size.width
        var y = rnd.nextFloat() * size.height
        for (s in 0 until 4) {
            val len = 40f + rnd.nextFloat() * 160f
            var nx = x
            var ny = y
            when (rnd.nextInt(3)) {
                0 -> nx = x + len
                1 -> ny = y + len
                else -> {
                    nx = x + len * 0.7f
                    ny = y + len * 0.7f
                }
            }
            drawLine(line, Offset(x, y), Offset(nx, ny), strokeWidth = 2f)
            x = nx
            y = ny
        }
    }
}

@Composable
fun PanelButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelFill)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun GameRow(name: String, icon: Drawable, selected: Boolean, onClick: () -> Unit) {
    val bmp = remember(icon) { icon.toBitmap().asImageBitmap() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (selected) 84.dp else 72.dp)
            .then(
                if (selected) Modifier
                    .background(Brush.horizontalGradient(listOf(Color(0xCC6A32B8), Color.Transparent)))
                    .border(1.5.dp, Brush.horizontalGradient(listOf(PanelBorder, Color.Transparent)), RectangleShape)
                else Modifier
            )
            .clickable { onClick() }
            .padding(start = if (selected) 20.dp else 28.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = bmp,
            contentDescription = name,
            modifier = Modifier
                .size(if (selected) 56.dp else 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .alpha(if (selected) 1f else 0.6f)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            name,
            color = if (selected) Color.White else SoftText,
            fontSize = if (selected) 20.sp else 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2
        )
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var games by remember { mutableStateOf(GameLibrary.getInstalledGames(context)) }
    var sel by remember { mutableStateOf(0) }
    var time by remember { mutableStateOf(nowText()) }
    val battery by produceState(initialValue = batteryPercent(context)) {
        while (true) {
            value = batteryPercent(context)
            delay(30_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            time = nowText()
            delay(20_000)
        }
    }
    val cur = games.getOrNull(sel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0716), Color(0xFF1A0B33))))
            .circuit()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "GAME PLAY",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        style = TextStyle(shadow = Shadow(color = PanelBorder, blurRadius = 28f))
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        time,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF8E44D6))
                            .border(1.dp, IconTint, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("$battery%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (games.isEmpty()) {
                    Text(
                        "No se detectaron juegos instalados",
                        color = SoftText,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(games) { i, g ->
                            GameRow(g.name, g.icon, i == sel) { sel = i }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(end = 28.dp, bottom = 24.dp, start = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    cur?.name ?: "",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    style = TextStyle(shadow = Shadow(color = PanelBorder, blurRadius = 20f))
                )
                Spacer(Modifier.height(16.dp))
                PanelButton(
                    modifier = Modifier.width(260.dp).height(78.dp),
                    onClick = { if (cur != null) startGame(context, cur.packageName) }
                ) {
                    Text("Start Game", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PanelButton(
                            modifier = Modifier.size(68.dp),
                            onClick = { stopGameplay(context) }
                        ) {
                            Text("■", color = IconTint, fontSize = 26.sp)
                        }
                        Text("Detener", color = SoftText, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PanelButton(
                            modifier = Modifier.size(68.dp),
                            onClick = { games = GameLibrary.getInstalledGames(context) }
                        ) {
                            Text("⟳", color = IconTint, fontSize = 28.sp)
                        }
                        Text("Actualizar", color = SoftText, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PanelButton(
                            modifier = Modifier.size(68.dp),
                            onClick = { openOverlaySettings(context) }
                        ) {
                            Text("⚙", color = IconTint, fontSize = 26.sp)
                        }
                        Text("Permiso", color = SoftText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}