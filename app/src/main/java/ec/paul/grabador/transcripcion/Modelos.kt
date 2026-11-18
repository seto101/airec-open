package ec.paul.grabador.transcripcion

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Modelos medidos en el teléfono el 2026-11-16 (Snapdragon 7 Gen 3, 4 hilos, audio con ruido). */
enum class Modelo(
    val id: String,
    val archivo: String,
    val bytes: Long,
    val nombre: String,
    val detalle: String,
) {
    SMALL("small", "ggml-small-q5_1.bin", 190_085_487, "Rápida", "small · 190 MB · ~45 min por hora de audio"),
    TURBO("large-v3-turbo", "ggml-large-v3-turbo-q5_0.bin", 574_041_195, "Precisa", "turbo · 574 MB · ~2 h 15 min por hora; mejor solo cargando");

    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$archivo"
}

class Modelos(contexto: Context) {
    // En la carpeta externa de la app (Android/data/…/files/modelos): se puede sembrar por adb y
    // sobrevive a reinstalar la misma firma.
    private val carpeta = File(contexto.getExternalFilesDir(null), "modelos").apply { mkdirs() }

    fun archivo(m: Modelo) = File(carpeta, m.archivo)
    fun disponible(m: Modelo) = archivo(m).let { it.exists() && it.length() == m.bytes }

    /** Descarga (o retoma) el modelo; [progreso] de 0 a 1. */
    fun descargar(m: Modelo, progreso: (Float) -> Unit) {
        if (disponible(m)) return
        val parcial = File(carpeta, m.archivo + ".parcial")
        var desde = if (parcial.exists()) parcial.length() else 0L
        val con = URL(m.url).openConnection() as HttpURLConnection
        con.instanceFollowRedirects = true
        con.connectTimeout = 20_000
        con.readTimeout = 30_000
        if (desde > 0) con.setRequestProperty("Range", "bytes=$desde-")
        con.connect()
        if (desde > 0 && con.responseCode != HttpURLConnection.HTTP_PARTIAL) desde = 0 // no retoma: desde cero
        check(con.responseCode in 200..299) { "No se pudo bajar el modelo (HTTP ${con.responseCode})" }
        con.inputStream.use { ent ->
            java.io.FileOutputStream(parcial, desde > 0).use { sal ->
                val b = ByteArray(256 * 1024)
                var total = desde
                var ultimo = 0L
                while (true) {
                    val n = ent.read(b)
                    if (n < 0) break
                    sal.write(b, 0, n)
                    total += n
                    val ahora = System.currentTimeMillis()
                    if (ahora - ultimo > 300) { progreso(total.toFloat() / m.bytes); ultimo = ahora }
                }
            }
        }
        check(parcial.length() == m.bytes) { "El modelo bajó incompleto (${parcial.length()} de ${m.bytes} bytes)" }
        check(parcial.renameTo(archivo(m))) { "No se pudo guardar el modelo" }
    }
}

/** Ajustes de transcripción (SharedPreferences). */
class AjustesTranscripcion(contexto: Context) {
    private val p = contexto.getSharedPreferences("transcripcion", Context.MODE_PRIVATE)

    var modelo: Modelo
        get() = Modelo.entries.firstOrNull { it.id == p.getString("modelo", null) } ?: Modelo.SMALL
        set(v) = p.edit().putString("modelo", v.id).apply()

    /** Transcribir sola cada grabación al terminar de bajarla. */
    var automatica: Boolean
        get() = p.getBoolean("automatica", true)
        set(v) = p.edit().putBoolean("automatica", v).apply()

    /** Solo transcribir con el teléfono cargando (recomendado con el modelo grande). */
    var soloCargando: Boolean
        get() = p.getBoolean("soloCargando", false)
        set(v) = p.edit().putBoolean("soloCargando", v).apply()
}
