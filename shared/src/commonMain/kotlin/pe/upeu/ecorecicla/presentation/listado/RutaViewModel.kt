package pe.upeu.ecorecicla.presentation.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Privilegios
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.model.comoErrorApi
import pe.upeu.ecorecicla.domain.repository.MonitorConexion
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPuntosUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarUltimaSincronizacionUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.components.Aviso
import pe.upeu.ecorecicla.presentation.components.mensaje

data class RutaUiState(
    /** true hasta que la base local emite por primera vez. */
    val cargando: Boolean = true,
    val ruta: Ruta? = null,
    val puntos: List<PuntoAcopio> = emptyList(),
    val busqueda: String = "",
    val ultimaSincronizacion: Long? = null,
    val sincronizando: Boolean = false,
    val cerrando: Boolean = false,
    val confirmarCierre: Boolean = false,
    /** Solo decide si se OFRECE el boton; el servicio vuelve a verificar RUTA_CERRAR. */
    val puedeCerrarRuta: Boolean = false,
    val aviso: Aviso? = null
) {
    val atendidos: Int get() = puntos.count { it.atendido }
}

/**
 * Pantalla principal (RF-A03). La lista se alimenta SOLO de la base local
 * mediante Flow: con o sin red la pantalla muestra lo ultimo guardado. La red
 * entra unicamente a traves de SincronizarUseCase, que actualiza la base, y la
 * base vuelve a emitir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RutaViewModel(
    sesion: Sesion,
    observarRuta: ObservarRutaUseCase,
    observarPuntos: ObservarPuntosUseCase,
    observarUltimaSincronizacion: ObservarUltimaSincronizacionUseCase,
    private val sincronizarUseCase: SincronizarUseCase,
    private val cerrarRutaUseCase: CerrarRutaUseCase,
    monitor: MonitorConexion
) : ViewModel() {

    private val _estado = MutableStateFlow(RutaUiState(puedeCerrarRuta = sesion.puede(Privilegios.RUTA_CERRAR)))
    val estado: StateFlow<RutaUiState> = _estado.asStateFlow()

    private val busqueda = MutableStateFlow("")

    init {
        viewModelScope.launch { observarRuta().collect { ruta -> _estado.update { it.copy(ruta = ruta) } } }
        viewModelScope.launch {
            // La busqueda se resuelve en la consulta SQL, no recorriendo la lista aqui.
            busqueda.flatMapLatest { observarPuntos(it) }
                .collect { puntos -> _estado.update { it.copy(puntos = puntos, cargando = false) } }
        }
        viewModelScope.launch {
            observarUltimaSincronizacion().collect { ms -> _estado.update { it.copy(ultimaSincronizacion = ms) } }
        }
        // Sincronizacion automatica al recuperar la conexion (false -> true).
        viewModelScope.launch {
            monitor.conectado.distinctUntilChanged().drop(1).filter { it }.collect { sincronizar(automatica = true) }
        }
        // Al entrar se intenta poner la base al dia; sin red no pasa nada: se ve lo guardado.
        sincronizar(automatica = true)
    }

    fun buscar(texto: String) {
        busqueda.value = texto
        _estado.update { it.copy(busqueda = texto) }
    }

    fun sincronizar() = sincronizar(automatica = false)

    private fun sincronizar(automatica: Boolean) {
        if (_estado.value.sincronizando) return
        _estado.update { it.copy(sincronizando = true) }
        viewModelScope.launch {
            sincronizarUseCase()
                .onSuccess { resumen ->
                    val hayNovedad = resumen.enviados > 0 || resumen.rechazos.isNotEmpty()
                    _estado.update {
                        it.copy(sincronizando = false, aviso = if (!automatica || hayNovedad) Aviso(resumen.texto()) else it.aviso)
                    }
                }
                .onFailure { e ->
                    val error = e.comoErrorApi()
                    _estado.update { it.copy(sincronizando = false, aviso = avisoDeSincronizacion(error, automatica)) }
                }
        }
    }

    fun pedirCierre() = _estado.update { it.copy(confirmarCierre = true) }

    fun cancelarCierre() = _estado.update { it.copy(confirmarCierre = false) }

    fun cerrarRuta() {
        if (_estado.value.cerrando) return
        _estado.update { it.copy(cerrando = true, confirmarCierre = false) }
        viewModelScope.launch {
            cerrarRutaUseCase()
                .onSuccess { _estado.update { it.copy(cerrando = false, aviso = Aviso("Ruta del dia cerrada")) } }
                .onFailure { e ->
                    val error = e.comoErrorApi()
                    val aviso = when (error) {
                        // 409: motivo del servidor (RN-04: cuantos puntos faltan)
                        is ErrorApi.Conflicto -> Aviso(error.mensaje)
                        // 404: se informa y se refresca el listado, sin reintentar
                        is ErrorApi.NoEncontrado -> Aviso(error.mensaje).also { sincronizar(automatica = true) }
                        // 500: mensaje breve y reintento
                        ErrorApi.Servidor -> Aviso(error.mensaje(), reintentar = ::cerrarRuta)
                        // 401 lo maneja SesionViewModel (vuelve al login)
                        ErrorApi.NoAutenticado -> null
                        else -> Aviso(error.mensaje())
                    }
                    _estado.update { it.copy(cerrando = false, aviso = aviso) }
                }
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }

    private fun avisoDeSincronizacion(error: ErrorApi, automatica: Boolean): Aviso? = when (error) {
        ErrorApi.SinConexion, ErrorApi.TiempoAgotado ->
            if (automatica) Aviso("Sin conexion: se muestran los datos guardados en el equipo")
            else Aviso("Sin conexion: lo registrado queda pendiente y se enviara al recuperar la senal")
        ErrorApi.Servidor -> Aviso(error.mensaje(), reintentar = { sincronizar(automatica = false) })
        ErrorApi.NoAutenticado -> null
        else -> Aviso(error.mensaje())
    }
}

private fun ResumenSincronizacion.texto(): String = buildString {
    append("Sincronizado: $enviados enviado(s), $recibidos recibido(s)")
    if (rechazos.isNotEmpty()) append(". Rechazados por el servidor: ${rechazos.joinToString(" | ")}")
}
