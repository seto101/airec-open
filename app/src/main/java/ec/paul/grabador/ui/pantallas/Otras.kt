package ec.paul.grabador.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.paul.grabador.ui.UiEstado
import ec.paul.grabador.ui.componentes.BarraSegmentos
import ec.paul.grabador.ui.componentes.Boton
import ec.paul.grabador.ui.componentes.BotonIcono
import ec.paul.grabador.ui.componentes.BotonSecundario
import ec.paul.grabador.ui.componentes.Ficha
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
import java.time.format.DateTimeFormatter

private val DIA_HORA = DateTimeFormatter.ofPattern("d MMM · HH:mm", ES)

@Composable
private fun Cabecera(texto: String, volver: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (volver != null) BotonIcono(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", volver)
        Text(texto, style = titulo(if (volver != null) 18.sp else 30.sp, if (volver != null) 600 else 700))
    }
}

@Composable
fun PantallaDescarga(ui: UiEstado, volver: () -> Unit, cancelar: () -> Unit) {
    val c = ui.cola
    val porNombre = ui.grabaciones.associateBy { it.nombre }
    Column(Modifier.fillMaxSize().background(C.Fondo).statusBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Cabecera("Bajando del grabador", volver)
            Spacer(Modifier.height(16.dp))
            val actual = c.actual?.let { porNombre[it] }
            if (actual == null) {
                Tarjeta(Modifier.fillMaxWidth(), radio = 24.dp, relleno = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (c.error != null) "Se detuvo" else "Todo al día", style = titulo(18.sp, 600))
                        Text(c.error ?: "No hay nada bajando. Lo bajado está en Grabaciones.", style = cuerpo(13.sp, color = C.Suave))
                    }
                }
            } else {
                Tarjeta(Modifier.fillMaxWidth(), radio = 24.dp, relleno = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("1 de ${c.pendientes.size + 1} · por Bluetooth", style = cuerpo(12.sp, color = C.Suave))
                                Text(tituloDe(actual), style = cuerpo(16.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row {
                                    Text("${actual.fecha?.format(DIA_HORA) ?: ""} · ", style = cuerpo(12.sp, color = C.Suave))
                                    Text(duracion(actual.segundos), style = mono(12.sp, color = C.Suave))
                                }
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${(c.progreso * 100).toInt()}", style = mono(40.sp, 600, C.Ambar))
                                Text("%", style = mono(18.sp, 600, C.Ambar), modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                        BarraSegmentos(20, c.progreso * 20, C.Ambar, alto = 18.dp, apagado = Color24, parcial = C.AmbarMedio)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ficha("Velocidad", if (c.velocidad > 0) "${c.velocidad / 1000} KB/s" else "—", Modifier.weight(1f), fondo = C.Elevada, borde = C.Elevada)
                            Ficha("Restante", c.segundosRestantes?.let { "~" + duracionCorta(it) } ?: "—", Modifier.weight(1f), fondo = C.Elevada, borde = C.Elevada)
                            Ficha("Recibido", megas(c.recibidos), Modifier.weight(1f), fondo = C.Elevada, borde = C.Elevada)
                        }
                        Text("Puedes bloquear el teléfono. Si se corta, sigue desde donde quedó.", style = cuerpo(12.sp, color = C.Suave))
                    }
                }
            }

            if (c.pendientes.isNotEmpty()) {
                Seccion("En cola")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.pendientes.forEachIndexed { i, n ->
                        val g = porNombre[n]
                        Tarjeta(Modifier.fillMaxWidth(), fondo = C.Fila, borde = C.Elevada, radio = 16.dp, relleno = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(C.Elevada), contentAlignment = Alignment.Center) {
                                    Text("${i + 2}", style = mono(12.sp, color = C.Claro))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(g?.let { tituloDe(it) } ?: n, style = cuerpo(14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(g?.fecha?.format(DIA_HORA), g?.let { megas(it.bytesEnvio) }).joinToString(" · "), style = cuerpo(11.sp, color = C.Suave))
                                }
                                Text(g?.let { duracion(it.segundos) } ?: "", style = mono(12.sp, color = C.Suave))
                            }
                        }
                    }
                }
            }

            Seccion("Transcripción")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TarjetaTranscripcion("Teléfono", "Rápida", "Whisper pequeño · sin internet · al terminar cada descarga", C.Gris, Modifier.weight(1f))
                TarjetaTranscripcion("PC", "Precisa", "Modelo grande · la mejora cuando la PC esté encendida", C.Gris, Modifier.weight(1f))
            }
            Text("Llega en la siguiente versión.", style = cuerpo(12.sp, color = C.Tenue), modifier = Modifier.padding(6.dp))
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSecundario(if (c.ocupada) "Detener" else "Cerrar", if (c.ocupada) cancelar else volver, Modifier.weight(1f))
            Boton("Seguir en segundo plano", volver, Modifier.weight(1f))
        }
    }
}

private val Color24 = androidx.compose.ui.graphics.Color(0xFF24272E)

@Composable
private fun TarjetaTranscripcion(donde: String, nombre: String, detalle: String, punto: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Tarjeta(modifier, radio = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(donde, style = cuerpo(12.sp, color = C.Suave), modifier = Modifier.weight(1f))
                Punto(punto, 8.dp)
            }
            Text(nombre, style = cuerpo(15.sp, 600))
            Text(detalle, style = cuerpo(11.sp, color = C.Suave))
        }
    }
}

@Composable
fun PantallaGrabador(
    ui: UiEstado,
    conectar: () -> Unit,
    actualizar: () -> Unit,
    desconectar: () -> Unit,
    alEncender: Boolean?,
    ponerAlEncender: (Boolean) -> Unit,
    bajarSola: Boolean,
    ponerBajarSola: (Boolean) -> Unit,
) {
    val a = ui.aparato
    Column(Modifier.fillMaxSize().background(C.Fondo).statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
        Cabecera("Grabador", null)
        Spacer(Modifier.height(16.dp))
        Tarjeta(Modifier.fillMaxWidth(), radio = 24.dp, relleno = PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Punto(if (ui.conectado) C.Verde else C.Gris, 9.dp)
                    Text(if (ui.conectado) "Conectado" else "Desconectado", style = cuerpo(15.sp, 600))
                }
                Fila("Modelo", "AIREC · Actions ATS3085")
                Fila("Número de serie", a?.serie?.ifBlank { null } ?: "—", true)
                Fila("Firmware", a?.version?.ifBlank { null } ?: "—", true)
                Fila("Batería", a?.bateria?.let { "$it %" } ?: "—", true)
                Fila("Espacio libre", a?.libreMb?.let { "%.1f GB".format(ES, it / 1024.0) } ?: "—", true)
                Fila("Capacidad", a?.totalMb?.let { "%.1f GB".format(ES, it / 1024.0) } ?: "—", true)
                Fila("Grabando", when (a?.grabando) { true -> "sí"; false -> "no"; null -> "—" })
                Fila("Última lista", ui.sincronizado?.let { haceCuanto(it) } ?: "—")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ui.conectado) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Actualizar", actualizar, Modifier.weight(1f))
                BotonSecundario("Desconectar", desconectar, Modifier.weight(1f))
            }
        } else {
            Boton(if (ui.conectando) "Conectando…" else "Conectar", conectar, Modifier.fillMaxWidth(), activo = !ui.conectando)
        }
        Text(
            "Al conectar, la app pone el reloj del grabador en hora, para que cada grabación se llame con su fecha real.",
            style = cuerpo(12.sp, color = C.Tenue), modifier = Modifier.padding(6.dp),
        )

        Seccion("Ajustes del grabador")
        Tarjeta(Modifier.fillMaxWidth(), radio = 24.dp, relleno = PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InterruptorActivo(
                    "Grabar al encender",
                    if (alEncender == null) "El grabador no deja leer este ajuste: al tocarlo se le manda el valor elegido."
                    else "Último valor enviado desde la app.",
                    alEncender ?: false, ui.conectado && !ui.cola.ocupada, ponerAlEncender,
                )
                InterruptorActivo(
                    "Bajar sola lo nuevo al conectar",
                    "Solo lo que se grabe después de activarlo; lo antiguo se baja a mano.",
                    bajarSola, true, ponerBajarSola,
                )
            }
        }
    }
}

@Composable
private fun Fila(nombre: String, valor: String, monoValor: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(nombre, style = cuerpo(13.sp, color = C.Suave), modifier = Modifier.weight(1f))
        Text(valor, style = if (monoValor) mono(13.sp) else cuerpo(13.sp))
    }
}

@Composable
fun PantallaAjustes(
    carpeta: String,
    version: String,
    tr: ec.paul.grabador.transcripcion.ColaTranscripcion,
    @Suppress("UNUSED_PARAMETER") versionAjustes: Int, // al cambiar, se vuelve a pintar con los ajustes nuevos
    elegirModelo: (ec.paul.grabador.transcripcion.Modelo) -> Unit,
    automatica: (Boolean) -> Unit,
    soloCargando: (Boolean) -> Unit,
) {
    val aj = tr.ajustes
    Column(Modifier.fillMaxSize().background(C.Fondo).statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
        Cabecera("Ajustes", null)
        Seccion("Transcripción en el teléfono")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ec.paul.grabador.transcripcion.Modelo.entries.forEach { m ->
                val elegido = aj.modelo == m
                Tarjeta(
                    Modifier.fillMaxWidth(),
                    fondo = if (elegido) C.AmbarFondo else C.Tarjeta, borde = if (elegido) C.AmbarBorde else C.Linea,
                    alPulsar = { elegirModelo(m) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Punto(if (elegido) C.Ambar else C.Gris, 10.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(m.nombre, style = cuerpo(15.sp, 600))
                            Text(m.detalle, style = cuerpo(12.sp, color = C.Suave))
                        }
                        Text(if (tr.modelos.disponible(m)) "Descargado" else "Se baja al usarlo", style = cuerpo(11.sp, color = if (tr.modelos.disponible(m)) C.VerdeTexto else C.Tenue))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Tarjeta(Modifier.fillMaxWidth(), radio = 24.dp, relleno = PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Interruptor("Transcribir sola al bajar", "Cada grabación nueva se transcribe al terminar de bajarla.", aj.automatica, automatica)
                Interruptor("Solo con el teléfono cargando", "Recomendado con el modelo preciso: deja las largas para la noche.", aj.soloCargando, soloCargando)
            }
        }
        Text(
            "La transcripción de la PC (pc/transcribir.py) es la más precisa y, si la usas, reemplaza la del teléfono.",
            style = cuerpo(12.sp, color = C.Tenue), modifier = Modifier.padding(6.dp),
        )

        Seccion("App")
        Tarjeta(Modifier.fillMaxWidth(), radio = 24.dp, relleno = PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Fila("Versión", version, true)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Carpeta de grabaciones", style = cuerpo(13.sp, color = C.Suave))
                    Text(carpeta, style = mono(11.sp, color = C.Claro))
                }
                Fila("Formato", "Ogg Opus · 16 kHz mono")
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun Interruptor(titulo: String, detalle: String, valor: Boolean, cambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = cuerpo(14.sp, 600))
            Text(detalle, style = cuerpo(12.sp, color = C.Suave))
        }
        androidx.compose.material3.Switch(
            checked = valor, onCheckedChange = cambiar,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = C.AmbarTinta, checkedTrackColor = C.Ambar,
                uncheckedThumbColor = C.Suave, uncheckedTrackColor = C.Elevada, uncheckedBorderColor = C.Borde,
            ),
        )
    }
}

@Composable
private fun InterruptorActivo(titulo: String, detalle: String, valor: Boolean, activo: Boolean, cambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = cuerpo(14.sp, 600, if (activo) C.Texto else C.Tenue))
            Text(detalle, style = cuerpo(12.sp, color = C.Suave))
        }
        androidx.compose.material3.Switch(
            checked = valor, onCheckedChange = cambiar, enabled = activo,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = C.AmbarTinta, checkedTrackColor = C.Ambar,
                uncheckedThumbColor = C.Suave, uncheckedTrackColor = C.Elevada, uncheckedBorderColor = C.Borde,
            ),
        )
    }
}
