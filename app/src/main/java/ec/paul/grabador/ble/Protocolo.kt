package ec.paul.grabador.ble

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Protocolo BLE del grabador AIREC (chip Actions ATS3085), medido contra el aparato el 2026-11-13.
 *
 * Ida:    55 AA <LEN> <CMD> <payload>   con LEN = 1 + len(payload)
 * Vuelta: AA 55 <LEN> <CMD> <payload>   (varias tramas pueden venir en una notificación)
 * Sin checksum. Enteros big-endian.
 */
object Protocolo {
    val SERVICIO: UUID = UUID.fromString("0011200a-2233-4455-6677-8899dfdedddc")
    val ESCRIBIR: UUID = UUID.fromString("0011202a-2233-4455-6677-8899dfdedddc")
    val RESPUESTAS: UUID = UUID.fromString("0011203a-2233-4455-6677-8899dfdedddc")
    val DATOS: UUID = UUID.fromString("0011204a-2233-4455-6677-8899dfdedddc")
    /** Audio en vivo. Hay que tener su notify activo para que el 07 mande datos por [DATOS]. */
    val VIVO: UUID = UUID.fromString("0011201a-2233-4455-6677-8899dfdedddc")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val NOMBRE_BLE = "AIREC"

    const val GRABAR = 0x03 // alterna, como el botón físico: mirar ESTADO antes
    const val DETENER = 0x04
    const val BORRAR = 0x0A // + nombre14 → AA 55 01 0A (OK) o FC (fallo); medido 2026-11-16
    const val GRABAR_AL_ENCENDER = 0x2E // + 00/01; sin respuesta, pero 00 hace que no grabe al encender (medido)
    const val FALLO_BORRAR = 0xFC
    const val TIPO_GRABACION = 0x21 // + 'R' (grabación normal), 100 ms después de GRABAR
    const val SN = 0x01
    const val HORA = 0x02
    const val LISTA = 0x05
    const val FIN_LISTA = 0x06
    const val DESCARGAR = 0x07
    const val PAUSAR = 0x08
    const val FIN_DESCARGA = 0x09
    const val ESPACIO = 0x0B
    const val ESPACIO_LIBRE = 0x0C
    const val ESPACIO_TOTAL = 0x0D
    const val BATERIA = 0x0E
    const val ESTADO = 0x0F
    const val VERSION = 0x12
    const val FALLO_DESCARGA = 0xFD
    const val FALLO_LISTA = 0xFE

    private val FORMATO_NOMBRE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun trama(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf(0x55, 0xAA.toByte(), (1 + payload.size).toByte(), cmd.toByte()) + payload

    fun ponerHora(ahora: LocalDateTime): ByteArray =
        trama(HORA, ahora.format(FORMATO_NOMBRE).toByteArray(Charsets.US_ASCII))

    /** 07: nombre de 14 dígitos + desplazamiento u32 BE (LEN fijo 0x13). */
    fun descargar(nombre: String, desplazamiento: Long = 0): ByteArray {
        require(nombre.length == 14 && nombre.all { it.isDigit() }) { "nombre inválido: $nombre" }
        return trama(DESCARGAR, nombre.toByteArray(Charsets.US_ASCII) + u32(desplazamiento))
    }

    fun u32(v: Long): ByteArray = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    fun leerU32(b: ByteArray, desde: Int): Long =
        ((b[desde].toLong() and 0xFF) shl 24) or ((b[desde + 1].toLong() and 0xFF) shl 16) or
            ((b[desde + 2].toLong() and 0xFF) shl 8) or (b[desde + 3].toLong() and 0xFF)

    fun fechaDeNombre(nombre: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(nombre, FORMATO_NOMBRE) }.getOrNull()
}

class Respuesta(val cmd: Int, val payload: ByteArray) {
    val texto: String get() = payload.toString(Charsets.US_ASCII).trimEnd('\u0000')
    override fun toString() = "Respuesta(0x%02X, %d B)".format(cmd, payload.size)
}

/** Parte notificaciones en tramas AA 55 LEN CMD payload. Una trama partida entre notificaciones se descarta (como la app). */
object LectorTramas {
    fun leer(notificacion: ByteArray): List<Respuesta> {
        val out = mutableListOf<Respuesta>()
        var i = 0
        while (i + 4 <= notificacion.size &&
            notificacion[i] == 0xAA.toByte() && notificacion[i + 1] == 0x55.toByte()
        ) {
            val len = notificacion[i + 2].toInt() and 0xFF
            val fin = i + 3 + len
            if (len < 1 || fin > notificacion.size) break
            out += Respuesta(notificacion[i + 3].toInt() and 0xFF, notificacion.copyOfRange(i + 4, fin))
            i = fin
        }
        return out
    }
}

/** Entrada de la lista (05): nombre + tamaño del WAV en el aparato (44 B + PCM 16 kHz 16 bit mono). */
data class ArchivoRemoto(val nombre: String, val tamanoWav: Long) {
    val segundos: Long get() = ((tamanoWav - 44).coerceAtLeast(0)) / 32_000
    val fecha: LocalDateTime? get() = Protocolo.fechaDeNombre(nombre)

    companion object {
        fun de(r: Respuesta): ArchivoRemoto? {
            if (r.cmd != Protocolo.LISTA || r.payload.size < 5) return null
            val n = r.payload.size - 4
            return ArchivoRemoto(
                r.payload.copyOfRange(0, n).toString(Charsets.US_ASCII).trimEnd('\u0000'),
                Protocolo.leerU32(r.payload, n),
            )
        }
    }
}
