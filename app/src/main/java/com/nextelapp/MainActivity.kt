package com.nextelapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
    private val pttServiceState = mutableStateOf<PttForegroundService?>(null)
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            pttServiceState.value = (binder as? PttForegroundService.LocalBinder)?.service()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            pttServiceState.value = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NextelHome(pttServiceState) }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, PttForegroundService::class.java).apply {
            action = PttForegroundService.ACTION_START
        }
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        super.onStop()
    }
}

@Suppress("DEPRECATION")
private fun localWifiIp(context: Context): String {
    return try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val value = wifi.connectionInfo.ipAddress
        if (value == 0) "" else listOf(0, 8, 16, 24).joinToString(".") { shift ->
            ((value shr shift) and 0xff).toString()
        }
    } catch (_: Exception) { "" }
}

@Composable
private fun NextelHome(serviceState: androidx.compose.runtime.State<PttForegroundService?>) {
    var selected by remember { mutableStateOf("Inicio") }
    var transmitting by remember { mutableStateOf(false) }
    var fingerDown by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Sin conexión · solo red Wi-Fi local") }
    var hostIp by remember { mutableStateOf("") }
    var remoteIp by remember { mutableStateOf("") }
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val service = serviceState.value
    val connection = service?.connection ?: remember {
        LanPttConnection { message -> mainHandler.post { status = message } }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && fingerDown) {
            transmitting = connection.startTalking()
            if (!transmitting) status = "No se pudo iniciar el audio. Verificá la conexión."
        } else if (!granted) status = "Permiso de micrófono denegado"
    }
    DisposableEffect(Unit) {
        onDispose { if (service == null) connection.close() }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Navy, surface = Color(0xFF06142B), primary = Blue)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF001B52), Navy, Color.Black)))
                .verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nextel App", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("PUSH-TO-TALK · LAN", color = Red, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Transmisión local por Wi-Fi", color = Color.LightGray, fontSize = 12.sp)
                }
                IconButton(onClick = { selected = "Configuración" }) {
                    Icon(Icons.Default.Settings, contentDescription = "Configuración", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().border(1.dp, Blue, RoundedCornerShape(20.dp))
                    .background(Color(0xCC020B1D), RoundedCornerShape(20.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (connection.isConnected()) "●" else "○", color = if (connection.isConnected()) Green else Color.LightGray)
                Spacer(Modifier.width(8.dp))
                Text(status, color = Color.White, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Text("CONEXIÓN ENTRE DOS TELÉFONOS", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Los dos teléfonos deben estar conectados a la misma Wi-Fi.", color = Color.LightGray, fontSize = 12.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    hostIp = localWifiIp(context)
                    if (hostIp.isBlank()) status = "Conectá este teléfono a una red Wi-Fi primero."
                    else { status = "Abriendo sala en $hostIp"; connection.host() }
                }, modifier = Modifier.weight(1f)) { Text("Crear sala") }
                Button(onClick = { connection.close(); status = "Desconectado"; transmitting = false },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Text("Desconectar")
                }
            }
            if (hostIp.isNotBlank()) {
                Text("IP de este teléfono: $hostIp", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("IP local de Nextel", hostIp))
                    status = "IP copiada: $hostIp"
                }) { Text("Copiar IP", color = Cyan) }
            }
            OutlinedTextField(
                value = remoteIp, onValueChange = { remoteIp = it },
                label = { Text("IP del teléfono que creó la sala") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Cyan, unfocusedLabelColor = Color.LightGray
                )
            )
            Button(onClick = {
                if (remoteIp.isBlank()) status = "Ingresá la IP del teléfono anfitrión."
                else { hostIp = ""; connection.connect(remoteIp); status = "Intentando conectar con $remoteIp…" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Conectar a una sala") }

            Spacer(Modifier.height(18.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(250.dp).border(5.dp, Brush.linearGradient(listOf(Cyan, Blue)), CircleShape)
                    .padding(12.dp).border(3.dp, Color.LightGray, CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF0759D8), Color(0xFF020B24))), CircleShape))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎙", fontSize = 68.sp)
                    Text(if (transmitting) "TRANSMITIENDO" else "MANTENÉ PRESIONADO",
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text(if (transmitting) "Soltá para terminar" else "PARA HABLAR",
                        color = Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(if (connection.isConnected()) "Audio LAN conectado" else "Esperando conexión LAN",
                        color = Color.LightGray, fontSize = 10.sp)
                }
                Box(Modifier.size(250.dp).background(Color.Transparent, CircleShape)
                    .pointerInput(connection.isConnected()) {
                        androidx.compose.foundation.gestures.detectTapGestures(onPress = {
                            fingerDown = true
                            if (!connection.isConnected()) {
                                status = "Primero conectá los dos teléfonos a la misma Wi-Fi y uní la sala."
                            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                transmitting = connection.startTalking()
                                if (!transmitting) status = "No se pudo iniciar el micrófono."
                            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            tryAwaitRelease()
                            fingerDown = false
                            connection.stopTalking()
                            transmitting = false
                        })
                    })
            }
            Spacer(Modifier.height(16.dp))
            Text("Prueba: en el teléfono 1 tocá Crear sala. En el teléfono 2 escribí la IP mostrada y tocá Conectar. Después mantené presionado el botón para hablar.",
                color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
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
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = { onSelect(label) }) { Icon(icon, contentDescription = label, tint = Color.White) }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
