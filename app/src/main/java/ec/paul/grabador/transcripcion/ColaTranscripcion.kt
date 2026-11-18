package ec.paul.grabador.transcripcion

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import ec.paul.grabador.datos.Biblioteca
import ec.paul.grabador.datos.Frase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime

data class EstadoTranscripcion(
    val actual: String? = null,
    val progreso: Float = 0f,
    val pendientes: List<String> = emptyList(),
    val bajandoModelo: Float? = null, // 0..1 mientras baja el modelo
    val esperandoCarga: Boolean = false,
    val error: String? = null,
) {
    val ocupada get() = actual != null || pendientes.isNotEmpty()
}

/**
 * Transcribe en el teléfono, de una en una, con whisper.cpp. Escribe `<nombre>.transcripcion.json`
 * (origen «telefono») en el mismo formato que pc/transcribir.py. Nunca pisa una de la PC (origen «pc»).
 */
class ColaTranscripcion(
    private val contexto: Context,
    private val biblioteca: Biblioteca,
    private val alcance: CoroutineScope,
) {
    private val tag = "ColaTranscripcion"
    val modelos = Modelos(contexto)
    val ajustes = AjustesTranscripcion(contexto)
    private val _estado = MutableStateFlow(EstadoTranscripcion())
    val estado: StateFlow<EstadoTranscripcion> = _estado
    private var trabajo: Job? = null
    /** Se llama al terminar cada una, para que la pantalla relea las transcripciones. */
    var alTerminar: (String) -> Unit = {}

    fun encolar(nombres: List<String>) {
        _estado.update { e ->
            val nuevos = nombres.filter { it != e.actual && it !in e.pendientes && biblioteca.transcripcion(it)?.precisa != true }
            e.copy(pendientes = e.pendientes + nuevos, error = null)
        }
        if (_estado.value.pendientes.isEmpty()) return
        ContextCompat.startForegroundService(contexto, Intent(contexto, TranscripcionService::class.java))
        if (trabajo?.isActive != true) trabajo = alcance.launch { procesar() }
    }

    fun cancelar() {
        Whisper.cancelarTodo()
        trabajo?.cancel()
        _estado.update { EstadoTranscripcion() }
    }

    private fun cargando(): Boolean {
        val i = contexto.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private suspend fun procesar() {
        val modelo = ajustes.modelo
        try {
            if (!modelos.disponible(modelo)) {
                _estado.update { it.copy(bajandoModelo = 0f) }
                modelos.descargar(modelo) { p -> _estado.update { it.copy(bajandoModelo = p) } }
                _estado.update { it.copy(bajandoModelo = null) }
            }
        } catch (e: Exception) {
            _estado.update { EstadoTranscripcion(error = "No se pudo bajar el modelo: ${e.message}") }
            return
        }
        val hilos = 4 // los 4 núcleos rápidos del Snapdragon 7 Gen 3; más no acelera y calienta
        Whisper.cargar(modelos.archivo(modelo)).use { w ->
            while (true) {
                val nombre = _estado.value.pendientes.firstOrNull() ?: break
                while (ajustes.soloCargando && !cargando()) {
                    _estado.update { it.copy(esperandoCarga = true) }
                    delay(30_000)
                }
                _estado.update { it.copy(actual = nombre, pendientes = it.pendientes.drop(1), progreso = 0f, esperandoCarga = false) }
                try {
                    transcribirUna(w, nombre, modelo, hilos)
                    alTerminar(nombre)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "fallo $nombre: ${e.message}")
                    _estado.update { it.copy(error = "No se pudo transcribir: ${e.message}") }
                }
            }
        }
        _estado.update { it.copy(actual = null, progreso = 0f) }
    }

    private fun transcribirUna(w: Whisper, nombre: String, modelo: Modelo, hilos: Int) {
        val audio = biblioteca.audio(nombre)
        val segundos = biblioteca.meta(nombre).segundos.coerceAtLeast(1)
        val trozo = 300 // 5 min por trozo
        val frases = ArrayList<Frase>()
        AudioPcm.porTrozos(audio, trozo) { inicioMs, pcm, _ ->
            if (pcm.size < AudioPcm.FRECUENCIA / 2) return@porTrozos // menos de medio segundo
            val hecho = inicioMs / 1000f
            val esteTrozo = pcm.size.toFloat() / AudioPcm.FRECUENCIA
            val parte = w.transcribir(pcm, hilos) { pct ->
                _estado.update { it.copy(progreso = ((hecho + esteTrozo * pct / 100f) / segundos).coerceIn(0f, 1f)) }
            }
            parte.forEach { frases += Frase(it.inicioMs + inicioMs, it.finMs + inicioMs, it.texto) }
        }
        guardar(nombre, modelo, frases, segundos)
    }

    private fun guardar(nombre: String, modelo: Modelo, frases: List<Frase>, segundos: Long) {
        // La de la PC manda: si llegó mientras tanto, no se pisa.
        if (biblioteca.transcripcion(nombre)?.precisa == true) return
        val segs = JSONArray()
        frases.forEach { f -> segs.put(JSONObject().put("i", f.inicioMs / 1000.0).put("f", f.finMs / 1000.0).put("t", f.texto)) }
        val doc = JSONObject()
            .put("version", 1).put("origen", "telefono").put("modelo", modelo.id).put("idioma", "es")
            .put("creado", OffsetDateTime.now().toString()).put("duracion", segundos).put("segmentos", segs)
        val destino = biblioteca.archivoTranscripcion(nombre)
        val tmp = File(destino.path + ".tmp")
        tmp.writeText(doc.toString())
        check(tmp.renameTo(destino)) { "no se pudo guardar la transcripción" }
    }
}
