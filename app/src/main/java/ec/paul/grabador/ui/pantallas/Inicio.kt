package ec.paul.grabador.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.paul.grabador.ui.Estado
import ec.paul.grabador.ui.Grabacion
import ec.paul.grabador.ui.UiEstado
import ec.paul.grabador.ui.componentes.BarraFina
import ec.paul.grabador.ui.componentes.BarraSegmentos
import ec.paul.grabador.ui.componentes.BloqueFecha
import ec.paul.grabador.ui.componentes.Boton
import ec.paul.grabador.ui.componentes.BotonIcono
import ec.paul.grabador.ui.componentes.Chip
import ec.paul.grabador.ui.componentes.Onda
import ec.paul.grabador.ui.componentes.Punto
import ec.paul.grabador.ui.componentes.Seccion
import ec.paul.grabador.ui.componentes.Tarjeta
import ec.paul.grabador.ui.componentes.duracion
import ec.paul.grabador.ui.componentes.duracionCorta
import ec.paul.grabador.ui.componentes.megas
import ec.paul.grabador.ui.tema.C
import ec.paul.grabador.ui.tema.cuerpo
import ec.paul.grabador.ui.tema.mono
import ec.paul.grabador.ui.tema.titulo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val ES: Locale = Locale.forLanguageTag("es-EC")
private val HOY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
private val HORA = DateTimeFormatter.ofPattern("HH:mm", ES)
private val MES = DateTimeFormatter.ofPattern("MMM", ES)

fun tituloDe(g: Grabacion) = g.meta.titulo.ifBlank { g.fecha?.let { "Grabación de las ${it.format(HORA)}" } ?: g.nombre }

fun haceCuanto(t: LocalDateTime?): String {
    if (t == null) return "—"
    val min = Duration.between(t, LocalDateTime.now()).toMinutes()
    return when {
        min < 1 -> "ahora"
        min < 60 -> "hace $min min"
        else -> "hace ${min / 60} h"
    }
}

@Composable
fun PantallaInicio(
    ui: UiEstado,
    conectar: () -> Unit,
    actualizar: () -> Unit,
    abrir: (String) -> Unit,
    bajarNuevas: () -> Unit,
    verDescarga: () -> Unit,
    grabar: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(C.Fondo).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(LocalDate.now().format(HOY).replaceFirstChar { it.titlecase(ES) }, style = cuerpo(13.sp, color = C.Suave))
                    Text("Grabaciones", style = titulo(30.sp))
                }
                if (ui.conectado) BotonIcono(Icons.Rounded.Refresh, "Actualizar lista", actualizar)
            }
        }
        item { Spacer(Modifier.height(6.dp)); Mosaico(ui, conectar, grabar) }
        item { Aviso(ui, bajarNuevas, verDescarga) }
        if (ui.cargandoLista) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = C.Ambar, trackColor = C.Pista) }
        item { Seccion("Recientes") }
        if (ui.grabaciones.isEmpty() && !ui.cargandoLista) item {
            Text(
                when {
                    !ui.conectado -> "Conecta el grabador para ver sus grabaciones."
                    ui.aparato?.grabando == true -> "El grabador está grabando. Detén la grabación y toca actualizar para ver y bajar sus archivos."
                    else -> "El grabador no tiene grabaciones."
                },
                style = cuerpo(14.sp, color = C.Suave), modifier = Modifier.padding(6.dp),
            )
        }
        items(ui.grabaciones, key = { it.nombre }) { g -> Fila(g, ui) { abrir(g.nombre) } }
    }
}

@Composable
private fun Mosaico(ui: UiEstado, conectar: () -> Unit, grabar: () -> Unit) {
    val a = ui.aparato
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tarjeta(Modifier.weight(1f).fillMaxHeight(), radio = 24.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(width = 30.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color24).border(1.dp, Color33, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.TopCenter,
                    ) { Box(Modifier.padding(top = 5.dp).size(4.dp).clip(CircleShape).background(if (ui.conectado) C.Ambar else C.Gris)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("AIREC", style = titulo(16.sp, 600), maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Punto(if (a?.grabando == true) C.Rojo else if (ui.conectado) C.Verde else C.Gris)
                            Text(
                                when { ui.conectando -> "Conectando…"; ui.conectado -> if (a?.grabando == true) "Grabando" else "Conectado"; else -> "Sin conexión" },
                                style = cuerpo(12.sp, color = C.Suave), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!ui.conectado) {
                    Boton(if (ui.conectando) "Conectando…" else "Conectar", conectar, Modifier.fillMaxWidth(), alto = 44.dp, radio = 14.dp, activo = !ui.conectando)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(a?.bateria?.toString() ?: "—", style = mono(36.sp, 600))
                            Text(" %", style = mono(16.sp, color = C.Suave), modifier = Modifier.padding(bottom = 5.dp))
                        }
                        BarraSegmentos(10, (a?.bateria ?: 0) / 10f, C.Verde)
                        Column(
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Linea))
                            Detalle("Firmware", a?.version?.ifBlank { "—" } ?: "—", monoValor = true)
                            Detalle("Reloj", "en hora", color = C.VerdeTexto)
                            Detalle("Sincronizado", haceCuanto(ui.sincronizado))
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tarjeta(Modifier.fillMaxWidth().weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Libre", style = cuerpo(12.sp, color = C.Suave))
                    val libre = a?.libreMb
                    val total = a?.totalMb
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(libre?.let { "%.1f".format(ES, it / 1024.0) } ?: "—", style = mono(20.sp, 600))
                        Text(total?.let { " / %.1f GB".format(ES, it / 1024.0) } ?: "", style = mono(12.sp, color = C.Suave), modifier = Modifier.padding(bottom = 2.dp))
                    }
                    BarraFina(if (libre != null && total != null && total > 0) libre.toFloat() / total else 0f, C.Gris)
                    if (libre != null) {
                        Text("${ui.remotos.size} archivos", style = cuerpo(11.sp, color = C.Suave), maxLines = 1)
                        Text("~${libre * 1024 * 1024 / 32_000 / 3600} h para grabar", style = cuerpo(11.sp, color = C.Suave), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text("conecta para ver", style = cuerpo(11.sp, color = C.Suave), maxLines = 1)
                    }
                }
            }
            BotonGrabar(ui, grabar)
        }
    }
}

private val Color24 = androidx.compose.ui.graphics.Color(0xFF24272E)
private val Color33 = androidx.compose.ui.graphics.Color(0xFF33373F)

@Composable
private fun Detalle(nombre: String, valor: String, color: androidx.compose.ui.graphics.Color = C.Claro, monoValor: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(nombre, style = cuerpo(11.sp, color = C.Suave), modifier = Modifier.weight(1f))
        Text(valor, style = if (monoValor) mono(11.sp, color = color) else cuerpo(11.sp, color = color))
    }
}

@Composable
private fun Aviso(ui: UiEstado, bajarNuevas: () -> Unit, verDescarga: () -> Unit) {
    val c = ui.cola
    val nuevas = ui.nuevas
    if (!c.ocupada && (!ui.conectado || nuevas.isEmpty())) return
    val ocupada = c.ocupada
    Tarjeta(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        fondo = C.AmbarFondo, borde = C.AmbarBorde,
        relleno = PaddingValues(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        alPulsar = if (ocupada) verDescarga else { { bajarNuevas(); verDescarga() } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Onda(floatArrayOf(0.3f, 0.75f, 0.45f, 1f, 0.6f, 0.85f, 0.4f), if (ocupada) c.progreso else 1f, Modifier.size(width = 34.dp, height = 30.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (ocupada) {
                    Text("Bajando ${c.pendientes.size + 1} · ${(c.progreso * 100).toInt()} %", style = cuerpo(15.sp, 600))
                    Text(c.segundosRestantes?.let { "quedan ~${duracionCorta(it)}" } ?: "calculando…", style = cuerpo(12.sp, color = C.AmbarSuave))
                } else {
                    val seg = nuevas.sumOf { it.segundos }
                    val bytes = nuevas.sumOf { it.bytesEnvio }
                    Text("${nuevas.size} ${if (nuevas.size == 1) "nueva" else "nuevas"}", style = cuerpo(15.sp, 600), maxLines = 1)
                    Row {
                        Text(duracion(seg), style = mono(12.sp, color = C.AmbarSuave))
                        Text(" · ~${duracionCorta(bytes / 45_000)}", style = cuerpo(12.sp, color = C.AmbarSuave), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(C.Ambar).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (ocupada) "Ver" else "Bajar", style = cuerpo(14.sp, 600, C.AmbarTinta), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun Fila(g: Grabacion, ui: UiEstado, alPulsar: () -> Unit) {
    Tarjeta(Modifier.fillMaxWidth(), radio = 20.dp, borde = C.LineaSuave, relleno = PaddingValues(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), alPulsar = alPulsar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BloqueFecha(g.fecha?.dayOfMonth?.let { "%02d".format(it) } ?: "--", g.fecha?.format(MES)?.trimEnd('.') ?: "", g.estado == Estado.NUEVO)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tituloDe(g), style = cuerpo(15.sp, 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row {
                    Text("${g.fecha?.format(HORA) ?: ""} · ", style = cuerpo(12.sp, color = C.Suave))
                    Text(duracion(g.segundos), style = mono(12.sp, color = C.Suave))
                    val extra = when {
                        g.estado == Estado.NUEVO || g.estado == Estado.EN_COLA -> megas(g.bytesEnvio)
                        g.meta.momentos.isNotEmpty() -> "${g.meta.momentos.size} ★"
                        else -> null
                    }
                    if (extra != null) Text(" · $extra", style = cuerpo(12.sp, color = C.Suave))
                }
                if (g.estado == Estado.BAJANDO) BarraFina(ui.cola.progreso, C.Ambar, Modifier.padding(top = 4.dp))
            }
            when (g.estado) {
                Estado.NUEVO -> Chip("Nuevo", C.AmbarChip, C.Ambar)
                Estado.EN_COLA -> Chip("En cola", C.Elevada, C.Claro)
                Estado.BAJANDO -> Chip("${(ui.cola.progreso * 100).toInt()} %", C.AmbarChip, C.Ambar)
                Estado.TELEFONO -> Chip("En el teléfono", C.Elevada, C.Claro)
                Estado.TRANSCRIBIENDO -> Chip(
                    if (ui.trans.actual == g.nombre) "Transcribiendo ${(ui.trans.progreso * 100).toInt()} %" else "Por transcribir",
                    C.AmbarChip, C.Ambar,
                )
                Estado.TRANSCRITA -> Chip("Transcrita", C.VerdeChip, C.VerdeTexto)
            }
        }
    }
}

private val RojoFondo = androidx.compose.ui.graphics.Color(0xFF2A1414)
private val RojoBorde = androidx.compose.ui.graphics.Color(0xFF5A2222)

/** Grabar cuando está parado; Detener (con el tiempo que lleva) mientras graba. */
@Composable
private fun BotonGrabar(ui: UiEstado, alPulsar: () -> Unit) {
    val grabando = ui.aparato?.grabando == true
    val activo = ui.conectado && !ui.cola.ocupada && !ui.grabandoAccion
    var ahora by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(grabando) { while (grabando) { ahora = LocalDateTime.now(); kotlinx.coroutines.delay(1_000) } }
    val forma = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(forma)
            .background(if (grabando) RojoFondo else if (activo) C.Ambar else C.Ambar.copy(alpha = 0.35f))
            .then(if (grabando) Modifier.border(1.dp, RojoBorde, forma) else Modifier)
            .clickable(enabled = activo, role = Role.Button, onClick = alPulsar),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (grabando) C.Rojo else C.AmbarTinta), contentAlignment = Alignment.Center) {
            if (grabando) Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(C.Texto))
            else Box(Modifier.size(11.dp).clip(CircleShape).background(C.Rojo))
        }
        if (grabando) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (ui.grabandoAccion) "Deteniendo…" else "Detener", style = titulo(18.sp, 700, C.Texto), textAlign = TextAlign.Center)
                val desde = ui.aparato?.grabandoDesde
                if (desde != null) Text(duracion(Duration.between(desde, ahora).seconds.coerceAtLeast(0)), style = mono(12.sp, color = C.Rojo))
            }
        } else {
            Text(if (ui.grabandoAccion) "Iniciando…" else "Grabar", style = titulo(20.sp, 700, C.AmbarTinta), textAlign = TextAlign.Center)
        }
    }
}
