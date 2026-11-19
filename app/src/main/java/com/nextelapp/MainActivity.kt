package com.nextelapp

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF020817)
private val Blue = Color(0xFF087BFF)
private val Cyan = Color(0xFF00D9FF)
private val Red = Color(0xFFE51B23)
private val Green = Color(0xFF38E600)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NextelHome() }
    }
}

@Composable
private fun NextelHome() {
    var selected by remember { mutableStateOf("Inicio") }
    var transmitting by remember { mutableStateOf(false) }
    var fingerDown by remember { mutableStateOf(false) }
    var micMessage by remember { mutableStateOf("Micrófono listo") }
    val context = LocalContext.current
    val capture = remember { PttAudioCapture() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && fingerDown) {
            transmitting = capture.start()
            micMessage = if (transmitting) "Micrófono activo · transmisión aún no conectada" else "No se pudo iniciar el micrófono"
        } else if (!granted) {
            micMessage = "Permiso de micrófono denegado"
        }
    }
    DisposableEffect(Unit) {
        onDispose { capture.stop() }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Navy, surface = Color(0xFF06142B), primary = Blue
    )) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF001B52), Navy, Color.Black)))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nextel App", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("PUSH-TO-TALK", color = Red, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Habla. Conecta. Comunica.", color = Color.LightGray, fontSize = 12.sp)
                }
                IconButton(onClick = { selected = "Configuración" }) {
                    Icon(Icons.Default.Settings, contentDescription = "Configuración", tint = Color.White)
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().border(1.dp, Blue, RoundedCornerShape(24.dp))
                    .background(Color(0xCC020B1D), RoundedCornerShape(24.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("● Conectado", color = Green, fontWeight = FontWeight.Bold)
                Text("Canal: General", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("▮▮▮", color = Green, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(280.dp).border(5.dp, Brush.linearGradient(listOf(Cyan, Blue)), CircleShape)
                    .padding(12.dp).border(3.dp, Color.LightGray, CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF0759D8), Color(0xFF020B24))), CircleShape))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎙", fontSize = 76.sp)
                    Text(if (transmitting) "TRANSMITIENDO" else "MANTENÉ PRESIONADO", color = Color.White,
                        fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Text(if (transmitting) "Soltá para terminar" else "PARA HABLAR", color = Cyan,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(micMessage, color = Color.LightGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
                // Interacción visual inicial. La transmisión de audio se implementará en la siguiente etapa.
                Box(Modifier.size(280.dp).background(Color.Transparent, CircleShape)
                    .pointerInputPtt(
                        onPress = {
                            fingerDown = true
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                transmitting = capture.start()
                                micMessage = if (transmitting) "Micrófono activo · transmisión aún no conectada" else "No se pudo iniciar el micrófono"
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onRelease = {
                            fingerDown = false
                            capture.stop()
                            transmitting = false
                            micMessage = "Micrófono listo"
                        }
                    ))
            }
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavItem("Inicio", Icons.Default.Home, selected) { selected = it }
                NavItem("Grupos", Icons.Default.Groups, selected) { selected = it }
                NavItem("Canales", Icons.Default.Tune, selected) { selected = it }
                NavItem("Perfil", Icons.Default.Person, selected) { selected = it }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                    selected: String, onSelect: (String) -> Unit) {
    val active = selected == label
    Column(
        Modifier.weight(1f).border(1.dp, if (active) Cyan else Blue, RoundedCornerShape(16.dp))
            .background(if (active) Color(0xFF063B9C) else Color(0xCC020B1D), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = { onSelect(label) }) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
