package ec.paul.grabador.datos

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lo que se sabe de una grabación guardada en el teléfono (`<nombre>.json` junto a `<nombre>.opus`). */
data class Meta(
    val segundos: Long = 0,
    val momentos: List<Long> = emptyList(), // milisegundos
    val notas: String = "",
    val titulo: String = "",
) {
    fun json(): String = JSONObject()
        .put("segundos", segundos)
        .put("momentos", JSONArray(momentos))
        .put("notas", notas)
        .put("titulo", titulo)
        .toString()

    companion object {
        fun de(texto: String): Meta = runCatching {
            val o = JSONObject(texto)
            val m = o.optJSONArray("momentos")
            Meta(
                segundos = o.optLong("segundos"),
                momentos = (0 until (m?.length() ?: 0)).map { m!!.getLong(it) },
                notas = o.optString("notas"),
                titulo = o.optString("titulo"),
            )
        }.getOrDefault(Meta())
    }
}

data class Frase(val inicioMs: Long, val finMs: Long, val texto: String)

/** `<nombre>.transcripcion.json`, escrito por pc/transcribir.py (origen «pc») o, más adelante, por el teléfono. */
data class Transcripcion(val origen: String, val modelo: String, val frases: List<Frase>) {
    val precisa: Boolean get() = origen == "pc" || modelo.contains("turbo")

    fun texto(conTiempos: Boolean): String = frases.joinToString("\n") { f ->
        if (conTiempos) "[${hms(f.inicioMs / 1000)}] ${f.texto}" else f.texto
    }

    companion object {
        private fun hms(s: Long) = "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)

        fun de(texto: String): Transcripcion? = runCatching {
            val o = JSONObject(texto)
            val segs = o.getJSONArray("segmentos")
            Transcripcion(
                origen = o.optString("origen", "pc"),
                modelo = o.optString("modelo"),
                frases = (0 until segs.length()).map { i ->
                    val s = segs.getJSONObject(i)
                    Frase((s.getDouble("i") * 1000).toLong(), (s.getDouble("f") * 1000).toLong(), s.getString("t"))
                },
            )
        }.getOrNull()
    }
}

class Biblioteca(val carpeta: File) {
    fun audio(nombre: String) = File(carpeta, "$nombre.opus")
    fun archivoTranscripcion(nombre: String) = File(carpeta, "$nombre.transcripcion.json")

    fun transcripcion(nombre: String): Transcripcion? =
        archivoTranscripcion(nombre).takeIf { it.exists() }?.let { Transcripcion.de(it.readText()) }
    private fun archivoMeta(nombre: String) = File(carpeta, "$nombre.json")
    fun archivoOnda(nombre: String) = File(carpeta, "$nombre.onda")

    fun nombres(): List<String> =
        carpeta.listFiles { f -> f.name.endsWith(".opus") }?.map { it.nameWithoutExtension }?.sortedDescending().orEmpty()

    fun meta(nombre: String): Meta {
        val f = archivoMeta(nombre)
        val m = if (f.exists()) Meta.de(f.readText()) else Meta()
        // Duración: si no se guardó al bajar, la calcula del Ogg (granule de la última página / 48 000).
        return if (m.segundos == 0L && audio(nombre).exists()) m.copy(segundos = segundosOgg(audio(nombre))) else m
    }

    fun guardar(nombre: String, m: Meta) {
        val tmp = File(carpeta, "$nombre.json.tmp")
        tmp.writeText(m.json())
        tmp.renameTo(archivoMeta(nombre))
    }

    fun actualizar(nombre: String, cambio: (Meta) -> Meta) = guardar(nombre, cambio(meta(nombre)))

    companion object {
        /** Granule position (muestras a 48 kHz) de la última página Ogg. */
        fun segundosOgg(f: File): Long = runCatching {
            java.io.RandomAccessFile(f, "r").use { raf ->
                val leer = minOf(raf.length(), 65_536L).toInt()
                val b = ByteArray(leer)
                raf.seek(raf.length() - leer)
                raf.readFully(b)
                var i = leer - 27
                while (i >= 0) {
                    if (b[i] == 'O'.code.toByte() && b[i + 1] == 'g'.code.toByte() && b[i + 2] == 'g'.code.toByte() && b[i + 3] == 'S'.code.toByte()) {
                        var g = 0L
                        for (k in 7 downTo 0) g = (g shl 8) or (b[i + 6 + k].toLong() and 0xFF)
                        return@use g / 48_000
                    }
                    i--
                }
                0L
            }
        }.getOrDefault(0L)
    }
}
