package ec.paul.grabador.ui.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ec.paul.grabador.R

/** Propuesta D «Estudio moderno» (lienzo de diseño del 2026-11-13). */
object C {
    val Fondo = Color(0xFF0B0C0F)
    val Tarjeta = Color(0xFF15171C)
    val Fila = Color(0xFF121418)
    val Elevada = Color(0xFF1C1F25)
    val Linea = Color(0xFF23262D)
    val LineaSuave = Color(0xFF1F2228)
    val Borde = Color(0xFF2B2F36)
    val Nav = Color(0xFF1A1C21)
    val Texto = Color(0xFFF2F2EE)
    val TextoFrase = Color(0xFFE4E5E8)
    val Suave = Color(0xFFA3A7B0)
    val Tenue = Color(0xFF8B8F98)
    val Claro = Color(0xFFC9CCD3)
    val Pista = Color(0xFF2C3037)
    val PistaOnda = Color(0xFF363A42)
    val Gris = Color(0xFF6E7480)
    val Ambar = Color(0xFFFFB547)
    val AmbarTinta = Color(0xFF1A1206)
    val AmbarFondo = Color(0xFF1E1912)
    val AmbarBorde = Color(0xFF3D2F16)
    val AmbarChip = Color(0xFF2A2111)
    val AmbarSuave = Color(0xFFD9C29A)
    val AmbarMedio = Color(0xFF7A5A22)
    val Verde = Color(0xFF5BE49B)
    val VerdeChip = Color(0xFF13281D)
    val VerdeTexto = Color(0xFF7BEAB0)
    val Rojo = Color(0xFFFF5A4E)
    val Celeste = Color(0xFF7DD3FC)
    val Lila = Color(0xFFC4B5FD)
}

// Fuentes estáticas, una por grosor (generadas de las variables con fontTools): las variables
// no tomaban el grosor en el teléfono y los números salían finos.
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_500, FontWeight(500)),
    Font(R.font.space_grotesk_600, FontWeight(600)),
    Font(R.font.space_grotesk_700, FontWeight(700)),
)
val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight(400)),
    Font(R.font.inter_500, FontWeight(500)),
    Font(R.font.inter_600, FontWeight(600)),
    Font(R.font.inter_700, FontWeight(700)),
)
val Mono = FontFamily(Font(R.font.jetbrains_mono_500, FontWeight(500)), Font(R.font.jetbrains_mono_600, FontWeight(600)))

fun titulo(tam: TextUnit, peso: Int = 700, color: Color = C.Texto) =
    TextStyle(fontFamily = SpaceGrotesk, fontSize = tam, fontWeight = FontWeight(peso), color = color, letterSpacing = (-0.02f * tam.value).sp)

fun cuerpo(tam: TextUnit, peso: Int = 400, color: Color = C.Texto) =
    TextStyle(fontFamily = Inter, fontSize = tam, fontWeight = FontWeight(peso), color = color)

fun mono(tam: TextUnit, peso: Int = 500, color: Color = C.Texto) =
    TextStyle(fontFamily = Mono, fontSize = tam, fontWeight = FontWeight(peso), color = color)

@Composable
fun TemaGrabador(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.Fondo, surface = C.Tarjeta, primary = C.Ambar, onPrimary = C.AmbarTinta,
            onBackground = C.Texto, onSurface = C.Texto, outline = C.Linea,
        ),
        content = contenido,
    )
}
