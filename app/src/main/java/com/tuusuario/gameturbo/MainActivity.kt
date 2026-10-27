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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val Red = Color(0xFFFF1834)
private val RedBright = Color(0xFFFF3349)
private val Bg = Color(0xFF050507)
private val Panel = Color(0xFF0D0D11)
private val Panel2 = Color(0xFF15151B)
private val Edge = Color(0xFF5D101B)
private val White = Color(0xFFF5F5F7)
private val Muted = Color(0xFFB7B7C0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Bg, surface = Panel)) { MainScreen() } }
        ShizukuHelper.requestPermission()
    }
}

fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    context.startActivity(intent)
}

fun startGame(context: Context, pkg: String) {
    if (!Settings.canDrawOverlays(context)) {
        openOverlaySettings(context)
        return
    }
    try { context.startService(Intent(context, OverlayService::class.java)) } catch (_: Exception) { }
    context.packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}

fun stopGameplay(context: Context) { context.stopService(Intent(context, OverlayService::class.java)) }

fun batteryPercent(context: Context): Int {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (scale > 0) level * 100 / scale else 0
}

fun nowText(): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

private fun Modifier.circuitRed(): Modifier = drawBehind {
    val rnd = Random(11)
    val line = Color(0x24FF1834)
    repeat(26) {
        var x = rnd.nextFloat() * size.width
        var y = rnd.nextFloat() * size.height
        repeat(3) {
            val len = 25f + rnd.nextFloat() * 115f
            val nx = if (rnd.nextBoolean()) x + len else x
            val ny = if (nx == x) y + len else y + len * 0.35f
            drawLine(line, Offset(x, y), Offset(nx, ny), strokeWidth = 1.2f)
            x = nx; y = ny
        }
    }
}

@Composable
private fun NeonPanel(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(17.dp)).background(Brush.verticalGradient(listOf(Color(0xFF17171D), Panel, Bg)))
        .border(if (selected) 2.dp else 1.dp, if (selected) RedBright else Edge, RoundedCornerShape(17.dp)), content = content)
}

@Composable
private fun LabelButton(text: String, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(if (active) Brush.horizontalGradient(listOf(Color(0xFFB9001B), Red)) else Brush.horizontalGradient(listOf(Panel2, Bg)))
        .border(1.dp, if (active) RedBright else Edge, RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = White, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun GameRow(name: String, icon: Drawable, selected: Boolean, onClick: () -> Unit) {
    val bmp = remember(icon) { icon.toBitmap(96, 96).asImageBitmap() }
    Row(Modifier.fillMaxWidth().height(if (selected) 68.dp else 61.dp).padding(horizontal = 8.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(10.dp)).background(if (selected) Brush.horizontalGradient(listOf(Color(0xCC65000D), Color(0x442B080D), Color.Transparent)) else Brush.horizontalGradient(listOf(Panel, Bg)))
        .border(if (selected) 1.5.dp else 0.5.dp, if (selected) RedBright else Color(0xFF352027), RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(bmp, contentDescription = name, modifier = Modifier.size(if (selected) 47.dp else 42.dp).clip(RoundedCornerShape(9.dp)).alpha(if (selected) 1f else 0.9f))
        Spacer(Modifier.width(13.dp))
        Text(name, color = if (selected) White else Muted, fontSize = if (selected) 15.sp else 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("›", color = Red, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var games by remember { mutableStateOf(GameLibrary.getInstalledGames(context)) }
    var selectedPackage by remember { mutableStateOf(games.firstOrNull()?.packageName.orEmpty()) }
    var time by remember { mutableStateOf(nowText()) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Todos") }
    var section by remember { mutableStateOf("Juegos") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var recent by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("") }
    val battery by produceState(initialValue = batteryPercent(context)) {
        while (true) { value = batteryPercent(context); delay(30_000) }
    }
    LaunchedEffect(Unit) { while (true) { time = nowText(); delay(20_000) } }
    val selectedIndex = games.indexOfFirst { it.packageName == selectedPackage }.let { if (it < 0) 0 else it }
    val current = games.getOrNull(selectedIndex)
    val filtered = games.filter { game ->
        val matchesQuery = game.name.contains(query, ignoreCase = true)
        val matchesTab = when (tab) {
            "Favoritos" -> favorites.contains(game.packageName)
            "Recientes" -> recent.contains(game.packageName)
            "Más jugados" -> recent.contains(game.packageName)
            else -> true
        }
        matchesQuery && matchesTab
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050507), Color(0xFF0C080A), Bg))).circuitRed().padding(10.dp)) {
        Column(Modifier.fillMaxSize()) {
            // Top status/title strip
            NeonPanel(Modifier.fillMaxWidth().height(55.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎮", fontSize = 27.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("GAME PLAY", color = White, fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                        style = TextStyle(shadow = Shadow(Red, blurRadius = 14f)))
                    Spacer(Modifier.weight(1f))
                    Text(time, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFB80D24)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text("$battery%", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(20.dp))
                    Text("◉  ▮▮  ▰", color = White, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Left navigation
                NeonPanel(Modifier.weight(0.20f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(9.dp)) {
                        Text("◆", color = Red, fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 7.dp), style = TextStyle(shadow = Shadow(Red, blurRadius = 20f)))
                        listOf("Juegos" to "🎮", "Categorías" to "▦", "Instalados" to "⇩", "Configuración" to "⚙").forEach { (name, icon) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (section == name) Brush.horizontalGradient(listOf(Color(0xFF970018), Color(0x442C090F))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                                .border(1.dp, if (section == name) Red else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { section = name; if (name == "Instalados") { tab = "Todos" }; if (name == "Categorías") message = "Categorías: se muestran las aplicaciones con acceso de inicio."; if (name == "Configuración") message = "Concede el permiso de ventanas flotantes para iniciar el HUD." }
                                .padding(horizontal = 9.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(icon, color = if (section == name) RedBright else White, fontSize = 21.sp)
                                Spacer(Modifier.width(9.dp))
                                Text(name, color = if (section == name) White else Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (section == name) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("GAME\nPLAY", modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp), color = Red, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = TextStyle(shadow = Shadow(Red, blurRadius = 15f)))
                    }
                }
                // Center library
                NeonPanel(Modifier.weight(0.48f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(9.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("Todos", "Recientes", "Más jugados", "Favoritos").forEach { t -> LabelButton(t, tab == t, Modifier.weight(1f)) { tab = t; section = "Juegos" } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xFF09090C)).border(1.dp, Edge, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⌕", color = Red, fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("Buscar juego...", color = Color(0xFF777780), fontSize = 13.sp)
                                BasicTextField(query, onValueChange = { query = it }, singleLine = true, textStyle = TextStyle(color = White, fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
                            }
                            Text("${filtered.size}", color = Muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        if (filtered.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (games.isEmpty()) "No se detectaron juegos instalados" else "No hay juegos en este filtro", color = Muted, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                itemsIndexed(filtered, key = { _, g -> g.packageName }) { _, game ->
                                    GameRow(game.name, game.icon, game.packageName == current?.packageName) {
                                        selectedPackage = game.packageName
                                        recent = (listOf(game.packageName) + recent.filterNot { it == game.packageName }).take(20)
                                        message = ""
                                    }
                                }
                            }
                        }
                    }
                }
                // Selected game detail and actions
                NeonPanel(Modifier.weight(0.32f).fillMaxHeight(), selected = current != null) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        if (current != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val bmp = remember(current.packageName) { current.icon.toBitmap(128, 128).asImageBitmap() }
                                Image(bmp, contentDescription = current.name, modifier = Modifier.size(86.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, Red, RoundedCornerShape(16.dp)))
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(current.name, color = White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = Shadow(Red, blurRadius = 12f)))
                                    Spacer(Modifier.height(6.dp))
                                    Text("Juego seleccionado", color = Muted, fontSize = 12.sp)
                                    Text(current.packageName, color = Color(0xFF85858F), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("GAME PLAY", color = Red, fontSize = 25.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = Shadow(Red, blurRadius = 18f)))
                                Text("Tu biblioteca de juegos", color = Muted, fontSize = 13.sp)
                            }
                        }
                        Column {
                            LabelButton("▶    Start Game", active = true, modifier = Modifier.fillMaxWidth().height(62.dp)) {
                                if (current != null) {
                                    recent = (listOf(current.packageName) + recent.filterNot { it == current.packageName }).take(20)
                                    startGame(context, current.packageName)
                                } else message = "Selecciona un juego primero."
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    LabelButton("■", modifier = Modifier.fillMaxWidth().height(52.dp)) { stopGameplay(context); message = "HUD detenido." }
                                    Text("Detener", color = Muted, fontSize = 11.sp)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    LabelButton("⟳", modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                        games = GameLibrary.getInstalledGames(context)
                                        if (games.none { it.packageName == selectedPackage }) selectedPackage = games.firstOrNull()?.packageName.orEmpty()
                                        message = "Biblioteca actualizada."
                                    }
                                    Text("Actualizar", color = Muted, fontSize = 11.sp)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    LabelButton("⚙", modifier = Modifier.fillMaxWidth().height(52.dp)) { openOverlaySettings(context) }
                                    Text("Permiso", color = Muted, fontSize = 11.sp)
                                }
                            }
                            if (message.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(message, color = RedBright, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text("RED • BLACK • PERFORMANCE", color = Color(0xFF6F252E), fontSize = 9.sp, letterSpacing = 1.3.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}
