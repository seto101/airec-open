package ec.paul.grabador

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ec.paul.grabador.ble.Grabador
import ec.paul.grabador.datos.Biblioteca
import ec.paul.grabador.descargas.ColaDescargas
import ec.paul.grabador.transcripcion.ColaTranscripcion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Una sola conexión, una sola cola y una sola biblioteca para toda la app. */
class GrabadorApp : Application() {
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var grabador: Grabador
        private set
    lateinit var biblioteca: Biblioteca
        private set
    lateinit var descargas: ColaDescargas
        private set
    lateinit var transcripciones: ColaTranscripcion
        private set

    override fun onCreate() {
        super.onCreate()
        grabador = Grabador(this)
        biblioteca = Biblioteca(File(getExternalFilesDir(null), "grabaciones").apply { mkdirs() })
        transcripciones = ColaTranscripcion(this, biblioteca, alcance)
        descargas = ColaDescargas(this, grabador, biblioteca, alcance) { nombre ->
            if (transcripciones.ajustes.automatica) transcripciones.encolar(listOf(nombre))
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL_DESCARGAS, "Descargas del grabador", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val CANAL_DESCARGAS = "descargas"
    }
}
