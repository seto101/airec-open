package ec.paul.grabador.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.paul.grabador.audio.OndaAudio
import ec.paul.grabador.audio.Reproductor
import ec.paul.grabador.datos.Biblioteca
import ec.paul.grabador.ui.Estado
import ec.paul.grabador.ui.Grabacion
import ec.paul.grabador.ui.UiEstado
import ec.paul.grabador.ui.componentes.BarraFina
import ec.paul.grabador.ui.componentes.Boton
import ec.paul.grabador.ui.componentes.BotonIcono
import ec.paul.grabador.ui.componentes.Chip
import ec.paul.grabador.ui.componentes.Ficha
import ec.paul.grabador.ui.componentes.Onda
import ec.paul.grabador.ui.componentes.Tarjeta
import ec.paul.grabador.ui.componentes.duracion
import ec.paul.grabador.ui.componentes.megas
import ec.paul.grabador.ui.tema.C
import ec.paul.grabador.ui.tema.cuerpo
import ec.paul.grabador.ui.tema.mono
import ec.paul.grabador.ui.tema.titulo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

private val FECHA_LARGA = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", ES)
private val HORA = DateTimeFormatter.ofPattern("HH:mm", ES)

data class AccionesGrabacion(
    val volver: () -> Unit,
    val bajar: (String) -> Unit,
    val marcar: (String, Long) -> Unit,
    val quitarMomento: (String, Long) -> Unit,
    val notas: (String, String) -> Unit,
    val renombrar: (String, String) -> Unit,
    val copiarParaIa: (Grabacion, String) -> Unit,
    val transcribir: (String) -> Unit,
    val borrarDelGrabador: (String) -> Unit,
    val cancelarTranscripcion: () -> Unit,
    val compartirMd: (Grabacion, String) -> Unit,
    val compartirAudio: (Grabacion) -> Unit,
)

@Composable
fun PantallaGrabacion(g: Grabacion, ui: UiEstado, biblioteca: Biblioteca, a: AccionesGrabacion) {
    val alcance = rememberCoroutineScope()
    val rep = remember(g.nombre) { Reproductor(alcance) }
    val r by rep.estado.collectAsState()
    val local = g.estado == Estado.TELEFONO || g.estado == Estado.TRANSCRITA || g.estado == Estado.TRANSCRIBIENDO
    var picos by remember(g.nombre) { mutableStateOf<FloatArray?>(null) }
    var pestana by remember { mutableStateOf(0) }
    var renombrando by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf(false) }
    val titulo = tituloDe(g)

    DisposableEffect(g.nombre, local) {
        if (local) rep.abrir(biblioteca.audio(g.nombre))
        onDispose { rep.liberar() }
    }
    LaunchedEffect(g.nombre, local) {
        if (local) picos = withContext(Dispatchers.Default) {
            runCatching { OndaAudio.obtener(biblioteca.audio(g.nombre), biblioteca.archivoOnda(g.nombre)) }.getOrNull()
        }
    }

    val t = g.transcripcion
    val actual = if (t != null && (r.sonando || r.posMs > 0)) t.frases.indexOfLast { it.inicioMs <= r.posMs } else -1
    val lista = rememberLazyListState()
    // Seguir la lectura: la frase que suena sube sola. Si el usuario desplaza la lista, se deja de seguir.
    var seguir by remember { mutableStateOf(true) }
    LaunchedEffect(lista) {
        lista.interactionSource.interactions.collect { if (it is DragInteraction.Start) seguir = false }
    }
    LaunchedEffect(actual, seguir, pestana) {
        if (seguir && r.sonando && pestana == 0 && actual >= 0) {
            // Item 0 es la cabecera; la frase i es el item i + 1. Se deja la anterior arriba como contexto.
            lista.animateScrollToItem(1 + maxOf(0, actual - 1))
        }
    }
    val enLaLista by remember { derivedStateOf { lista.firstVisibleItemIndex > 0 } }
    val alternar = { rep.alternar(); seguir = true }

    Column(Modifier.fillMaxSize().background(C.Fondo).statusBarsPadding()) {
        AnimatedVisibility(local && enLaLista) {
            MiniReproductor(r.sonando, r.posMs, r.durMs, tituloDe(g), alternar)
        }
        LazyColumn(Modifier.weight(1f), state = lista, contentPadding = PaddingValues(horizontal = 16.dp)) {
          item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonIcono(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", a.volver)
                Spacer(Modifier.weight(1f))
                when (g.estado) {
                    Estado.TRANSCRITA -> Chip(if (t?.precisa == true) "Transcrita · precisa" else "Transcrita · rápida", C.VerdeChip, C.VerdeTexto)
                    Estado.TELEFONO -> Chip("En el teléfono", C.Elevada, C.Claro)
                    Estado.TRANSCRIBIENDO -> Chip("Transcribiendo", C.AmbarChip, C.Ambar)
                    else -> Chip("En el grabador", C.AmbarChip, C.Ambar)
                }
                Spacer(Modifier.width(8.dp))
                if (local && g.enGrabador) {
                    BotonIcono(Icons.Rounded.DeleteOutline, "Borrar del grabador", { borrando = true }, tinta = C.Rojo)
                    Spacer(Modifier.width(8.dp))
                }
                if (local) BotonIcono(Icons.Rounded.Edit, "Cambiar el título", { renombrando = true })
            }

            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, style = titulo(24.sp))
                val fin = g.fecha?.plusSeconds(g.segundos)
                Text(
                    listOfNotNull(g.fecha?.format(FECHA_LARGA)?.replaceFirstChar { it.titlecase(ES) }, fin?.let { "– ${it.format(HORA)}" }).joinToString(" "),
                    style = cuerpo(13.sp, color = C.Suave),
                )
            }

            Spacer(Modifier.height(14.dp))
            if (local) {
                Tarjeta(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val prog = if (r.durMs > 0) r.posMs.toFloat() / r.durMs else 0f
                        val marcas = if (r.durMs > 0) g.meta.momentos.map { it.toFloat() / r.durMs } else emptyList()
                        Onda(
                            picos ?: FloatArray(72) { 0.15f }, prog, Modifier.fillMaxWidth().height(56.dp), marcas,
                            alSaltar = { f -> rep.ir((f * r.durMs).toLong()) },
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(duracion(r.posMs / 1000), style = mono(11.sp, color = C.Suave), modifier = Modifier.weight(1f))
                            Text("−" + duracion(((r.durMs - r.posMs) / 1000).coerceAtLeast(0)), style = mono(11.sp, color = C.Suave))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (r.listo) C.Ambar else C.Ambar.copy(alpha = 0.4f))
                                    .clickable(role = Role.Button) { alternar() }
                                    .semantics { contentDescription = if (r.sonando) "Pausar" else "Reproducir" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (r.sonando) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = C.AmbarTinta, modifier = Modifier.size(26.dp)) }
                            BotonTexto("−15", "Retroceder 15 segundos") { rep.mover(-15_000) }
                            BotonTexto("+15", "Adelantar 15 segundos") { rep.mover(15_000) }
                            Spacer(Modifier.weight(1f))
                            val vel = when (r.velocidad) { 1.25f -> "1,25×"; 1.5f -> "1,5×"; 2f -> "2×"; else -> "1×" }
                            BotonTexto(vel, "Velocidad de reproducción") { rep.siguienteVelocidad() }
                            BotonIcono(Icons.Rounded.Bookmark, "Marcar momento", { a.marcar(g.nombre, r.posMs) }, tinta = C.Ambar, borde = C.Borde, fondo = C.Fondo)
                        }
                    }
                }
            } else {
                Tarjeta(Modifier.fillMaxWidth(), fondo = C.AmbarFondo, borde = C.AmbarBorde) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Está en el grabador", style = cuerpo(15.sp, 600))
                        if (g.estado == Estado.BAJANDO) {
                            BarraFina(ui.cola.progreso, C.Ambar)
                            Text("Bajando… ${(ui.cola.progreso * 100).toInt()} %", style = cuerpo(12.sp, color = C.AmbarSuave))
                        } else if (g.estado == Estado.EN_COLA) {
                            Text("En cola para bajar", style = cuerpo(12.sp, color = C.AmbarSuave))
                        } else {
                            Text("Bájala para escucharla, marcar momentos y transcribirla. Unos ${megas(g.bytesEnvio)}.", style = cuerpo(12.sp, color = C.AmbarSuave))
                            Boton("Bajar ahora", { a.bajar(g.nombre) }, Modifier.fillMaxWidth(), activo = ui.conectado)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tam = biblioteca.audio(g.nombre).takeIf { it.exists() }?.length() ?: g.bytesEnvio
                Ficha("Duración", duracion(g.segundos), Modifier.weight(1f), "${megas(tam)} · Opus")
                if (t != null) Ficha("Frases", t.frases.size.toString(), Modifier.weight(1f), if (t.precisa) "precisa · PC" else "rápida")
                else Ficha("Frases", "—", Modifier.weight(1f), "al transcribir")
                Ficha("Momentos", g.meta.momentos.size.toString(), Modifier.weight(1f), "marcados")
            }

            Spacer(Modifier.height(12.dp))
            Pestanas(listOf("Transcripción", "Momentos", "Notas"), pestana) { pestana = it }
            Spacer(Modifier.height(10.dp))
          }

            if (pestana == 0 && t != null) {
                itemsIndexed(t.frases, key = { i, _ -> "f$i" }) { i, f ->
                    val sonando = i == actual
                    Tarjeta(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        fondo = if (sonando) C.AmbarFondo else C.Fila,
                        borde = if (sonando) C.AmbarBorde else C.Elevada,
                        radio = 16.dp, relleno = PaddingValues(12.dp),
                        alPulsar = if (local) ({ rep.ir(f.inicioMs) }) else null,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(duracion(f.inicioMs / 1000), style = mono(11.sp, color = if (sonando) C.Ambar else C.Suave))
                            Text(f.texto, style = cuerpo(14.sp, color = C.TextoFrase))
                        }
                    }
                }
            } else item {
            when (pestana) {
                0 -> TarjetaSinTranscripcion(g, ui, local, a)
                1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (g.meta.momentos.isEmpty()) Text("Toca el marcador mientras escuchas para guardar un momento.", style = cuerpo(13.sp, color = C.Suave), modifier = Modifier.padding(4.dp))
                    g.meta.momentos.forEachIndexed { i, ms ->
                        Tarjeta(Modifier.fillMaxWidth(), fondo = C.Fila, borde = C.Elevada, radio = 16.dp, relleno = PaddingValues(12.dp), alPulsar = { rep.ir(ms) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(C.Ambar), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", style = mono(11.sp, 600, C.AmbarTinta))
                                }
                                Text(duracion(ms / 1000), style = mono(14.sp), modifier = Modifier.weight(1f))
                                TextButton(onClick = { a.quitarMomento(g.nombre, ms) }) { Text("Quitar", style = cuerpo(13.sp, 600, C.Suave)) }
                            }
                        }
                    }
                }
                else -> CampoNotas(g.meta.notas) { a.notas(g.nombre, it) }
            }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        if (local && !seguir && r.sonando && t != null && pestana == 0) {
            Boton(
                "Seguir la lectura", { seguir = true },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                fondo = C.AmbarChip, tinta = C.Ambar, borde = C.AmbarBorde, alto = 44.dp,
            )
        }
        if (local) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Elevada))
            Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Boton("Copiar para mi IA", { a.copiarParaIa(g, titulo) }, Modifier.weight(1f), fondo = C.Texto, tinta = C.Fondo, icono = Icons.Rounded.ContentCopy)
                Boton(".md", { a.compartirMd(g, titulo) }, fondo = C.Tarjeta, tinta = C.Texto, borde = C.Borde)
                BotonIcono(Icons.Rounded.IosShare, "Compartir el audio", { a.compartirAudio(g) }, tam = 50.dp, borde = C.Borde)
            }
        }
    }

    if (borrando) {
        AlertDialog(
            onDismissRequest = { borrando = false },
            containerColor = C.Tarjeta,
            title = { Text("¿Borrar del grabador?", style = titulo(20.sp, 600)) },
            text = { Text("Se borra del grabador para liberar espacio. La copia del teléfono, su transcripción y tus notas se quedan.", style = cuerpo(14.sp, color = C.Suave)) },
            confirmButton = { TextButton(onClick = { a.borrarDelGrabador(g.nombre); borrando = false }) { Text("Borrar del grabador", color = C.Rojo) } },
            dismissButton = { TextButton(onClick = { borrando = false }) { Text("Cancelar", color = C.Suave) } },
        )
    }

    if (renombrando) {
        var texto by remember { mutableStateOf(g.meta.titulo) }
        AlertDialog(
            onDismissRequest = { renombrando = false },
            containerColor = C.Tarjeta,
            title = { Text("Título", style = titulo(20.sp, 600)) },
            text = { OutlinedTextField(texto, { texto = it }, singleLine = true, placeholder = { Text("Ej.: Reunión con proveedor") }) },
            confirmButton = { TextButton(onClick = { a.renombrar(g.nombre, texto); renombrando = false }) { Text("Guardar", color = C.Ambar) } },
            dismissButton = { TextButton(onClick = { renombrando = false }) { Text("Cancelar", color = C.Suave) } },
        )
    }
}

/** Reproductor compacto fijo arriba mientras se lee la transcripción. */
@Composable
private fun MiniReproductor(sonando: Boolean, posMs: Long, durMs: Long, titulo: String, alternar: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Fondo).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
        Tarjeta(Modifier.fillMaxWidth(), radio = 18.dp, relleno = PaddingValues(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Ambar)
                        .clickable(role = Role.Button, onClick = alternar)
                        .semantics { contentDescription = if (sonando) "Pausar" else "Reproducir" },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (sonando) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = C.AmbarTinta, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(titulo, style = cuerpo(13.sp, 600), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(duracion(posMs / 1000), style = mono(12.sp, color = C.Suave))
                    }
                    BarraFina(if (durMs > 0) posMs.toFloat() / durMs else 0f, C.Ambar)
                }
            }
        }
    }
}

@Composable
private fun BotonTexto(texto: String, descripcion: String, alPulsar: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.Borde, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = alPulsar).semantics { contentDescription = descripcion }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, style = mono(12.sp), textAlign = TextAlign.Center) }
}

@Composable
fun Pestanas(nombres: List<String>, sel: Int, elegir: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Tarjeta).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        nombres.forEachIndexed { i, n ->
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(if (i == sel) C.Elevada else C.Tarjeta)
                    .clickable(role = Role.Tab) { elegir(i) },
                contentAlignment = Alignment.Center,
            ) { Text(n, style = cuerpo(13.sp, if (i == sel) 600 else 400, if (i == sel) C.Texto else C.Suave), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun CampoNotas(inicial: String, guardar: (String) -> Unit) {
    var texto by remember { mutableStateOf(inicial) }
    LaunchedEffect(texto) { if (texto != inicial) { delay(600); guardar(texto) } }
    Tarjeta(Modifier.fillMaxWidth(), fondo = C.Fila, borde = C.Elevada) {
        BasicTextField(
            texto, { texto = it },
            textStyle = cuerpo(15.sp, color = C.TextoFrase),
            cursorBrush = SolidColor(C.Ambar),
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            decorationBox = { campo ->
                if (texto.isEmpty()) Text("Escribe aquí tus notas de esta reunión…", style = cuerpo(15.sp, color = C.Tenue))
                campo()
            },
        )
    }
}

@Composable
private fun TarjetaSinTranscripcion(g: Grabacion, ui: UiEstado, local: Boolean, a: AccionesGrabacion) {
    val tr = ui.trans
    val esta = tr.actual == g.nombre
    val enCola = g.nombre in tr.pendientes
    Tarjeta(Modifier.fillMaxWidth(), fondo = if (esta || enCola) C.AmbarFondo else C.Fila, borde = if (esta || enCola) C.AmbarBorde else C.Elevada) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                !local -> {
                    Text("Aún sin transcripción", style = cuerpo(15.sp, 600))
                    Text("Bájala primero; al terminar se transcribe sola en el teléfono.", style = cuerpo(13.sp, color = C.Suave))
                }
                esta && tr.bajandoModelo != null -> {
                    Text("Bajando el modelo de transcripción", style = cuerpo(15.sp, 600))
                    BarraFina(tr.bajandoModelo, C.Ambar)
                    Text("Solo la primera vez · ${(tr.bajandoModelo * 100).toInt()} %", style = cuerpo(12.sp, color = C.AmbarSuave))
                }
                esta && tr.esperandoCarga -> {
                    Text("Esperando el cargador", style = cuerpo(15.sp, 600))
                    Text("Elegiste transcribir solo con el teléfono cargando.", style = cuerpo(13.sp, color = C.AmbarSuave))
                }
                esta -> {
                    Text("Transcribiendo en el teléfono", style = cuerpo(15.sp, 600))
                    BarraFina(tr.progreso, C.Ambar)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${(tr.progreso * 100).toInt()} % · puedes bloquear el teléfono", style = cuerpo(12.sp, color = C.AmbarSuave), modifier = Modifier.weight(1f))
                        TextButton(onClick = a.cancelarTranscripcion) { Text("Cancelar", style = cuerpo(13.sp, 600, C.Suave)) }
                    }
                }
                enCola -> {
                    Text("En cola para transcribir", style = cuerpo(15.sp, 600))
                    Text("Empieza cuando termine la que está en curso.", style = cuerpo(13.sp, color = C.AmbarSuave))
                }
                else -> {
                    Text("Aún sin transcripción", style = cuerpo(15.sp, 600))
                    Text("Se hace en el teléfono, sin internet. Una hora de audio tarda unos 45 minutos con el modelo rápido.", style = cuerpo(13.sp, color = C.Suave))
                    Boton("Transcribir en el teléfono", { a.transcribir(g.nombre) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
