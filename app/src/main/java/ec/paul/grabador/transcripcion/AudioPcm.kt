package ec.paul.grabador.transcripcion

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Decodifica un audio (el .opus de la app) a float 16 kHz mono y lo entrega por trozos de [segundos],
 * para no tener entera en memoria una reunión de horas.
 */
object AudioPcm {
    const val FRECUENCIA = 16_000

    fun porTrozos(audio: File, segundos: Int, alTrozo: (inicioMs: Long, pcm: FloatArray, fin: Boolean) -> Unit) {
        val tam = FRECUENCIA * segundos
        var buf = FloatArray(tam)
        var n = 0
        var inicioMs = 0L
        decodificar(audio) { muestra ->
            buf[n++] = muestra
            if (n == tam) {
                alTrozo(inicioMs, buf, false)
                inicioMs += segundos * 1000L
                buf = FloatArray(tam)
                n = 0
            }
        }
        alTrozo(inicioMs, buf.copyOf(n), true)
    }

    /** Mono y remuestreo lineal a 16 kHz sobre la marcha (el decodificador Opus de Android da 48 kHz). */
    private fun decodificar(audio: File, emitir: (Float) -> Unit) {
        val ex = MediaExtractor()
        ex.setDataSource(audio.path)
        val fmt = ex.getTrackFormat(0)
        ex.selectTrack(0)
        val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(fmt, null, null, 0)
        dec.start()
        var canales = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var origen = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var paso = origen.toDouble() / FRECUENCIA
        var siguiente = 0.0 // posición (en muestras de origen) de la próxima muestra de salida
        var indice = 0L // muestras de origen ya vistas
        var previa = 0f
        val info = MediaCodec.BufferInfo()
        var finEntrada = false
        var finSalida = false
        try {
            while (!finSalida) {
                if (!finEntrada) {
                    val i = dec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val b = dec.getInputBuffer(i)!!
                        val t = ex.readSampleData(b, 0)
                        if (t < 0) {
                            dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); finEntrada = true
                        } else {
                            dec.queueInputBuffer(i, 0, t, ex.sampleTime, 0); ex.advance()
                        }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat
                    canales = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    origen = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    paso = origen.toDouble() / FRECUENCIA
                } else if (o >= 0) {
                    val sh = dec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    var k = 0
                    while (k + canales <= sh.remaining()) {
                        var suma = 0f
                        for (c in 0 until canales) suma += sh.get(k + c)
                        val actual = suma / canales / 32768f
                        // Muestras de salida que caen entre la muestra anterior y esta.
                        while (siguiente <= indice) {
                            val frac = (siguiente - (indice - 1)).toFloat().coerceIn(0f, 1f)
                            emitir(previa + (actual - previa) * frac)
                            siguiente += paso
                        }
                        previa = actual
                        indice++
                        k += canales
                    }
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) finSalida = true
                }
            }
        } finally {
            dec.stop(); dec.release(); ex.release()
        }
    }
}
