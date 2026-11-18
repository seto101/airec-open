package ec.paul.grabador.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ec.paul.grabador.GrabadorApp
import ec.paul.grabador.ble.ArchivoRemoto
import ec.paul.grabador.ble.BluetoothApagado
import ec.paul.grabador.ble.EstadoGrabador
import ec.paul.grabador.ble.Protocolo
import ec.paul.grabador.datos.Meta
import ec.paul.grabador.datos.Transcripcion
import ec.paul.grabador.descargas.EstadoCola
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime

enum class Estado { NUEVO, EN_COLA, BAJANDO, TELEFONO, TRANSCRIBIENDO, TRANSCRITA }

/** Una grabación tal como se muestra: junta lo que dice el grabador y lo que hay en el teléfono. */
data class Grabacion(
    val nombre: String,
    val fecha: LocalDateTime?,
    val segundos: Long,
    val bytesEnvio: Long, // tamaño aproximado de la descarga (Opus a 4 KB/s)
    val estado: Estado,
    val enGrabador: Boolean,
    val meta: Meta,
    val transcripcion: Transcripcion? = null,
) {
    val titulo: String get() = meta.titulo.ifBlank { "Grabación" }
}

data class UiEstado(
    val conectando: Boolean = false,
    val conectado: Boolean = false,
    val aparato: EstadoGrabador? = null,
    val remotos: List<ArchivoRemoto> = emptyList(),
    val locales: Map<String, Meta> = emptyMap(),
    val transcripciones: Map<String, Transcripcion> = emptyMap(),
    val cargandoLista: Boolean = false,
    val grabandoAccion: Boolean = false,
    val sincronizado: LocalDateTime? = null,
    val cola: EstadoCola = EstadoCola(),
    val trans: ec.paul.grabador.transcripcion.EstadoTranscripcion = ec.paul.grabador.transcripcion.EstadoTranscripcion(),
    val mensaje: String? = null,
) {
    val grabaciones: List<Grabacion>
        get() {
            val nombres = (remotos.map { it.nombre } + locales.keys).toSortedSet(compareByDescending { it })
            val porNombre = remotos.associateBy { it.nombre }
            return nombres.map { n ->
                val r = porNombre[n]
                val m = locales[n] ?: Meta()
                val seg = r?.segundos ?: m.segundos
                val estado = when {
                    cola.actual == n -> Estado.BAJANDO
                    n in cola.pendientes -> Estado.EN_COLA
                    n in locales && (trans.actual == n || n in trans.pendientes) -> Estado.TRANSCRIBIENDO
                    n in locales && n in transcripciones -> Estado.TRANSCRITA
                    n in locales -> Estado.TELEFONO
                    else -> Estado.NUEVO
                }
                Grabacion(n, Protocolo.fechaDeNombre(n), seg, seg * 4_100, estado, r != null, m, transcripciones[n])
            }
        }
    val nuevas: List<Grabacion> get() = grabaciones.filter { it.estado == Estado.NUEVO }
}

class GrabadorViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app as GrabadorApp
    private val grabador = g.grabador
    private val cola = g.descargas
    val transcripciones = g.transcripciones
    val biblioteca = g.biblioteca
    private val base = MutableStateFlow(UiEstado())
    private val prefsGrabador = app.getSharedPreferences("grabador", android.content.Context.MODE_PRIVATE)
    /** Ajustes que se leen al pintar Ajustes/Grabador; cambiar algo fuerza a repintar. */
    val versionAjustes = MutableStateFlow(0)

    val ui: StateFlow<UiEstado> = combine(base, cola.estado, transcripciones.estado) { b, c, t ->
        b.copy(cola = c, trans = t, mensaje = b.mensaje ?: c.error ?: t.error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiEstado())

    /** La actividad lo observa para lanzar el diálogo del sistema que enciende el Bluetooth. */
    val pedirEncenderBluetooth = MutableStateFlow(false)

    init {
        transcripciones.alTerminar = { refrescarLocales() }
        refrescarLocales()
        viewModelScope.launch { cola.estado.map { it.hechos }.distinctUntilChanged().collect { refrescarLocales() } }
        if (grabador.estaConectado()) { base.update { it.copy(conectado = true) }; actualizar(); vigilarEstado() }
    }

    fun refrescarLocales() {
        val nombres = biblioteca.nombres()
        base.update {
            it.copy(
                locales = nombres.associateWith { n -> biblioteca.meta(n) },
                transcripciones = nombres.mapNotNull { n -> biblioteca.transcripcion(n)?.let { t -> n to t } }.toMap(),
            )
        }
    }

    fun audio(nombre: String): File = biblioteca.audio(nombre)

    private fun intentar(bloque: suspend () -> Unit) = viewModelScope.launch {
        try { bloque() } catch (e: BluetoothApagado) {
            base.update { it.copy(conectando = false, mensaje = e.message) }
            pedirEncenderBluetooth.value = true
        } catch (e: Exception) {
            base.update { it.copy(mensaje = e.message ?: e.toString(), conectando = false, cargandoLista = false) }
        }
    }

    fun conectar() = intentar {
        base.update { it.copy(conectando = true, mensaje = null) }
        grabador.conectar()
        base.update { it.copy(conectando = false, conectado = true) }
        actualizarTodo()
        vigilarEstado()
    }

    /** Cada 10 s pregunta si graba (también ve el botón físico). Nunca en medio de otra operación. */
    private var sondeo: kotlinx.coroutines.Job? = null
    private fun vigilarEstado() {
        sondeo?.cancel()
        sondeo = viewModelScope.launch {
            while (grabador.estaConectado()) {
                kotlinx.coroutines.delay(10_000)
                if (cola.estado.value.ocupada || base.value.grabandoAccion) continue
                val n = runCatching { grabador.grabacionEnCursoSiLibre() }.getOrNull() ?: continue
                val antes = base.value.aparato?.grabando
                ponerGrabando(n)
                if (antes == true && n.isEmpty()) { kotlinx.coroutines.delay(2_000); runCatching { actualizarTodo() } }
            }
        }
    }

    private fun ponerGrabando(enCurso: String) = base.update {
        it.copy(aparato = (it.aparato ?: EstadoGrabador()).copy(grabando = enCurso.isNotEmpty(), grabandoDesde = Protocolo.fechaDeNombre(enCurso)))
    }

    fun grabarODetener() = intentar {
        if (cola.estado.value.ocupada) error("Espera a que termine la descarga para grabar o detener.")
        base.update { it.copy(grabandoAccion = true) }
        try {
            if (base.value.aparato?.grabando == true) {
                if (!grabador.detener()) error("El grabador no confirmó que se detuvo")
                ponerGrabando("")
                kotlinx.coroutines.delay(2_000) // el aparato termina de guardar el archivo
                actualizarTodo()
            } else {
                val n = grabador.grabar() ?: error("El grabador no respondió")
                if (n.isEmpty()) error("El grabador no empezó a grabar")
                ponerGrabando(n)
            }
        } finally {
            base.update { it.copy(grabandoAccion = false) }
        }
    }

    fun desconectar() {
        sondeo?.cancel()
        cola.cancelar()
        grabador.desconectar()
        base.update { UiEstado(locales = it.locales) }
    }

    fun actualizar() = intentar { actualizarTodo() }

    /** Preferencias del grabador (no se pueden leer del aparato: firmware 1.0.0 no responde a 0x26). */
    val grabarAlEncenderGuardado: Boolean? get() = if (prefsGrabador.contains("alEncender")) prefsGrabador.getBoolean("alEncender", false) else null
    val bajarSolaLoNuevo: Boolean get() = prefsGrabador.getBoolean("bajarSola", false)

    fun ponerGrabarAlEncender(v: Boolean) = intentar {
        grabador.grabarAlEncender(v)
        prefsGrabador.edit().putBoolean("alEncender", v).apply()
        versionAjustes.value++
    }

    fun ponerBajarSola(v: Boolean) {
        // Al activarlo, lo que ya está en el grabador cuenta como visto: solo baja lo que se grabe después.
        prefsGrabador.edit().putBoolean("bajarSola", v).putStringSet("conocidos", base.value.remotos.map { it.nombre }.toSet()).apply()
        versionAjustes.value++
    }

    fun borrarDelGrabador(nombre: String) = intentar {
        check(nombre in base.value.locales) { "Solo se borra del grabador lo que ya está en el teléfono" }
        if (!grabador.borrar(nombre)) error("El grabador no pudo borrarla")
        base.update { it.copy(remotos = it.remotos.filterNot { r -> r.nombre == nombre }, mensaje = "Borrada del grabador (sigue en el teléfono)") }
    }

    private fun bajarLoNuevo(lista: List<ArchivoRemoto>) {
        if (!bajarSolaLoNuevo) return
        val conocidos = prefsGrabador.getStringSet("conocidos", emptySet()).orEmpty()
        val nuevos = lista.map { it.nombre }.filter { it !in conocidos && it !in base.value.locales }
        prefsGrabador.edit().putStringSet("conocidos", conocidos + lista.map { it.nombre }).apply()
        if (nuevos.isNotEmpty()) cola.encolar(nuevos.sorted())
    }

    private suspend fun actualizarTodo() {
        if (cola.estado.value.ocupada) return // no mezclar comandos con una descarga en curso
        // Recién detenida una grabación, el aparato tarda en responder: reintentar y no perder lo que ya se sabía.
        var est = grabador.estado()
        if (est.bateria == null) { kotlinx.coroutines.delay(1_500); est = grabador.estado() }
        val previo = base.value.aparato
        if (previo != null) est = est.copy(
            serie = est.serie.ifBlank { previo.serie },
            version = est.version.ifBlank { previo.version },
            bateria = est.bateria ?: previo.bateria,
            libreMb = est.libreMb ?: previo.libreMb,
            totalMb = est.totalMb ?: previo.totalMb,
        )
        base.update { it.copy(aparato = est) }
        if (est.grabando == true) {
            base.update { it.copy(mensaje = "El grabador está grabando: detén la grabación para ver y bajar archivos.") }
            return
        }
        base.update { it.copy(cargandoLista = true) }
        val lista = grabador.listar()
        base.update { it.copy(remotos = lista, cargandoLista = false, sincronizado = LocalDateTime.now()) }
        bajarLoNuevo(lista)
    }

    fun descargar(nombre: String) = cola.encolar(listOf(nombre))
    fun descargarNuevas() = cola.encolar(ui.value.nuevas.sortedBy { it.nombre }.map { it.nombre })
    fun cancelarDescargas() = cola.cancelar()

    fun marcarMomento(nombre: String, ms: Long) {
        biblioteca.actualizar(nombre) { it.copy(momentos = (it.momentos + ms).sorted()) }
        refrescarLocales()
    }

    fun borrarMomento(nombre: String, ms: Long) {
        biblioteca.actualizar(nombre) { it.copy(momentos = it.momentos - ms) }
        refrescarLocales()
    }

    fun guardarNotas(nombre: String, notas: String) {
        biblioteca.actualizar(nombre) { it.copy(notas = notas) }
        refrescarLocales()
    }

    fun renombrar(nombre: String, titulo: String) {
        biblioteca.actualizar(nombre) { it.copy(titulo = titulo.trim()) }
        refrescarLocales()
    }

    fun cerrarMensaje() = base.update { it.copy(mensaje = null) }

    fun transcribir(nombre: String) = transcripciones.encolar(listOf(nombre))
    fun cancelarTranscripcion() = transcripciones.cancelar()

    /** Ajustes de transcripción; cambiar algo fuerza a repintar (se leen en la pantalla de Ajustes). */
    fun elegirModelo(m: ec.paul.grabador.transcripcion.Modelo) { transcripciones.ajustes.modelo = m; versionAjustes.value++ }
    fun transcribirSola(v: Boolean) { transcripciones.ajustes.automatica = v; versionAjustes.value++ }
    fun soloCargando(v: Boolean) { transcripciones.ajustes.soloCargando = v; versionAjustes.value++ }
}
