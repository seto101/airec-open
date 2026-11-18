package ec.paul.grabador

import ec.paul.grabador.audio.FlujoAirec
import ec.paul.grabador.audio.OggOpusWriter
import ec.paul.grabador.ble.ArchivoRemoto
import ec.paul.grabador.ble.LectorTramas
import ec.paul.grabador.ble.Protocolo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Bytes medidos contra el grabador real el 2026-11-13 (ver docs/PROTOCOL.md). */
class ProtocoloTest {
    private fun hex(s: String) = s.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test fun tramasDeIda() {
        assertArrayEquals(hex("55 aa 01 05"), Protocolo.trama(Protocolo.LISTA))
        assertArrayEquals(
            hex("55 aa 13 07 32 30 32 34 31 32 32 35 30 32 35 37 33 32 00 00 00 00"),
            Protocolo.descargar("20241225025732"),
        )
        assertArrayEquals(
            hex("55 aa 0f 02 32 30 32 36 31 31 31 33 31 34 34 34 35 32"),
            Protocolo.ponerHora(LocalDateTime.of(2026, 11, 13, 14, 44, 52)),
        )
    }

    @Test fun respuestasMedidas() {
        val sn = LectorTramas.leer(hex("aa 55 10 01 32 30 34 34 30 31 32 35 30 34 30 30 31 32 38")).single()
        assertEquals("204401250400128", sn.texto)

        // 0C y 0D llegan en notificaciones separadas, pero el lector acepta varias por notificación
        val dos = LectorTramas.leer(hex("aa 55 05 0c 00 00 e3 3c aa 55 05 0d 00 00 e8 e0"))
        assertEquals(listOf(0x0C, 0x0D), dos.map { it.cmd })
        assertEquals(58_172L, Protocolo.leerU32(dos[0].payload, 0))

        val bat = LectorTramas.leer(hex("aa 55 02 0e 30")).single()
        assertEquals(48, bat.payload[0].toInt())

        val f = ArchivoRemoto.de(
            LectorTramas.leer(hex("aa 55 13 05 32 30 32 34 31 32 32 35 30 32 35 37 33 32 00 33 b2 2c")).single(),
        )!!
        assertEquals("20241225025732", f.nombre)
        assertEquals(3_387_948L, f.tamanoWav)
        assertEquals(105L, f.segundos)
        assertEquals(LocalDateTime.of(2024, 12, 25, 2, 57, 32), f.fecha)
    }

    @Test fun tramaPartidaSeDescarta() {
        assertEquals(0, LectorTramas.leer(hex("aa 55 13 05 32 30")).size)
    }

    @Test fun crcOggDeReferencia() {
        // CRC de "OggS" con el polinomio de Ogg (0x04C11DB7, sin reflejar, init 0)
        assertEquals(0x5FB0A94F, OggOpusWriter.crcOgg("OggS".toByteArray()))
    }

    /** Usa una descarga real (ruta en AIREC_MUESTRA) si existe; si no, se salta. */
    @Test fun descargaRealAOgg() {
        val ruta = System.getenv("AIREC_MUESTRA")
        assumeTrue(ruta != null)
        val crudo = File(ruta!!)
        assumeTrue(crudo.exists())
        val ps = FlujoAirec.paquetes(crudo.readBytes())
        assertTrue(ps.isNotEmpty())
        val salida = File("build/test-salida/${crudo.nameWithoutExtension}.opus").apply { parentFile.mkdirs() }
        salida.outputStream().use { assertEquals(ps.size, OggOpusWriter.desdeFlujo(crudo.readBytes(), it)) }
    }
}
