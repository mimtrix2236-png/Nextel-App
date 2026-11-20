package com.nextelapp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sala PTT multipunto por LAN.
 * El anfitrión acepta varios clientes y retransmite paquetes de audio PCM
 * a los demás participantes. No requiere acceso a Internet.
 */
class LanPttConnection(private val onStatus: (String) -> Unit) {
    companion object {
        private const val PORT = 8988
        private const val RATE = 16000
        private const val TYPE_AUDIO = 1
        private const val TYPE_TEXT = 2
        private const val MAX_PACKET = 64 * 1024
        private const val MAX_PARTICIPANTS = 16
    }

    private class Peer(val socket: Socket) {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val writeLock = Any()
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var clientInput: DataInputStream? = null
    @Volatile private var clientOutput: DataOutputStream? = null
    private val clientWriteLock = Any()
    private val peers = CopyOnWriteArrayList<Peer>()
    private val connected = AtomicBoolean(false)
    @Volatile private var hosting = false
    private val sending = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var receiveTrack: AudioTrack? = null
    private var sendThread: Thread? = null

    @Synchronized fun host() {
        close()
        hosting = true
        Thread {
            try {
                val listeningServer = ServerSocket()
                listeningServer.reuseAddress = true
                listeningServer.bind(InetSocketAddress(PORT))
                server = listeningServer
                onStatus("Sala creada · IP local · esperando participantes")
                while (!listeningServer.isClosed) {
                    val accepted = listeningServer.accept()
                    if (peers.size >= MAX_PARTICIPANTS - 1) {
                        try { accepted.close() } catch (_: Exception) { }
                        onStatus("Sala completa · máximo $MAX_PARTICIPANTS participantes, incluido el anfitrión")
                        continue
                    }
                    try {
                        accepted.tcpNoDelay = true
                        val peer = Peer(accepted)
                        peers.add(peer)
                        updateHostStatus()
                        Thread { handlePeer(peer) }.also {
                            it.name = "NextelPeer-${accepted.inetAddress.hostAddress}"
                            it.start()
                        }
                    } catch (e: Exception) {
                        try { accepted.close() } catch (_: Exception) { }
                        Log.e("NextelLAN", "No se pudo aceptar participante", e)
                    }
                }
            } catch (e: Exception) {
                if (hosting) onStatus("No se pudo crear la sala: ${e.localizedMessage ?: "error de red"}")
                hosting = false
            }
        }.also { it.name = "NextelLanHost"; it.start() }
    }

    fun connect(ip: String) {
        if (hosting) {
            onStatus("Esta sala está creada en este teléfono. Otro teléfono debe ingresar esta IP.")
            return
        }
        Thread {
            try {
                close()
                onStatus("Conectando a $ip…")
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(ip.trim(), PORT), 8000)
                socket = s
                clientInput = DataInputStream(BufferedInputStream(s.getInputStream()))
                clientOutput = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                connected.set(true)
                onStatus("Conectado a la sala · audio PTT listo")
                receiveClientPackets()
            } catch (e: Exception) {
                connected.set(false)
                onStatus("No se pudo conectar: ${e.localizedMessage ?: "revisá la IP y la Wi-Fi"}")
            }
        }.also { it.name = "NextelLanConnect"; it.start() }
    }

    private fun handlePeer(peer: Peer) {
        try {
            while (hosting && !peer.socket.isClosed) {
                val type = peer.input.readUnsignedByte()
                val length = peer.input.readInt()
                if (length < 0 || length > MAX_PACKET) throw IllegalStateException("Paquete inválido")
                val payload = ByteArray(length)
                peer.input.readFully(payload)
                when (type) {
                    TYPE_AUDIO -> {
                        playAudio(payload)
                        broadcast(TYPE_AUDIO, payload, peer)
                    }
                    TYPE_TEXT -> {
                        val message = payload.toString(Charsets.UTF_8)
                        broadcast(TYPE_TEXT, payload, peer)
                        onStatus("Mensaje LAN de participante: $message")
                    }
                    else -> Log.w("NextelLAN", "Tipo de paquete desconocido: $type")
                }
            }
        } catch (_: Exception) {
            // El cierre de socket es la señal normal de salida de un participante.
        } finally {
            peers.remove(peer)
            try { peer.socket.close() } catch (_: Exception) { }
            if (hosting) updateHostStatus()
        }
    }

    private fun receiveClientPackets() {
        try {
            while (connected.get()) {
                val type = clientInput?.readUnsignedByte() ?: break
                val length = clientInput?.readInt() ?: break
                if (length < 0 || length > MAX_PACKET) throw IllegalStateException("Paquete inválido")
                val payload = ByteArray(length)
                clientInput?.readFully(payload)
                when (type) {
                    TYPE_AUDIO -> playAudio(payload)
                    TYPE_TEXT -> onStatus("Mensaje LAN: ${payload.toString(Charsets.UTF_8)}")
                }
            }
        } catch (e: Exception) {
            if (connected.get()) onStatus("Conexión interrumpida")
        } finally {
            connected.set(false)
            try { socket?.close() } catch (_: Exception) { }
            socket = null
        }
    }

    private fun broadcast(type: Int, payload: ByteArray, except: Peer? = null) {
        peers.forEach { peer ->
            if (peer !== except) {
                try {
                    synchronized(peer.writeLock) {
                        peer.output.writeByte(type)
                        peer.output.writeInt(payload.size)
                        peer.output.write(payload)
                        peer.output.flush()
                    }
                } catch (_: Exception) {
                    try { peer.socket.close() } catch (_: Exception) { }
                }
            }
        }
    }

    private fun sendClientPacket(type: Int, payload: ByteArray) {
        synchronized(clientWriteLock) {
            val out = clientOutput ?: return
            out.writeByte(type)
            out.writeInt(payload.size)
            out.write(payload)
            out.flush()
        }
    }

    fun sendMessage(message: String) {
        val bytes = message.trim().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return
        if (hosting) broadcast(TYPE_TEXT, bytes)
        else if (connected.get()) {
            try { sendClientPacket(TYPE_TEXT, bytes) }
            catch (e: Exception) { onStatus("No se pudo enviar el mensaje") }
        }
    }

    private fun ensurePlayback() {
        if (receiveTrack != null) return
        val min = AudioTrack.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        receiveTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().also { it.play() }
    }

    private fun playAudio(bytes: ByteArray) {
        try {
            ensurePlayback()
            receiveTrack?.write(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("NextelLAN", "Error reproduciendo audio LAN", e)
        }
    }

    fun startTalking(): Boolean {
        if (!isConnected() || !sending.compareAndSet(false, true)) return false
        return try {
            val min = AudioRecord.getMinBufferSize(
                RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min * 2, 8192)
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                sending.set(false)
                return false
            }
            recorder = audio
            audio.startRecording()
            sendThread = Thread {
                val bytes = ByteArray(2048)
                try {
                    while (sending.get() && isConnected()) {
                        val n = audio.read(bytes, 0, bytes.size)
                        if (n > 0) {
                            val packet = bytes.copyOf(n)
                            if (hosting) broadcast(TYPE_AUDIO, packet)
                            else sendClientPacket(TYPE_AUDIO, packet)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NextelLAN", "Error enviando audio", e)
                    onStatus("Se perdió la conexión de audio")
                }
            }.also { it.name = "NextelAudioSend"; it.start() }
            true
        } catch (e: SecurityException) {
            sending.set(false)
            onStatus("Falta permiso de micrófono")
            false
        } catch (e: Exception) {
            sending.set(false)
            onStatus("Error de micrófono: ${e.localizedMessage}")
            false
        }
    }

    fun stopTalking() {
        sending.set(false)
        try { recorder?.stop() } catch (_: Exception) { }
        try { sendThread?.join(400) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        sendThread = null
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
    }

    private fun updateHostStatus() {
        onStatus("Sala activa · ${peers.size + 1}/$MAX_PARTICIPANTS participantes (incluido anfitrión)")
    }

    fun participantCount(): Int = if (hosting) peers.size + 1 else if (connected.get()) 1 else 0

    fun isConnected(): Boolean = if (hosting) peers.isNotEmpty() else connected.get()

    fun close() {
        stopTalking()
        connected.set(false)
        hosting = false
        try { socket?.close() } catch (_: Exception) { }
        try { server?.close() } catch (_: Exception) { }
        peers.forEach { try { it.socket.close() } catch (_: Exception) { } }
        peers.clear()
        socket = null
        server = null
        clientInput = null
        clientOutput = null
        try { receiveTrack?.stop(); receiveTrack?.release() } catch (_: Exception) { }
        receiveTrack = null
    }
}
