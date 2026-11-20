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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    DisposableEffect(service) {
        if (service != null) {
            status = service.currentStatus
            service.statusListener = { message -> mainHandler.post { status = message } }
        }
        onDispose {
            if (service != null) service.statusListener = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && fingerDown) {
            transmitting = connection.startTalking()
            if (!transmitting) status = "No se pudo iniciar el audio. Verificá la conexión."
        } else if (!granted) status = "Permiso de micrófono denegado"
    }

    DisposableEffect(connection, service) {
        onDispose { if (service == null) connection.close() }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Navy, surface = Color(0xFF06142B), primary = Blue)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF001B52), Navy, Color.Black)))
        ) {
            val tiny = maxHeight < 620.dp
            val compact = maxHeight < 700.dp
            val outerPadding = if (tiny) 9.dp else 12.dp
            val micSize = if (tiny) 142.dp else if (compact) 174.dp else 204.dp
            val titleSize = if (tiny) 25.sp else 29.sp

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = outerPadding, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth().height(if (tiny) 57.dp else 65.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nextel App", fontSize = titleSize, fontWeight = FontWeight.Black, color = Color.White,
                            maxLines = 1)
                        Text("PUSH-TO-TALK · LAN", color = Red, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp, fontSize = if (tiny) 12.sp else 14.sp)
                        if (!tiny) Text("Transmisión local por Wi-Fi", color = Color.LightGray, fontSize = 11.sp)
                    }
                    IconButton(onClick = { selected = "Configuración" }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 38.dp)
                        .border(1.dp, Blue, RoundedCornerShape(18.dp))
                        .background(Color(0xCC020B1D), RoundedCornerShape(18.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (connection.isConnected()) "●" else "○",
                        color = if (connection.isConnected()) Green else Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(status, color = Color.White, fontSize = if (tiny) 10.sp else 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(6.dp))
                Text("SALA MULTIJUGADOR · RED LAN", color = Cyan,
                    fontWeight = FontWeight.Bold, fontSize = if (tiny) 11.sp else 12.sp)
                if (!tiny) Text("Varios teléfonos pueden unirse a la sala sin Internet.",
                    color = Color.LightGray, fontSize = 10.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(if (tiny) 39.dp else 43.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        hostIp = localWifiIp(context)
                        if (hostIp.isBlank()) status = "Conectá este teléfono a una red Wi-Fi primero."
                        else connection.host()
                    }, modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Crear sala", fontSize = 12.sp)
                    }
                    Button(onClick = {
                        connection.close()
                        hostIp = ""
                        status = "Desconectado"
                        transmitting = false
                    }, modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Desconectar", fontSize = 12.sp)
                    }
                }

                if (hostIp.isNotBlank()) {
                    Row(Modifier.fillMaxWidth().height(31.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("IP: $hostIp", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("IP local de Nextel", hostIp))
                            status = "IP copiada: $hostIp"
                        }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                            Text("Copiar", color = Cyan, fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = remoteIp, onValueChange = { remoteIp = it },
                    label = { Text("IP del teléfono que creó la sala", fontSize = 11.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().height(if (tiny) 53.dp else 57.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedLabelColor = Cyan, unfocusedLabelColor = Color.LightGray
                    )
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = {
                    val enteredIp = remoteIp.trim()
                    val thisPhoneIp = localWifiIp(context)
                    if (enteredIp.isBlank()) {
                        status = "Ingresá la IP del teléfono anfitrión."
                    } else if (thisPhoneIp.isNotBlank() && enteredIp == thisPhoneIp) {
                        status = "Esa es la IP de este teléfono. El otro teléfono debe ingresar esta IP para unirse."
                    } else {
                        hostIp = ""
                        connection.connect(enteredIp)
                        status = "Intentando conectar con $enteredIp…"
                    }
                }, modifier = Modifier.fillMaxWidth().height(if (tiny) 39.dp else 42.dp),
                    contentPadding = PaddingValues(vertical = 0.dp)) {
                    Text("Conectar a una sala", fontSize = 12.sp)
                }

                Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))
                Box(Modifier.size(micSize), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().border(4.dp,
                        Brush.linearGradient(listOf(Cyan, Blue)), CircleShape).padding(9.dp)
                        .border(3.dp, Color.LightGray, CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF0759D8), Color(0xFF020B24))), CircleShape))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎙", fontSize = if (tiny) 43.sp else 54.sp)
                        Text(if (transmitting) "TRANSMITIENDO" else "MANTENÉ PRESIONADO",
                            color = Color.White, fontWeight = FontWeight.Black,
                            fontSize = if (tiny) 10.sp else 12.sp, textAlign = TextAlign.Center)
                        Text(if (transmitting) "Soltá para terminar" else "PARA HABLAR",
                            color = Cyan, fontWeight = FontWeight.Bold, fontSize = if (tiny) 11.sp else 12.sp)
                        Text(if (connection.isConnected()) "Audio LAN conectado" else "Esperando conexión LAN",
                            color = Color.LightGray, fontSize = 9.sp)
                    }
                    Box(Modifier.fillMaxSize().background(Color.Transparent, CircleShape)
                        .pointerInput(connection.isConnected()) {
                            detectTapGestures(onPress = {
                                fingerDown = true
                                if (!connection.isConnected()) {
                                    status = "Primero creá o conectá una sala en la misma Wi-Fi."
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

                Spacer(Modifier.weight(1f, fill = true))
                if (!tiny) {
                    Text("Mantené presionado para hablar · soltá para escuchar.",
                        color = Color.LightGray, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                }
                Row(Modifier.fillMaxWidth().height(if (tiny) 49.dp else 54.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    NavItem("Inicio", Icons.Default.Home, selected) { selected = it }
                    NavItem("Grupos", Icons.Default.Groups, selected) { selected = it }
                    NavItem("Canales", Icons.Default.Tune, selected) { selected = it }
                    NavItem("Perfil", Icons.Default.Person, selected) { selected = it }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                    selected: String, onSelect: (String) -> Unit) {
    val active = selected == label
    Column(
        Modifier.weight(1f).fillMaxHeight().border(1.dp, if (active) Cyan else Blue, RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFF063B9C) else Color(0xCC020B1D), RoundedCornerShape(14.dp))
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onSelect(label) }, modifier = Modifier.size(30.dp)) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
