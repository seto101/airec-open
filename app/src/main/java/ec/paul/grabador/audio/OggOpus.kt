package ec.paul.grabador.audio

import java.io.OutputStream

/**
 * Lo que manda el grabador al descargar: paquetes Opus de 80 B precedidos por 5B 50
 * (16 kHz, mono, 20 ms). Se meten tal cual en un Ogg Opus estándar (RFC 7845), sin decodificar.
 */
object FlujoAirec {
    private const val M0 = 0x5B.toByte()
    private const val M1 = 0x50.toByte()
    const val TAM_PAQUETE = 80

    /** Strict80: marca + 80 B fijos; si se pierde la alineación, busca la siguiente marca. */
    fun paquetes(d: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>(d.size / (TAM_PAQUETE + 2))
        var i = buscar(d, 0)
        while (i >= 0 && i + 2 + TAM_PAQUETE <= d.size) {
            out += d.copyOfRange(i + 2, i + 2 + TAM_PAQUETE)
            val j = i + 2 + TAM_PAQUETE
            i = if (j + 1 < d.size && d[j] == M0 && d[j + 1] == M1) j else buscar(d, j)
        }
        return out
    }

    private fun buscar(d: ByteArray, desde: Int): Int {
        var i = desde
        while (i + 1 < d.size) {
            if (d[i] == M0 && d[i + 1] == M1) return i
            i++
        }
        return -1
    }
}

class OggOpusWriter(
    private val out: OutputStream,
    private val frecuenciaOriginal: Int = 16_000,
    private val muestras48kPorPaquete: Int = 960, // 20 ms
    private val paquetesPorPagina: Int = 50,
) {
    private val serie = 0x41495245 // "AIRE"
    private var secuencia = 0
    private var granulo = 0L
    private val pendientes = ArrayList<ByteArray>()

    init {
        val head = java.io.ByteArrayOutputStream().apply {
            write("OpusHead".toByteArray())
            write(1) // versión
            write(1) // canales
            le16(0) // pre-skip
            le32(frecuenciaOriginal.toLong())
            le16(0) // ganancia
            write(0) // mapeo
        }.toByteArray()
        pagina(listOf(head), granulo = 0, tipo = 0x02)
        val vendor = "grabador-airec".toByteArray()
        val tags = java.io.ByteArrayOutputStream().apply {
            write("OpusTags".toByteArray()); le32(vendor.size.toLong()); write(vendor); le32(0)
        }.toByteArray()
        pagina(listOf(tags), granulo = 0, tipo = 0)
    }

    fun escribir(paquete: ByteArray) {
        pendientes += paquete
        if (pendientes.size >= paquetesPorPagina) volcar(false)
    }

    fun cerrar() {
        volcar(true)
        out.flush()
    }

    private fun volcar(ultima: Boolean) {
        if (pendientes.isEmpty() && !ultima) return
        granulo += pendientes.size.toLong() * muestras48kPorPaquete
        pagina(pendientes.toList(), granulo, if (ultima) 0x04 else 0)
        pendientes.clear()
    }

    private fun pagina(paquetes: List<ByteArray>, granulo: Long, tipo: Int) {
        val lacing = java.io.ByteArrayOutputStream()
        for (p in paquetes) {
            var n = p.size
            while (n >= 255) { lacing.write(255); n -= 255 }
            lacing.write(n)
        }
        val seg = lacing.toByteArray()
        require(seg.size <= 255) { "demasiados segmentos en una página" }
        val b = java.io.ByteArrayOutputStream()
        b.write("OggS".toByteArray()); b.write(0); b.write(tipo)
        for (k in 0 until 8) b.write(((granulo shr (8 * k)) and 0xFF).toInt())
        for (k in 0 until 4) b.write((serie shr (8 * k)) and 0xFF)
        for (k in 0 until 4) b.write((secuencia shr (8 * k)) and 0xFF)
        b.write(byteArrayOf(0, 0, 0, 0)) // CRC, se rellena después
        b.write(seg.size); b.write(seg)
        paquetes.forEach { b.write(it) }
        val pag = b.toByteArray()
        val crc = crcOgg(pag)
        for (k in 0 until 4) pag[22 + k] = ((crc shr (8 * k)) and 0xFF).toByte()
        out.write(pag)
        secuencia++
    }

    private fun java.io.ByteArrayOutputStream.le16(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
    private fun java.io.ByteArrayOutputStream.le32(v: Long) { for (k in 0 until 4) write(((v shr (8 * k)) and 0xFF).toInt()) }

    companion object {
        private val TABLA = IntArray(256) { i ->
            var r = i shl 24
            repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
            r
        }

        fun crcOgg(d: ByteArray): Int {
            var crc = 0
            for (x in d) crc = (crc shl 8) xor TABLA[((crc ushr 24) xor (x.toInt() and 0xFF)) and 0xFF]
            return crc
        }

        /** Convierte el flujo crudo descargado en un .opus. Devuelve cuántos paquetes escribió. */
        fun desdeFlujo(crudo: ByteArray, out: OutputStream): Int {
            val w = OggOpusWriter(out)
            val ps = FlujoAirec.paquetes(crudo)
            ps.forEach(w::escribir)
            w.cerrar()
            return ps.size
        }
    }
}
