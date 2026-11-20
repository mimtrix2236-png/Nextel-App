package com.nextelapp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Audio PTT local por Wi-Fi: un teléfono aloja la sala y el otro se conecta
 * usando la IP local del anfitrión. PCM mono 16 kHz / 16 bits.
 */
class LanPttConnection(private val onStatus: (String) -> Unit) {
    companion object {
        private const val PORT = 8988
        private const val RATE = 16000
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var output: BufferedOutputStream? = null
    @Volatile private var input: BufferedInputStream? = null
    @Volatile private var receiveTrack: AudioTrack? = null
    private val connected = AtomicBoolean(false)
    private val sending = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    private var sendThread: Thread? = null
    private var receiveThread: Thread? = null

    @Synchronized fun host() {
        close()
        Thread {
            try {
                val listeningServer = ServerSocket(PORT)
                server = listeningServer
                onStatus("Sala creada correctamente · esperando al otro teléfono")
                val accepted = listeningServer.accept()
                attach(accepted)
            } catch (e: Exception) {
                if (server?.isClosed != true) {
                    onStatus("No se pudo crear la sala: ${e.localizedMessage ?: "error de red"}")
                }
            }
        }.also { it.name = "NextelLanHost"; it.start() }
    }

    fun connect(ip: String) {
        Thread {
            try {
                close()
                onStatus("Conectando a $ip…")
                val s = Socket()
                s.connect(java.net.InetSocketAddress(ip.trim(), PORT), 8000)
                attach(s)
            } catch (e: Exception) {
                onStatus("No se pudo conectar: ${e.localizedMessage ?: "revisá la IP y la Wi-Fi"}")
            }
        }.also { it.name = "NextelLanConnect"; it.start() }
    }

    @Synchronized private fun attach(s: Socket) {
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
        connected.set(true)
        onStatus("Segundo teléfono conectado · PTT listo")
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        receiveTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }

        receiveThread = Thread {
            val buf = ByteArray(2048)
            try {
                while (connected.get()) {
                    val n = input?.read(buf) ?: -1
                    if (n <= 0) break
                    receiveTrack?.write(buf, 0, n)
                }
            } catch (_: Exception) { }
            if (connected.get()) onStatus("Conexión interrumpida")
            connected.set(false)
        }.also { it.name = "NextelAudioReceive"; it.start() }
    }

    fun startTalking(): Boolean {
        if (!connected.get() || !sending.compareAndSet(false, true)) return false
        return try {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 4096))
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release(); sending.set(false); return false
            }
            recorder = audio
            audio.startRecording()
            sendThread = Thread {
                val bytes = ByteArray(2048)
                try {
                    while (sending.get() && connected.get()) {
                        val n = audio.read(bytes, 0, bytes.size)
                        if (n > 0) synchronized(this) {
                            output?.write(bytes, 0, n)
                            output?.flush()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NextelLAN", "Error enviando audio", e)
                    onStatus("Se perdió la conexión de audio")
                }
            }.also { it.name = "NextelAudioSend"; it.start() }
            true
        } catch (e: SecurityException) {
            sending.set(false); onStatus("Falta permiso de micrófono"); false
        } catch (e: Exception) {
            sending.set(false); onStatus("Error de micrófono: ${e.localizedMessage}"); false
        }
    }

    fun stopTalking() {
        sending.set(false)
        try { recorder?.stop() } catch (_: Exception) { }
        try { sendThread?.join(300) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        sendThread = null
        recorder?.release()
        recorder = null
    }

    private fun closeSocketOnly() {
        connected.set(false)
        try { socket?.close() } catch (_: Exception) { }
        try { server?.close() } catch (_: Exception) { }
        socket = null; server = null; input = null; output = null
    }

    fun close() {
        stopTalking()
        closeSocketOnly()
        try { receiveTrack?.stop(); receiveTrack?.release() } catch (_: Exception) { }
        receiveTrack = null
    }

    fun isConnected() = connected.get()
}
