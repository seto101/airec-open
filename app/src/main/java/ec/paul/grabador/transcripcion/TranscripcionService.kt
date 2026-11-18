package ec.paul.grabador.transcripcion

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import ec.paul.grabador.GrabadorApp
import ec.paul.grabador.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Mantiene vivo el proceso mientras se transcribe, con su notificación de progreso. */
class TranscripcionService : Service() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bloqueo: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tipo = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        startForeground(ID, notificacion("Preparando la transcripción…", 0f, true), tipo)
        if (bloqueo == null) {
            bloqueo = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "grabador:transcripcion").apply { acquire(6 * 60 * 60 * 1000L) }
        }
        val cola = (application as GrabadorApp).transcripciones
        alcance.launch {
            cola.estado.collect { e ->
                if (!e.ocupada && e.bajandoModelo == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                val texto = when {
                    e.bajandoModelo != null -> "Bajando el modelo de transcripción…"
                    e.esperandoCarga -> "Esperando a que conectes el cargador"
                    else -> "Transcribiendo" + if (e.pendientes.isNotEmpty()) " (+${e.pendientes.size})" else ""
                }
                getSystemService(NotificationManager::class.java)
                    .notify(ID, notificacion(texto, e.bajandoModelo ?: e.progreso, e.esperandoCarga))
            }
        }
        return START_NOT_STICKY
    }

    private fun notificacion(texto: String, progreso: Float, indeterminado: Boolean): Notification {
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, GrabadorApp.CANAL_DESCARGAS)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Grabador")
            .setContentText(texto)
            .setProgress(1000, (progreso * 1000).toInt(), indeterminado)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(abrir)
            .build()
    }

    override fun onDestroy() {
        alcance.cancel()
        bloqueo?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object { private const val ID = 2 }
}
