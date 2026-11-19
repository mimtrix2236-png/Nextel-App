package com.nextelapp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura PCM del micrófono mientras el usuario mantiene pulsado el botón PTT.
 * En esta primera etapa los datos se capturan localmente y se descartan:
 * el transporte WebRTC hacia otro teléfono todavía no está conectado.
 */
class PttAudioCapture {
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    fun start(): Boolean {
        if (running.get()) return true
        val sampleRate = 48000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false

        return try {
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, 4096)
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                return false
            }
            audio.startRecording()
            recorder = audio
            running.set(true)
            worker = Thread {
                val buffer = ByteArray(maxOf(minBuffer, 2048))
                while (running.get()) {
                    val count = try {
                        audio.read(buffer, 0, buffer.size)
                    } catch (e: Exception) {
                        Log.e("NextelPTT", "Error leyendo micrófono", e)
                        -1
                    }
                    if (count < 0) running.set(false)
                }
            }.also { it.name = "NextelMicCapture"; it.start() }
            true
        } catch (e: SecurityException) {
            Log.e("NextelPTT", "Permiso de micrófono denegado", e)
            false
        } catch (e: Exception) {
            Log.e("NextelPTT", "No se pudo iniciar la captura", e)
            false
        }
    }

    fun stop() {
        running.set(false)
        try { worker?.join(500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
    }

    fun isRunning(): Boolean = running.get()
}
