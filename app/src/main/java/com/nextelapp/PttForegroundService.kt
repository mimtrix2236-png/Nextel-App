package com.nextelapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Mantiene viva la conexión PTT mientras la actividad no está visible.
 * El servicio se inicia desde la interfaz visible del usuario.
 */
class PttForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.nextelapp.action.START_PTT"
        const val ACTION_HOST = "com.nextelapp.action.HOST"
        const val ACTION_CONNECT = "com.nextelapp.action.CONNECT"
        const val ACTION_DISCONNECT = "com.nextelapp.action.DISCONNECT"
        const val EXTRA_IP = "host_ip"
        private const val CHANNEL_ID = "nextel_ptt_active"
        private const val NOTIFICATION_ID = 8988
    }

    inner class LocalBinder : Binder() {
        fun service(): PttForegroundService = this@PttForegroundService
    }

    private val binder = LocalBinder()
    @Volatile var currentStatus = "Servicio PTT activo"
        private set
    @Volatile var statusListener: ((String) -> Unit)? = null
    lateinit var connection: LanPttConnection
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        connection = LanPttConnection { message ->
            currentStatus = message
            statusListener?.invoke(message)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(message))
            sendBroadcast(Intent("com.nextelapp.PTT_STATUS").setPackage(packageName)
                .putExtra("status", message))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Listo para conectar"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Listo para conectar"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HOST -> connection.host()
            ACTION_CONNECT -> {
                val ip = intent.getStringExtra(EXTRA_IP).orEmpty()
                if (ip.isNotBlank()) connection.connect(ip)
            }
            ACTION_DISCONNECT -> connection.close()
            ACTION_START, null -> Unit
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        connection.close()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Nextel: conexión activa",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indica que Nextel mantiene activa la conexión de walkie-talkie."
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Nextel App · Walkie-talkie")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
