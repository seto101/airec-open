package ec.paul.grabador.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.paul.grabador.ui.tema.C
import ec.paul.grabador.ui.tema.cuerpo
import ec.paul.grabador.ui.tema.mono

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    fondo: Color = C.Tarjeta,
    borde: Color = C.Linea,
    radio: Dp = 22.dp,
    relleno: PaddingValues = PaddingValues(14.dp),
    alPulsar: (() -> Unit)? = null,
    contenido: @Composable () -> Unit,
) {
    val forma = RoundedCornerShape(radio)
    Box(
        modifier
            .clip(forma)
            .background(fondo)
            .border(1.dp, borde, forma)
            .then(if (alPulsar != null) Modifier.clickable(role = Role.Button, onClick = alPulsar) else Modifier)
            .padding(relleno),
    ) { contenido() }
}

/** Botón con el texto SIEMPRE centrado (pedido de Paúl). */
@Composable
fun Boton(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    fondo: Color = C.Ambar,
    tinta: Color = C.AmbarTinta,
    borde: Color? = null,
    icono: ImageVector? = null,
    alto: Dp = 50.dp,
    radio: Dp = 16.dp,
    activo: Boolean = true,
) {
    val forma = RoundedCornerShape(radio)
    Row(
        modifier
            .height(alto)
            .clip(forma)
            .background(if (activo) fondo else fondo.copy(alpha = 0.35f))
            .then(if (borde != null) Modifier.border(1.dp, borde, forma) else Modifier)
            .clickable(enabled = activo, role = Role.Button, onClick = alPulsar)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) Icon(icono, contentDescription = null, tint = tinta, modifier = Modifier.size(18.dp))
        Text(texto, style = cuerpo(15.sp, 600, tinta), textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun BotonSecundario(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null, activo: Boolean = true) =
    Boton(texto, alPulsar, modifier, fondo = C.Tarjeta, tinta = C.Texto, borde = C.Borde, icono = icono, activo = activo)

@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, alPulsar: () -> Unit, tinta: Color = C.Texto, tam: Dp = 44.dp, fondo: Color = C.Tarjeta, borde: Color = C.Linea) {
    val forma = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(tam)
            .clip(forma)
            .background(fondo)
            .border(1.dp, borde, forma)
            .clickable(role = Role.Button, onClick = alPulsar)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) { Icon(icono, contentDescription = null, tint = tinta, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Chip(texto: String, fondo: Color, tinta: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = cuerpo(11.sp, 600, tinta),
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(fondo).padding(horizontal = 9.dp, vertical = 5.dp),
        maxLines = 1,
    )
}

@Composable
fun Punto(color: Color, tam: Dp = 7.dp) = Box(Modifier.size(tam).clip(CircleShape).background(color))

@Composable
fun Ficha(nombre: String, valor: String, modifier: Modifier = Modifier, detalle: String? = null, fondo: Color = C.Tarjeta, borde: Color = C.LineaSuave) {
    Tarjeta(modifier, fondo = fondo, borde = borde, radio = 16.dp, relleno = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(nombre, style = cuerpo(11.sp, color = C.Suave))
            Text(valor, style = mono(17.sp, 600), maxLines = 1)
            if (detalle != null) Text(detalle, style = cuerpo(10.sp, color = C.Tenue), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Barra por segmentos (batería, progreso). [parcial]: color del segmento a medias. */
@Composable
fun BarraSegmentos(total: Int, encendidos: Float, color: Color, modifier: Modifier = Modifier, alto: Dp = 8.dp, apagado: Color = C.Pista, parcial: Color? = null) {
    Canvas(modifier.fillMaxWidth().height(alto)) {
        val hueco = 3.dp.toPx()
        val ancho = (size.width - hueco * (total - 1)) / total
        val llenos = encendidos.toInt()
        for (i in 0 until total) {
            val c = when {
                i < llenos -> color
                i == llenos && parcial != null && encendidos > llenos -> parcial
                else -> apagado
            }
            drawRoundRect(c, Offset(i * (ancho + hueco), 0f), Size(ancho, size.height), CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable
fun BarraFina(fraccion: Float, color: Color, modifier: Modifier = Modifier, pista: Color = C.Pista) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(pista)) {
        Box(Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

/** Forma de onda con lo reproducido en ámbar, marcas arriba y toque para saltar. */
@Composable
fun Onda(
    picos: FloatArray,
    progreso: Float,
    modifier: Modifier = Modifier,
    marcas: List<Float> = emptyList(),
    color: Color = C.Ambar,
    alSaltar: ((Float) -> Unit)? = null,
) {
    Canvas(
        modifier.then(
            if (alSaltar != null) Modifier.pointerInput(Unit) { detectTapGestures { alSaltar((it.x / size.width).coerceIn(0f, 1f)) } }
            else Modifier,
        ),
    ) {
        val n = picos.size.coerceAtLeast(1)
        val hueco = 2.dp.toPx()
        val ancho = ((size.width - hueco * (n - 1)) / n).coerceAtLeast(1f)
        val arriba = 8.dp.toPx()
        val altoUtil = size.height - arriba
        for (i in 0 until n) {
            val h = (4.dp.toPx() + (picos.getOrElse(i) { 0.2f }.coerceIn(0f, 1f)) * (altoUtil - 4.dp.toPx()))
            val x = i * (ancho + hueco)
            val c = if ((i + 0.5f) / n <= progreso) color else C.PistaOnda
            drawRoundRect(c, Offset(x, arriba + (altoUtil - h) / 2), Size(ancho, h), CornerRadius(2.dp.toPx()))
        }
        marcas.forEach { m -> drawCircle(color, 3.dp.toPx(), Offset(m * size.width, 3.dp.toPx())) }
    }
}

@Composable
fun BloqueFecha(dia: String, mes: String, nuevo: Boolean) {
    Column(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (nuevo) C.AmbarChip else C.Elevada),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(dia, style = mono(18.sp, 600, if (nuevo) C.Ambar else C.Texto))
        Text(mes.uppercase(), style = cuerpo(10.sp, 600, if (nuevo) C.Ambar else C.Texto))
    }
}

@Composable
fun Seccion(texto: String, modifier: Modifier = Modifier, accion: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = ec.paul.grabador.ui.tema.titulo(17.sp, 600), modifier = Modifier.weight(1f))
        accion?.invoke(this)
    }
}

fun duracion(seg: Long): String = "%d:%02d:%02d".format(seg / 3600, seg % 3600 / 60, seg % 60)
fun duracionCorta(seg: Long): String = if (seg >= 3600) "%d h %02d min".format(seg / 3600, seg % 3600 / 60) else "%d min".format((seg + 59) / 60)
fun megas(b: Long): String = if (b >= 1_000_000) "%.1f MB".format(b / 1_000_000.0).replace('.', ',') else "%d KB".format(b / 1000)
