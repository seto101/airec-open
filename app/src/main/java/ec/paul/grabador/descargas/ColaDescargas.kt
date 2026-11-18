package ec.paul.grabador.descargas

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import ec.paul.grabador.audio.OggOpusWriter
import ec.paul.grabador.ble.Grabador
import ec.paul.grabador.datos.Biblioteca
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class EstadoCola(
    val actual: String? = null,
    val recibidos: Long = 0,
    val total: Long = 0,
    val velocidad: Long = 0, // bytes por segundo
    val pendientes: List<String> = emptyList(),
    val hechos: Set<String> = emptySet(),
    val error: String? = null,
) {
    val ocupada get() = actual != null || pendientes.isNotEmpty()
    val progreso: Float get() = if (total > 0) (recibidos.toFloat() / total).coerceIn(0f, 1f) else 0f
    val segundosRestantes: Long? get() = if (velocidad > 0 && total > recibidos) (total - recibidos) / velocidad else null
}

/**
 * Baja de una en una, en el alcance de la app (sigue aunque se cierre la pantalla; el servicio
 * mantiene vivo el proceso). Lo recibido se escribe en `<nombre>.raw.parcial`, así que un corte
 * se reanuda desde donde quedó. Al terminar se convierte a `<nombre>.opus` y se borra el crudo.
 */
class ColaDescargas(
    private val contexto: Context,
    private val grabador: Grabador,
    private val biblioteca: Biblioteca,
    private val alcance: CoroutineScope,
    private val alBajar: (String) -> Unit = {},
) {
    private val tag = "ColaDescargas"
    private val _estado = MutableStateFlow(EstadoCola(hechos = biblioteca.nombres().toSet()))
    val estado: StateFlow<EstadoCola> = _estado
    private var trabajo: Job? = null

    private fun parcial(nombre: String) = File(biblioteca.carpeta, "$nombre.raw.parcial")

    fun encolar(nombres: List<String>) {
        _estado.update { e ->
            val nuevos = nombres.filter { it !in e.hechos && it != e.actual && it !in e.pendientes }
            e.copy(pendientes = e.pendientes + nuevos, error = null)
        }
        if (_estado.value.pendientes.isEmpty()) return
        ContextCompat.startForegroundService(contexto, Intent(contexto, DescargaService::class.java))
        if (trabajo?.isActive != true) trabajo = alcance.launch { procesar() }
    }

    fun cancelar() {
        trabajo?.cancel()
        _estado.update { it.copy(actual = null, pendientes = emptyList(), recibidos = 0, total = 0, velocidad = 0) }
    }

    fun refrescar() = _estado.update { it.copy(hechos = biblioteca.nombres().toSet()) }

    private suspend fun procesar() {
        while (true) {
            val nombre = _estado.value.pendientes.firstOrNull() ?: break
            _estado.update { it.copy(actual = nombre, pendientes = it.pendientes.drop(1), recibidos = 0, total = 0, velocidad = 0) }
            var intento = 0
            while (true) {
                try {
                    bajarUno(nombre)
                    _estado.update { it.copy(hechos = it.hechos + nombre) }
                    alBajar(nombre)
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    intento++
                    Log.w(tag, "fallo $nombre (intento $intento): ${e.message}")
                    if (intento >= 4) {
                        _estado.update { it.copy(error = "No se pudo bajar $nombre: ${e.message}") }
                        break
                    }
                    delay(3_000L * intento)
                    runCatching { if (!grabador.estaConectado()) grabador.conectar() }
                }
            }
        }
        _estado.update { it.copy(actual = null, recibidos = 0, total = 0, velocidad = 0) }
    }

    private suspend fun bajarUno(nombre: String) {
        if (!grabador.estaConectado()) grabador.conectar()
        val p = parcial(nombre)
        val t0 = System.currentTimeMillis()
        var inicial = -1L
        var ultimo = 0L
        grabador.descargar(nombre, p) { hecho, total ->
            if (inicial < 0) inicial = hecho
            val ahora = System.currentTimeMillis()
            if (ahora - ultimo < 250 && hecho < total) return@descargar // ~240 avisos/s: no repintar tanto
            ultimo = ahora
            val seg = (System.currentTimeMillis() - t0) / 1000.0
            val vel = if (seg > 1) ((hecho - inicial) / seg).toLong() else 0L
            _estado.update { it.copy(recibidos = hecho, total = total, velocidad = vel) }
        }
        val destino = biblioteca.audio(nombre)
        val tmp = File(destino.path + ".tmp")
        val paquetes = tmp.outputStream().use { OggOpusWriter.desdeFlujo(p.readBytes(), it) }
        check(tmp.renameTo(destino)) { "no se pudo guardar ${destino.name}" }
        biblioteca.actualizar(nombre) { it.copy(segundos = paquetes * 20L / 1000) }
        p.delete()
    }
}
