package pe.upeu.ecorecicla.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.presentation.components.IndicadorCarga
import pe.upeu.ecorecicla.presentation.detalle.DetallePuntoScreen
import pe.upeu.ecorecicla.presentation.listado.RutaScreen
import pe.upeu.ecorecicla.presentation.perfil.PerfilScreen
import pe.upeu.ecorecicla.presentation.sesion.EstadoSesion
import pe.upeu.ecorecicla.presentation.sesion.LoginScreen
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel

sealed interface Pantalla {
    data object Ruta : Pantalla
    data class Detalle(val puntoId: Long) : Pantalla
    data object Perfil : Pantalla
}

/** Intercepta el boton Atras del sistema. Cada plataforma aporta su implementacion. */
@Composable
expect fun AlPulsarAtras(habilitado: Boolean, alVolver: () -> Unit)

/** Guardia de navegacion: sin sesion vigente no se entra al area protegida. */
@Composable
fun NavegacionEco() {
    val sesionVm = koinViewModel<SesionViewModel>()
    val estado by sesionVm.estado.collectAsStateWithLifecycle()

    when (val actual = estado) {
        EstadoSesion.Comprobando -> IndicadorCarga()
        is EstadoSesion.SinSesion -> LoginScreen(sesionVm, actual.aviso)
        is EstadoSesion.Activa -> AreaProtegida(actual.sesion, alCerrarSesion = sesionVm::sesionCerrada)
    }
}

@Composable
private fun AreaProtegida(sesion: Sesion, alCerrarSesion: () -> Unit) {
    var pila by remember(sesion.token) { mutableStateOf(listOf<Pantalla>(Pantalla.Ruta)) }
    val volver = { pila = pila.dropLast(1) }
    AlPulsarAtras(habilitado = pila.size > 1, alVolver = volver)

    // La clave incluye la sesion: un nuevo turno obtiene ViewModels nuevos.
    val clave = sesion.token.hashCode()
    when (val pantalla = pila.last()) {
        Pantalla.Ruta -> RutaScreen(
            viewModel = koinViewModel(key = "ruta-$clave") { parametersOf(sesion) },
            alAbrirPunto = { pila = pila + Pantalla.Detalle(it) },
            alAbrirPerfil = { pila = pila + Pantalla.Perfil }
        )
        is Pantalla.Detalle -> DetallePuntoScreen(
            viewModel = koinViewModel(key = "detalle-${pantalla.puntoId}-$clave") { parametersOf(pantalla.puntoId, sesion) },
            alVolver = volver
        )
        Pantalla.Perfil -> PerfilScreen(
            viewModel = koinViewModel(key = "perfil-$clave") { parametersOf(sesion) },
            alVolver = volver,
            alCerrarSesion = alCerrarSesion
        )
    }
}
