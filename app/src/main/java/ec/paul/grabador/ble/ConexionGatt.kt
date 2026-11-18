package ec.paul.grabador.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Conexión GATT con el grabador. Android solo admite una operación GATT a la vez,
 * así que todas pasan por [mutex] y esperan su callback.
 * Las respuestas (0011203a) y los datos (0011204a) llegan por canales sin límite.
 */
@SuppressLint("MissingPermission") // los permisos se piden en la actividad antes de conectar
class ConexionGatt(private val contexto: Context, private val dispositivo: BluetoothDevice) {
    private val tag = "ConexionGatt"
    private var gatt: BluetoothGatt? = null
    private val mutex = Mutex()
    private var pendiente: CompletableDeferred<Int>? = null

    val respuestas = Channel<Respuesta>(Channel.UNLIMITED)
    val datos = Channel<ByteArray>(Channel.UNLIMITED)

    private val _conectado = MutableStateFlow(false)
    val conectado: StateFlow<Boolean> = _conectado
    var mtu = 23
        private set

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, nuevo: Int) {
            Log.d(tag, "estado=$nuevo status=$status")
            if (nuevo == BluetoothProfile.STATE_CONNECTED) {
                pendiente?.complete(status)
            } else if (nuevo == BluetoothProfile.STATE_DISCONNECTED) {
                _conectado.value = false
                pendiente?.complete(-1)
                g.close()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { pendiente?.complete(status) }
        override fun onMtuChanged(g: BluetoothGatt, nuevoMtu: Int, status: Int) {
            mtu = nuevoMtu
            pendiente?.complete(status)
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendiente?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, valor: ByteArray) {
            when (c.uuid) {
                Protocolo.RESPUESTAS -> LectorTramas.leer(valor).forEach { respuestas.trySend(it) }
                Protocolo.DATOS -> datos.trySend(valor)
            }
        }
    }

    private suspend fun operar(ms: Long = 10_000, accion: () -> Boolean): Int = mutex.withLock {
        val d = CompletableDeferred<Int>()
        pendiente = d
        check(accion()) { "Android rechazó la operación GATT" }
        try { withTimeout(ms) { d.await() } } finally { pendiente = null }
    }

    suspend fun conectar() {
        val st = operar(20_000) {
            gatt = dispositivo.connectGatt(contexto, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt != null
        }
        check(st == BluetoothGatt.GATT_SUCCESS) { "no se pudo conectar (status $st)" }
        val g = gatt!!
        operar { g.requestMtu(247) }
        operar { g.discoverServices() }
        val svc = checkNotNull(g.getService(Protocolo.SERVICIO)) { "este aparato no tiene el servicio AIREC" }
        // Medido 2026-11-13: SIN notify en 0011201a el aparato acepta el 07 pero no manda ni un byte del archivo.
        // Por 201a llega el audio en vivo solo mientras graba; aquí se ignora.
        for (uuid in listOf(Protocolo.RESPUESTAS, Protocolo.DATOS, Protocolo.VIVO)) {
            val c = checkNotNull(svc.getCharacteristic(uuid))
            g.setCharacteristicNotification(c, true)
            val cccd = c.getDescriptor(Protocolo.CCCD)
            operar { g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS }
        }
        _conectado.value = true
        Log.d(tag, "listo, mtu=$mtu")
    }

    /** Escribe sin respuesta; si la pila está ocupada, reintenta. */
    suspend fun enviar(trama: ByteArray) {
        val g = checkNotNull(gatt) { "sin conexión" }
        val c = checkNotNull(g.getService(Protocolo.SERVICIO)?.getCharacteristic(Protocolo.ESCRIBIR))
        mutex.withLock {
            repeat(20) {
                val r = g.writeCharacteristic(c, trama, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                if (r == BluetoothStatusCodes.SUCCESS) return
                delay(50)
            }
            error("no se pudo escribir en el grabador")
        }
    }

    /** Vacía respuestas viejas antes de un comando nuevo. */
    fun vaciar() {
        while (respuestas.tryReceive().isSuccess) Unit
        while (datos.tryReceive().isSuccess) Unit
    }

    fun desconectar() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _conectado.value = false
    }
}
