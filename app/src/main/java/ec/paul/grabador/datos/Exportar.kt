package ec.paul.grabador.datos

import ec.paul.grabador.ui.Grabacion
import ec.paul.grabador.ui.componentes.duracion
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Markdown de una grabación, para notas (Obsidian, Nextcloud…) o para pegar en la IA. */
object Exportar {
    private val FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-EC"))

    fun markdown(g: Grabacion, titulo: String, transcripcion: String? = null): String = buildString {
        appendLine("# $titulo")
        appendLine()
        g.fecha?.let { appendLine("- **Fecha:** ${it.format(FECHA)}") }
        appendLine("- **Duración:** ${duracion(g.segundos)}")
        appendLine("- **Archivo:** ${g.nombre}.opus")
        if (g.meta.momentos.isNotEmpty()) {
            appendLine()
            appendLine("## Momentos marcados")
            g.meta.momentos.forEach { appendLine("- ${duracion(it / 1000)}") }
        }
        if (g.meta.notas.isNotBlank()) {
            appendLine()
            appendLine("## Notas")
            appendLine(g.meta.notas.trim())
        }
        appendLine()
        appendLine("## Transcripción")
        appendLine(transcripcion?.trim()?.ifBlank { null } ?: "_(aún sin transcripción)_")
    }

    fun paraIa(md: String): String =
        "Esta es una reunión grabada. Resúmela en español: puntos clave, decisiones, acuerdos y tareas con responsable y fecha si se mencionan.\n\n$md"
}
