package ec.paul.grabador

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ec.paul.grabador.datos.Exportar
import ec.paul.grabador.ui.Grabacion
import ec.paul.grabador.ui.GrabadorViewModel
import ec.paul.grabador.ui.componentes.Tarjeta
import ec.paul.grabador.ui.pantallas.AccionesGrabacion
import ec.paul.grabador.ui.pantallas.PantallaAjustes
import ec.paul.grabador.ui.pantallas.PantallaDescarga
import ec.paul.grabador.ui.pantallas.PantallaGrabacion
import ec.paul.grabador.ui.pantallas.PantallaGrabador
import ec.paul.grabador.ui.pantallas.PantallaInicio
import ec.paul.grabador.ui.tema.C
import ec.paul.grabador.ui.tema.TemaGrabador
import ec.paul.grabador.ui.tema.cuerpo
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: GrabadorViewModel by viewModels()
    private val permisos = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
    private val pedirPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.BLUETOOTH_CONNECT] == true && r[Manifest.permission.BLUETOOTH_SCAN] == true) vm.conectar()
    }
    private val encenderBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) vm.conectar()
    }

    private fun conectar() {
        if (permisos.take(2).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) vm.conectar()
        else pedirPermisos.launch(permisos)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TemaGrabador {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val pedirBt by vm.pedirEncenderBluetooth.collectAsStateWithLifecycle()
                LaunchedEffect(pedirBt) {
                    if (pedirBt) {
                        vm.pedirEncenderBluetooth.value = false
                        encenderBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                }
                var pestana by rememberSaveable { mutableStateOf(0) }
                var abierta by rememberSaveable { mutableStateOf<String?>(null) }
                var descarga by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = abierta != null || descarga || pestana != 0) {
                    when {
                        descarga -> descarga = false
                        abierta != null -> abierta = null
                        else -> pestana = 0
                    }
                }

                Box(Modifier.fillMaxSize().background(C.Fondo)) {
                    val g = abierta?.let { n -> ui.grabaciones.firstOrNull { it.nombre == n } }
                    when {
                        descarga -> PantallaDescarga(ui, volver = { descarga = false }, cancelar = vm::cancelarDescargas)
                        g != null -> PantallaGrabacion(g, ui, vm.biblioteca, acciones { abierta = null })
                        else -> {
                            when (pestana) {
                                0 -> PantallaInicio(
                                    ui, conectar = ::conectar, actualizar = vm::actualizar, abrir = { abierta = it },
                                    bajarNuevas = vm::descargarNuevas, verDescarga = { descarga = true },
                                    grabar = vm::grabarODetener,
                                )
                                1 -> {
                                    val v by vm.versionAjustes.collectAsStateWithLifecycle()
                                    key(v) {
                                        PantallaGrabador(
                                            ui, ::conectar, vm::actualizar, vm::desconectar,
                                            vm.grabarAlEncenderGuardado, vm::ponerGrabarAlEncender,
                                            vm.bajarSolaLoNuevo, vm::ponerBajarSola,
                                        )
                                    }
                                }
                                else -> {
                                    val v by vm.versionAjustes.collectAsStateWithLifecycle()
                                    PantallaAjustes(
                                        vm.biblioteca.carpeta.path, versionApp(), vm.transcripciones, v,
                                        vm::elegirModelo, vm::transcribirSola, vm::soloCargando,
                                    )
                                }
                            }
                            BarraNav(pestana, { pestana = it }, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                    ui.mensaje?.let { m ->
                        LaunchedEffect(m) { delay(6_000); vm.cerrarMensaje() }
                        Tarjeta(
                            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth(),
                            fondo = C.Elevada, borde = C.Borde, alPulsar = vm::cerrarMensaje,
                        ) { Text(m, style = cuerpo(14.sp)) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refrescarLocales() // por si la PC dejó transcripciones nuevas mientras tanto
    }

    private fun acciones(volver: () -> Unit) = AccionesGrabacion(
        volver = volver,
        bajar = vm::descargar,
        marcar = vm::marcarMomento,
        quitarMomento = vm::borrarMomento,
        notas = vm::guardarNotas,
        renombrar = vm::renombrar,
        transcribir = vm::transcribir,
        borrarDelGrabador = vm::borrarDelGrabador,
        cancelarTranscripcion = vm::cancelarTranscripcion,
        copiarParaIa = { g, t ->
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(t, Exportar.paraIa(Exportar.markdown(g, t, g.transcripcion?.texto(conTiempos = true)))))
            Toast.makeText(this, "Copiado: pégalo en Claude o en tu IA", Toast.LENGTH_SHORT).show()
        },
        compartirMd = { g, t ->
            val f = File(vm.biblioteca.carpeta, "${g.nombre}.md").apply { writeText(Exportar.markdown(g, t, g.transcripcion?.texto(conTiempos = true))) }
            compartir(f, "text/markdown", t)
        },
        compartirAudio = { g: Grabacion -> compartir(vm.audio(g.nombre), "audio/ogg", g.nombre) },
    )

    private fun compartir(f: File, tipo: String, titulo: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.archivos", f)
        val i = Intent(Intent.ACTION_SEND).setType(tipo).putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, titulo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, titulo))
    }

    private fun versionApp(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "—"
}

private val Linea2 = androidx.compose.ui.graphics.Color(0xFF2A2D34)

@Composable
private fun BarraNav(sel: Int, elegir: (Int) -> Unit, modifier: Modifier) {
    val forma = RoundedCornerShape(30.dp)
    Row(
        modifier.navigationBarsPadding().padding(bottom = 16.dp).shadow(18.dp, forma).clip(forma).background(C.Nav).border(1.dp, Linea2, forma).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Item(Icons.Rounded.GraphicEq, "Grabaciones", sel == 0) { elegir(0) }
        Item(Icons.Rounded.Smartphone, "Grabador", sel == 1) { elegir(1) }
        Item(Icons.Rounded.Tune, "Ajustes", sel == 2) { elegir(2) }
    }
}

@Composable
private fun Item(icono: ImageVector, nombre: String, activo: Boolean, alPulsar: () -> Unit) {
    Row(
        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (activo) C.Ambar else C.Nav)
            .clickable(role = Role.Tab, onClick = alPulsar).semantics { contentDescription = nombre }
            .padding(horizontal = if (activo) 18.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, null, tint = if (activo) C.AmbarTinta else C.Suave, modifier = Modifier.size(20.dp))
        if (activo) Text(nombre, style = cuerpo(14.sp, 600, C.AmbarTinta))
    }
}
