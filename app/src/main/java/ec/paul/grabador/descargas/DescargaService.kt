package ec.paul.grabador.descargas

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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

/** Mantiene vivo el proceso mientras la cola baja archivos, con una notificación de progreso. */
class DescargaService : Service() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bloqueo: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as GrabadorApp
        startForeground(ID, notificacion("Preparando descarga…", 0f, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if (bloqueo == null) {
            bloqueo = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "grabador:descarga").apply { acquire(3 * 60 * 60 * 1000L) }
        }
        alcance.launch {
            app.descargas.estado.collect { e ->
                if (!e.ocupada) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                val quedan = e.pendientes.size
                val titulo = "Bajando ${e.actual ?: ""}" + if (quedan > 0) " (+$quedan)" else ""
                getSystemService(NotificationManager::class.java).notify(ID, notificacion(titulo, e.progreso, false))
            }
        }
        return START_NOT_STICKY
    }

    private fun notificacion(texto: String, progreso: Float, indeterminado: Boolean): Notification {
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, GrabadorApp.CANAL_DESCARGAS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
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

    companion object { private const val ID = 1 }
}
