package ec.paul.grabador

import ec.paul.grabador.ble.Reanudacion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReanudacionTest {
    private val f = File.createTempFile("parcial", ".raw")

    @After fun limpiar() {
        f.delete()
        Reanudacion.UNIDAD = Reanudacion.Unidad.ENVIADO
        Reanudacion.ANUNCIA_LO_QUE_FALTA = false
    }

    @Test fun recortaAPaquetesCompletos() {
        f.writeBytes(ByteArray(82 * 10 + 37))
        assertEquals(820L, Reanudacion.recortar(f))
        assertEquals(820L, f.length())
    }

    @Test fun sinParcialEmpiezaDeCero() {
        f.delete()
        assertEquals(0L, Reanudacion.recortar(f))
        assertEquals(0L, Reanudacion.desplazamiento(0))
    }

    @Test fun desplazamientoEnLasDosUnidades() {
        Reanudacion.UNIDAD = Reanudacion.Unidad.ENVIADO
        assertEquals(820L, Reanudacion.desplazamiento(820))
        Reanudacion.UNIDAD = Reanudacion.Unidad.WAV
        // 10 paquetes de 20 ms = 0,2 s = 6400 B de PCM a 16 kHz/16 bit, más la cabecera WAV
        assertEquals(44L + 6400, Reanudacion.desplazamiento(820))
    }

    @Test fun totalEsperado() {
        assertEquals(434_026L, Reanudacion.totalEsperado(0, 434_026))
        Reanudacion.ANUNCIA_LO_QUE_FALTA = true
        assertEquals(434_026L, Reanudacion.totalEsperado(200_000, 234_026))
        Reanudacion.ANUNCIA_LO_QUE_FALTA = false
        assertEquals(434_026L, Reanudacion.totalEsperado(200_000, 434_026))
        Reanudacion.ANUNCIA_LO_QUE_FALTA = false // lo medido
    }

    @Test fun valoresMedidosPorDefecto() {
        assertEquals(Reanudacion.Unidad.ENVIADO, Reanudacion.UNIDAD)
        assertEquals(false, Reanudacion.ANUNCIA_LO_QUE_FALTA)
    }
}
