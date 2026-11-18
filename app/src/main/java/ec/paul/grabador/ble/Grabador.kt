package ec.paul.grabador.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDateTime

class BluetoothApagado : IllegalStateException("El Bluetooth del teléfono está apagado")

data class EstadoGrabador(
    val serie: String = "",
    val version: String = "",
    val bateria: Int? = null,
    val libreMb: Long? = null,
    val totalMb: Long? = null,
    val grabando: Boolean? = null,
    /** Inicio de la grabación en curso (sale de su nombre, que el aparato pone con su reloj). */
    val grabandoDesde: LocalDateTime? = null,
)

/** Operaciones de alto nivel, probadas contra el aparato real. */
@SuppressLint("MissingPermission")
class Grabador(private val contexto: Context) {
    private var conexion: ConexionGatt? = null
    val conectado get() = conexion?.conectado

    private val adaptador get() = contexto.getSystemService(BluetoothManager::class.java).adapter
    private val prefs get() = contexto.getSharedPreferences("grabador", Context.MODE_PRIVATE)

    val bluetoothEncendido: Boolean get() = adaptador?.isEnabled == true

    /** Sin filtro de hardware: algunos chips no ven el nombre si va en la respuesta de escaneo. */
    suspend fun buscar(ms: Long = 15_000): BluetoothDevice {
        if (!bluetoothEncendido) throw BluetoothApagado()
        val escaner = adaptador.bluetoothLeScanner ?: throw BluetoothApagado()
        val encontrado = CompletableDeferred<BluetoothDevice>()
        val cb = object : ScanCallback() {
            override fun onScanResult(tipo: Int, r: ScanResult) {
                val nombre = r.scanRecord?.deviceName ?: r.device.name
                val tieneServicio = r.scanRecord?.serviceUuids?.any { it.uuid == Protocolo.SERVICIO } == true
                if (nombre == Protocolo.NOMBRE_BLE || tieneServicio) encontrado.complete(r.device)
            }
            override fun onScanFailed(codigo: Int) {
                encontrado.completeExceptionally(IllegalStateException("La búsqueda Bluetooth falló (código $codigo)"))
            }
        }
        escaner.startScan(emptyList<ScanFilter>(), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        try {
            return withTimeout(ms) { encontrado.await() }
        } catch (e: TimeoutCancellationException) {
            error("No encuentro el grabador. ¿Está encendido, cerca, y cerrada la app AIREC?")
        } finally {
            escaner.stopScan(cb)
        }
    }

    suspend fun conectar() {
        if (!bluetoothEncendido) throw BluetoothApagado()
        // Primero, directo a la MAC conocida (la del grabador es pública y fija); si falla, buscar.
        val conocida = prefs.getString("mac", null)
        var c: ConexionGatt? = null
        if (conocida != null) {
            c = ConexionGatt(contexto, adaptador.getRemoteDevice(conocida))
            if (runCatching { c.conectar() }.isFailure) { c.desconectar(); c = null }
        }
        if (c == null) {
            val d = buscar()
            c = ConexionGatt(contexto, d)
            c.conectar()
            prefs.edit().putString("mac", d.address).apply()
        }
        conexion = c
        // Lo primero, como la app oficial: poner el reloj en hora para que lo que se grabe se llame con su fecha real.
        pedir(Protocolo.ponerHora(LocalDateTime.now()), Protocolo.HORA)
    }

    fun desconectar() {
        conexion?.desconectar()
        conexion = null
    }

    private fun c() = checkNotNull(conexion) { "Sin conexión con el grabador" }

    /**
     * Una operación con el aparato a la vez: las respuestas llegan por un solo canal y [pedir] lo vacía,
     * así que un sondeo de estado en medio de una descarga se comería sus datos.
     */
    private val ocupado = kotlinx.coroutines.sync.Mutex()
    private suspend fun <T> enExclusiva(bloque: suspend () -> T): T = ocupado.withLock { bloque() }

    /** Manda un comando y espera la primera respuesta con el código indicado. */
    private suspend fun pedir(trama: ByteArray, espera: Int, ms: Long = 3_000): Respuesta? {
        val c = c()
        c.vaciar()
        c.enviar(trama)
        return withTimeoutOrNull(ms) {
            while (true) {
                val r = c.respuestas.receive()
                if (r.cmd == espera) return@withTimeoutOrNull r
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }

    suspend fun estado(): EstadoGrabador = enExclusiva { estadoSinCandado() }

    private suspend fun estadoSinCandado(): EstadoGrabador {
        val serie = pedir(Protocolo.trama(Protocolo.SN), Protocolo.SN)?.texto.orEmpty()
        val version = pedir(Protocolo.trama(Protocolo.VERSION), Protocolo.VERSION)?.texto.orEmpty()
        val bateria = pedir(Protocolo.trama(Protocolo.BATERIA), Protocolo.BATERIA)?.payload?.firstOrNull()?.toInt()
        // 0F: 00 + nombre = grabando; 02 = parado (medido)
        val enCurso = enCursoSinCandado()
        val grabando = enCurso?.isNotEmpty()
        // 0B responde con dos tramas: 0C (libre) y 0D (total), en MB
        val c = c()
        c.vaciar()
        c.enviar(Protocolo.trama(Protocolo.ESPACIO))
        var libre: Long? = null
        var total: Long? = null
        withTimeoutOrNull(3_000) {
            while (libre == null || total == null) {
                val r = c.respuestas.receive()
                if (r.cmd == Protocolo.ESPACIO_LIBRE) libre = Protocolo.leerU32(r.payload, 0)
                if (r.cmd == Protocolo.ESPACIO_TOTAL) total = Protocolo.leerU32(r.payload, 0)
            }
        }
        return EstadoGrabador(serie, version, bateria, libre, total, grabando, enCurso?.let { Protocolo.fechaDeNombre(it) })
    }

    /** 05 → una trama 05 por archivo, 06 al final. Mientras graba, el aparato no responde. */
    suspend fun listar(): List<ArchivoRemoto> = enExclusiva { listarSinCandado() }

    private suspend fun listarSinCandado(): List<ArchivoRemoto> {
        val c = c()
        c.vaciar()
        c.enviar(Protocolo.trama(Protocolo.LISTA))
        val out = mutableListOf<ArchivoRemoto>()
        val ok = withTimeoutOrNull(15_000) {
            while (true) {
                val r = c.respuestas.receive()
                when (r.cmd) {
                    Protocolo.LISTA -> ArchivoRemoto.de(r)?.let(out::add)
                    Protocolo.FIN_LISTA -> return@withTimeoutOrNull true
                    Protocolo.FALLO_LISTA -> error("El grabador no pudo leer sus archivos")
                }
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (ok == null) error("El grabador no respondió a la lista. ¿Está grabando? Detén la grabación e inténtalo de nuevo.")
        return out.sortedByDescending { it.nombre }
    }

    fun estaConectado() = conexion?.conectado?.value == true

    /** 0F: `00` + nombre de la grabación en curso, o `02` parado. Devuelve el nombre si graba, "" si no, null si no respondió. */
    suspend fun grabacionEnCurso(): String? = enExclusiva { enCursoSinCandado() }

    /** Para el sondeo periódico: si hay otra operación en marcha, no espera; devuelve null. */
    suspend fun grabacionEnCursoSiLibre(): String? {
        if (!ocupado.tryLock()) return null
        return try { enCursoSinCandado() } finally { ocupado.unlock() }
    }

    private suspend fun enCursoSinCandado(): String? {
        val r = pedir(Protocolo.trama(Protocolo.ESTADO), Protocolo.ESTADO) ?: return null
        return if (r.payload.firstOrNull()?.toInt() == 0) r.payload.copyOfRange(1, r.payload.size).toString(Charsets.US_ASCII) else ""
    }

    /** Medido 2026-11-16: parado → 03, 100 ms, 21 'R' → graba. 03 alterna: solo se manda si está parado. */
    suspend fun grabar(): String? = enExclusiva {
        enCursoSinCandado()?.takeIf { it.isNotEmpty() }?.let { return@enExclusiva it }
        c().enviar(Protocolo.trama(Protocolo.GRABAR))
        kotlinx.coroutines.delay(100)
        c().enviar(Protocolo.trama(Protocolo.TIPO_GRABACION, byteArrayOf('R'.code.toByte())))
        kotlinx.coroutines.delay(1_500)
        enCursoSinCandado()
    }

    /** 0A + nombre de 14 dígitos. Devuelve true si el aparato confirmó (0A); false si respondió FC. */
    suspend fun borrar(nombre: String): Boolean = enExclusiva {
        require(nombre.length == 14 && nombre.all { it.isDigit() })
        val c = c()
        c.vaciar()
        c.enviar(Protocolo.trama(Protocolo.BORRAR, nombre.toByteArray(Charsets.US_ASCII)))
        withTimeoutOrNull(4_000) {
            while (true) {
                val r = c.respuestas.receive()
                if (r.cmd == Protocolo.BORRAR) return@withTimeoutOrNull true
                if (r.cmd == Protocolo.FALLO_BORRAR) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE") false
        } ?: error("El grabador no respondió al borrar")
    }

    /** 2E 00/01. El aparato no responde ni deja leer el valor: se apunta en la app lo último que se mandó. */
    suspend fun grabarAlEncender(activo: Boolean) = enExclusiva {
        c().enviar(Protocolo.trama(Protocolo.GRABAR_AL_ENCENDER, byteArrayOf(if (activo) 1 else 0)))
        kotlinx.coroutines.delay(500)
    }

    /** 04 detiene siempre (no alterna). */
    suspend fun detener(): Boolean = enExclusiva {
        c().enviar(Protocolo.trama(Protocolo.DETENER))
        kotlinx.coroutines.delay(1_500)
        enCursoSinCandado() == ""
    }

    /**
     * 07 nombre + desplazamiento → respuesta 07 con tamaño → datos por 0011204a → 09.
     * El aparato transcodifica a Opus (5B 50 + 80 B por paquete) al enviar. Los datos se AÑADEN a
     * [parcial]: si ya tiene algo, se recorta a paquetes completos y se pide desde ahí.
     */
    suspend fun descargar(nombre: String, parcial: File, progreso: (Long, Long) -> Unit) =
        enExclusiva { descargarSinCandado(nombre, parcial, progreso) }

    private suspend fun descargarSinCandado(nombre: String, parcial: File, progreso: (Long, Long) -> Unit) {
        val c = c()
        parcial.parentFile?.mkdirs()
        val previo = Reanudacion.recortar(parcial)
        val desplazamiento = Reanudacion.desplazamiento(previo)
        val r = pedir(Protocolo.descargar(nombre, desplazamiento), Protocolo.DESCARGAR, 5_000)
            ?: error("El grabador no aceptó la descarga")
        val anunciado = Protocolo.leerU32(r.payload, r.payload.size - 4)
        val total = Reanudacion.totalEsperado(previo, anunciado)
        if (previo >= total) { // ya estaba completo (p. ej. se cortó justo antes del 09)
            runCatching { c.enviar(Protocolo.trama(Protocolo.PAUSAR)) }
            progreso(total, total)
            return
        }
        var recibidos = 0L
        var ultimoDato = System.currentTimeMillis()
        java.io.FileOutputStream(parcial, true).buffered(64 * 1024).use { out ->
            while (previo + recibidos < total) {
                val d = withTimeoutOrNull(1_000) { c.datos.receive() }
                if (d != null) {
                    out.write(d)
                    recibidos += d.size
                    ultimoDato = System.currentTimeMillis()
                    progreso(previo + recibidos, total)
                } else {
                    out.flush()
                    val fin = c.respuestas.tryReceive().getOrNull()
                    if (fin?.cmd == Protocolo.FIN_DESCARGA) break
                    if (fin?.cmd == Protocolo.FALLO_DESCARGA) error("El grabador interrumpió la descarga")
                    if (!estaConectado()) error("Se perdió la conexión")
                    if (System.currentTimeMillis() - ultimoDato > 15_000) {
                        runCatching { c.enviar(Protocolo.trama(Protocolo.PAUSAR)) }
                        error("La descarga se detuvo (${previo + recibidos} de $total bytes)")
                    }
                }
            }
        }
    }
}

/**
 * Reanudación. El archivo parcial guarda lo ENVIADO (paquetes de 82 B).
 * Medido 2026-11-13: el desplazamiento de 07 cuenta bytes ENVIADOS (no del WAV), y la transcodificación
 * es determinista: lo recibido desde la mitad coincide byte a byte con la descarga completa.
 * Un desplazamiento ≥ tamaño hace que el aparato mande basura desde el principio: no pedirlo nunca.
 */
object Reanudacion {
    enum class Unidad { ENVIADO, WAV }
    var UNIDAD = Unidad.ENVIADO
    const val PAQUETE = 82L
    private const val WAV_POR_PAQUETE = 640L

    /** Corta el parcial a paquetes completos y devuelve su tamaño. */
    fun recortar(f: File): Long {
        if (!f.exists()) return 0
        val n = f.length() / PAQUETE * PAQUETE
        if (n != f.length()) java.io.RandomAccessFile(f, "rw").use { it.setLength(n) }
        return n
    }

    fun desplazamiento(previo: Long): Long = when {
        previo == 0L -> 0
        UNIDAD == Unidad.ENVIADO -> previo
        else -> 44 + previo / PAQUETE * WAV_POR_PAQUETE
    }

    /** Medido 2026-11-13: con desplazamiento, el 07 sigue anunciando el TOTAL. */
    var ANUNCIA_LO_QUE_FALTA = false

    fun totalEsperado(previo: Long, anunciado: Long): Long =
        if (previo == 0L || !ANUNCIA_LO_QUE_FALTA) anunciado else previo + anunciado
}
