package pe.upeu.ecorecicla.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pe.upeu.ecorecicla.domain.model.ErrorApi

/** Canal por el que el cliente HTTP avisa que el servicio rechazo el token (401). */
class AvisosDeSesion {
    private val avisos = MutableSharedFlow<ErrorApi>(extraBufferCapacity = 1)

    val sesionTerminada: Flow<ErrorApi> = avisos.asSharedFlow()

    fun avisar(error: ErrorApi) {
        avisos.tryEmit(error)
    }
}
