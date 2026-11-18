package ec.paul.grabador.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class EstadoReproductor(
    val listo: Boolean = false,
    val sonando: Boolean = false,
    val posMs: Long = 0,
    val durMs: Long = 0,
    val velocidad: Float = 1f,
)

class Reproductor(private val alcance: CoroutineScope) {
    private var mp: MediaPlayer? = null
    private var tic: Job? = null
    private val _estado = MutableStateFlow(EstadoReproductor())
    val estado: StateFlow<EstadoReproductor> = _estado

    fun abrir(f: File) {
        liberar()
        mp = MediaPlayer().apply {
            setDataSource(f.path)
            setOnPreparedListener { _estado.value = EstadoReproductor(listo = true, durMs = duration.toLong()) }
            setOnCompletionListener { _estado.value = _estado.value.copy(sonando = false, posMs = _estado.value.durMs) }
            prepareAsync()
        }
    }

    fun alternar() {
        val p = mp ?: return
        if (!_estado.value.listo) return
        if (p.isPlaying) {
            p.pause()
            tic?.cancel()
            _estado.value = _estado.value.copy(sonando = false, posMs = p.currentPosition.toLong())
        } else {
            if (_estado.value.posMs >= _estado.value.durMs - 200) p.seekTo(0)
            p.start()
            aplicarVelocidad(_estado.value.velocidad)
            _estado.value = _estado.value.copy(sonando = true)
            tic = alcance.launch(Dispatchers.Main) {
                while (isActive) {
                    _estado.value = _estado.value.copy(posMs = p.currentPosition.toLong())
                    delay(200)
                }
            }
        }
    }

    fun ir(ms: Long) {
        val p = mp ?: return
        val destino = ms.coerceIn(0, _estado.value.durMs)
        p.seekTo(destino, MediaPlayer.SEEK_CLOSEST)
        _estado.value = _estado.value.copy(posMs = destino)
    }

    fun mover(deltaMs: Long) = ir(_estado.value.posMs + deltaMs)

    fun siguienteVelocidad() {
        val v = when (_estado.value.velocidad) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
        _estado.value = _estado.value.copy(velocidad = v)
        if (mp?.isPlaying == true) aplicarVelocidad(v)
    }

    private fun aplicarVelocidad(v: Float) {
        runCatching { mp?.playbackParams = (mp?.playbackParams ?: PlaybackParams()).setSpeed(v) }
    }

    fun liberar() {
        tic?.cancel()
        mp?.release()
        mp = null
        _estado.value = EstadoReproductor()
    }
}
