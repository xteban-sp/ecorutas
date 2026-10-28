package pe.upeu.ecorecicla.presentation.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.Privilegios
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.model.comoErrorApi
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPuntoUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.components.Aviso
import pe.upeu.ecorecicla.presentation.components.kg
import pe.upeu.ecorecicla.presentation.components.mensaje

/** Formulario de registro (idEditando = null) o de correccion. Errores por campo. */
data class FormularioPesaje(
    val idEditando: Long? = null,
    val material: String = "",
    val pesoBruto: String = "",
    val tara: String = "",
    val observacion: String = "",
    /** SC-C: humedad estimada en %, opcional. */
    val humedad: String = "",
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false
)

data class AnulacionPesaje(
    val pesaje: Pesaje,
    val motivo: String = "",
    val error: String? = null,
    val enviando: Boolean = false
)

data class DetalleUiState(
    val cargando: Boolean = true,
    val punto: PuntoAcopio? = null,
    val pesajes: List<Pesaje> = emptyList(),
    val formulario: FormularioPesaje? = null,
    val anulacion: AnulacionPesaje? = null,
    val aviso: Aviso? = null
)

class DetalleViewModel(
    private val puntoId: Long,
    private val sesion: Sesion,
    observarPunto: ObservarPuntoUseCase,
    observarPesajes: ObservarPesajesUseCase,
    private val registrarPesaje: RegistrarPesajeUseCase,
    private val corregirPesaje: CorregirPesajeUseCase,
    private val anularPesaje: AnularPesajeUseCase,
    private val sincronizar: SincronizarUseCase
) : ViewModel() {

    private val _estado = MutableStateFlow(DetalleUiState())
    val estado: StateFlow<DetalleUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch { observarPunto(puntoId).collect { p -> _estado.update { it.copy(punto = p) } } }
        viewModelScope.launch {
            observarPesajes(puntoId).collect { lista -> _estado.update { it.copy(pesajes = lista, cargando = false) } }
        }
    }

    // ---- Que acciones se OFRECEN. Es experiencia de usuario: el servicio verifica igual.

    val puedeRegistrar: Boolean get() = sesion.puede(Privilegios.PESAJE_CREAR)

    fun puedeCorregir(p: Pesaje): Boolean =
        !p.anulado && sesion.puede(Privilegios.PESAJE_EDITAR) &&
            (sesion.puede(Privilegios.PESAJE_EDITAR_TODOS) || p.operario == sesion.usuario)

    fun puedeAnular(p: Pesaje): Boolean = !p.anulado && sesion.puede(Privilegios.PESAJE_ANULAR)

    // ---- Formulario

    fun abrirRegistro() = _estado.update {
        it.copy(formulario = FormularioPesaje(material = it.punto?.materialHabitual.orEmpty()))
    }

    fun abrirCorreccion(p: Pesaje) = _estado.update {
        it.copy(
            formulario = FormularioPesaje(
                p.id, p.material, kg(p.pesoBruto), kg(p.tara), p.observacion,
                humedad = p.humedadEstimada?.let(::kg).orEmpty()
            )
        )
    }

    fun cerrarFormulario() = _estado.update { it.copy(formulario = null) }

    fun editarFormulario(cambio: FormularioPesaje.() -> FormularioPesaje) = _estado.update { estado ->
        estado.copy(formulario = estado.formulario?.cambio()?.copy(errores = emptyMap()))
    }

    fun guardar() {
        val form = _estado.value.formulario ?: return
        if (form.guardando) return
        _estado.update { it.copy(formulario = form.copy(guardando = true)) }
        val datos = DatosPesaje(
            puntoId = puntoId,
            material = form.material,
            pesoBruto = form.pesoBruto.replace(',', '.').toDoubleOrNull(),
            tara = form.tara.replace(',', '.').toDoubleOrNull(),
            observacion = form.observacion,
            humedadEstimada = form.humedad.replace(',', '.').toDoubleOrNull()
        )
        viewModelScope.launch {
            val resultado = if (form.idEditando == null) registrarPesaje(datos) else corregirPesaje(form.idEditando, datos)
            resultado
                .onSuccess { p ->
                    val texto = if (p.pendiente) {
                        "Guardado sin conexion: queda pendiente de envio (id local ${p.id})"
                    } else {
                        "Pesaje guardado. Peso neto calculado por el servicio: ${kg(p.pesoNeto)} kg"
                    }
                    _estado.update { it.copy(formulario = null, aviso = Aviso(texto)) }
                }
                .onFailure { e -> manejarErrorDeFormulario(e.comoErrorApi()) }
        }
    }

    private fun manejarErrorDeFormulario(error: ErrorApi) = _estado.update { estado ->
        val form = estado.formulario?.copy(guardando = false)
        when (error) {
            // 400: cada mensaje bajo el campo que lo origino
            is ErrorApi.Validacion -> estado.copy(formulario = form?.copy(errores = error.campos))
            // 409: motivo del servidor (RN-01 ruta cerrada...)
            is ErrorApi.Conflicto -> estado.copy(formulario = form?.copy(errores = mapOf("general" to error.mensaje)))
            // 403: no autorizado, la sesion se mantiene (RN-02)
            is ErrorApi.SinPrivilegio -> estado.copy(formulario = null, aviso = Aviso(error.mensaje()))
            // 404: se informa y se refresca, sin reintento
            is ErrorApi.NoEncontrado -> estado.copy(formulario = null, aviso = Aviso(error.mensaje)).also { refrescar() }
            // 500: mensaje breve y reintento
            ErrorApi.Servidor -> estado.copy(formulario = form, aviso = Aviso(error.mensaje(), reintentar = ::guardar))
            // 401: SesionViewModel vuelve al login
            ErrorApi.NoAutenticado -> estado.copy(formulario = null)
            ErrorApi.SinConexion, ErrorApi.TiempoAgotado -> estado.copy(formulario = form, aviso = Aviso(error.mensaje()))
        }
    }

    // ---- Anulacion

    fun pedirAnulacion(p: Pesaje) = _estado.update { it.copy(anulacion = AnulacionPesaje(p)) }

    fun cambiarMotivo(texto: String) = _estado.update { it.copy(anulacion = it.anulacion?.copy(motivo = texto, error = null)) }

    fun cancelarAnulacion() = _estado.update { it.copy(anulacion = null) }

    fun anular() {
        val anulacion = _estado.value.anulacion ?: return
        if (anulacion.enviando) return
        _estado.update { it.copy(anulacion = anulacion.copy(enviando = true)) }
        viewModelScope.launch {
            anularPesaje(anulacion.pesaje.id, anulacion.motivo)
                .onSuccess { _estado.update { it.copy(anulacion = null, aviso = Aviso("Pesaje anulado")) } }
                .onFailure { e ->
                    val error = e.comoErrorApi()
                    _estado.update { estado ->
                        when (error) {
                            is ErrorApi.Validacion ->
                                estado.copy(anulacion = anulacion.copy(error = error.campos.values.firstOrNull()))
                            is ErrorApi.NoEncontrado ->
                                estado.copy(anulacion = null, aviso = Aviso(error.mensaje)).also { refrescar() }
                            ErrorApi.Servidor -> estado.copy(anulacion = anulacion, aviso = Aviso(error.mensaje(), reintentar = ::anular))
                            ErrorApi.NoAutenticado -> estado.copy(anulacion = null)
                            ErrorApi.SinConexion, ErrorApi.TiempoAgotado ->
                                estado.copy(anulacion = null, aviso = Aviso("La anulacion necesita conexion con el servidor"))
                            else -> estado.copy(anulacion = null, aviso = Aviso(error.mensaje()))
                        }
                    }
                }
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }

    private fun refrescar() {
        viewModelScope.launch { sincronizar() }
    }
}
