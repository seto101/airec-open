package ec.paul.grabador.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Forma de onda real: decodifica el Opus con MediaCodec y saca el RMS de [n] tramos (0..1).
 * Se guarda en `<nombre>.onda` para no repetirlo.
 */
object OndaAudio {
    fun obtener(audio: File, cache: File, n: Int = 72): FloatArray {
        if (cache.exists() && cache.lastModified() >= audio.lastModified()) {
            val b = ByteBuffer.wrap(cache.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            if (b.remaining() == n * 4) return FloatArray(n) { b.float }
        }
        val picos = calcular(audio, n)
        val out = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
        picos.forEach { out.putFloat(it) }
        cache.writeBytes(out.array())
        return picos
    }

    private fun calcular(audio: File, n: Int): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(audio.path)
        val fmt = ex.getTrackFormat(0)
        ex.selectTrack(0)
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 1L
        val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(fmt, null, null, 0)
        dec.start()
        val suma = DoubleArray(n)
        val cuenta = LongArray(n)
        val info = MediaCodec.BufferInfo()
        var finEntrada = false
        var finSalida = false
        while (!finSalida) {
            if (!finEntrada) {
                val i = dec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = dec.getInputBuffer(i)!!
                    val tam = ex.readSampleData(buf, 0)
                    if (tam < 0) {
                        dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        finEntrada = true
                    } else {
                        dec.queueInputBuffer(i, 0, tam, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = dec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val buf = dec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                val tramo = ((info.presentationTimeUs.toDouble() / durUs) * n).toInt().coerceIn(0, n - 1)
                val sh = buf.asShortBuffer()
                // Una de cada 8 muestras basta para el RMS y es 8 veces más rápido.
                var k = 0
                while (k < sh.remaining()) {
                    val v = sh.get(k) / 32768.0
                    suma[tramo] += v * v
                    cuenta[tramo]++
                    k += 8
                }
                dec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) finSalida = true
            }
        }
        dec.stop(); dec.release(); ex.release()
        val rms = FloatArray(n) { if (cuenta[it] > 0) sqrt(suma[it] / cuenta[it]).toFloat() else 0f }
        val max = rms.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return FloatArray(n) { sqrt(rms[it] / max) } // raíz: que lo bajo también se vea
    }
}
