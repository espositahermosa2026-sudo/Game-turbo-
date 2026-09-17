package com.tuusuario.gameturbo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BgDark = Color(0xFF0A0A0F)
val VioletNeon = Color(0xFF9D4EDD)
val VioletDeep = Color(0xFF5A189A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
        ShizukuHelper.requestPermission()
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val games = remember { GameLibrary.getInstalledGames(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("GAMEPLAY", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } else {
                    val serviceIntent = Intent(context, OverlayService::class.java)
                    context.startService(serviceIntent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = VioletNeon)
        ) {
            Text("Activar Gameplay", color = Color.White)
        }

        Spacer(Modifier.height(32.dp))
        Text("MIS JUEGOS", color = VioletNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (games.isEmpty()) {
            Text("No se detectaron juegos instalados", color = Color.Gray, fontSize = 12.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(games) { game ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                val launchIntent =
                                    context.packageManager.getLaunchIntentForPackage(game.packageName)
                                launchIntent?.let { context.startActivity(it) }
                            }
                    ) {
                        Image(
                            bitmap = game.icon.toBitmap().asImageBitmap(),
                            contentDescription = game.name,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            game.name,
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
