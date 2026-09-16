package com.tuusuario.gameturbo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BgDark = Color(0xFF0A0A0F)
val VioletNeon = Color(0xFF9D4EDD)
val VioletDeep = Color(0xFF5A189A)
val LavenderLight = Color(0xFFE0AAFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameTurboScreen()
        }
    }
}

@Composable
fun GameTurboScreen() {
    var turboOn by remember { mutableStateOf(false) }
    var ramFree by remember { mutableStateOf(true) }
    var highPerf by remember { mutableStateOf(true) }
    var blockNotifs by remember { mutableStateOf(true) }
    var blockCalls by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("GAME TURBO", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(VioletNeon, VioletDeep)))
                .clickable { turboOn = !turboOn },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (turboOn) "ON" else "OFF",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(40.dp))

        TurboCard("Liberar RAM", ramFree) { ramFree = it }
        TurboCard("Modo alto rendimiento", highPerf) { highPerf = it }
        TurboCard("Bloquear notificaciones", blockNotifs) { blockNotifs = it }
        TurboCard("Bloquear llamadas", blockCalls) { blockCalls = it }
    }
}

@Composable
fun TurboCard(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF17121F))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = LavenderLight, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VioletNeon,
                checkedTrackColor = VioletDeep
            )
        )
    }
}

