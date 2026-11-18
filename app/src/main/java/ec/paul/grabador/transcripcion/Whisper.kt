package ec.paul.grabador.transcripcion

import ec.paul.grabador.datos.Frase
import java.io.Closeable
import java.io.File

/** whisper.cpp por JNI (app/src/main/cpp/transcriptor.cpp). Un hilo a la vez por instancia. */
class Whisper private constructor(private var ptr: Long) : Closeable {

    fun interface Progreso { fun avance(porcentaje: Int) }

    /** [pcm] float 16 kHz mono. Devuelve frases con tiempos relativos al trozo; vacío si se canceló. */
    fun transcribir(pcm: FloatArray, hilos: Int, idioma: String = "es", progreso: Progreso? = null): List<Frase> {
        check(ptr != 0L) { "modelo cerrado" }
        val salida = transcribir(ptr, pcm, hilos, idioma, progreso)
        return salida.lineSequence().mapNotNull { l ->
            val p = l.split('\t', limit = 3)
            if (p.size < 3 || p[2].isBlank()) null else Frase(p[0].toLong(), p[1].toLong(), p[2].trim())
        }.toList()
    }

    override fun close() {
        if (ptr != 0L) liberar(ptr)
        ptr = 0
    }

    private external fun transcribir(ptr: Long, pcm: FloatArray, hilos: Int, idioma: String, progreso: Progreso?): String
    private external fun liberar(ptr: Long)

    companion object {
        init { System.loadLibrary("transcriptor") }

        fun cargar(modelo: File): Whisper {
            val p = iniciar(modelo.path)
            check(p != 0L) { "No se pudo cargar el modelo ${modelo.name}" }
            return Whisper(p)
        }

        /** Corta la transcripción en curso (se comprueba entre pasos de whisper). */
        fun cancelarTodo() = cancelar()

        @JvmStatic private external fun iniciar(ruta: String): Long
        @JvmStatic private external fun cancelar()
    }
}
