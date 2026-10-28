package pe.upeu.ecorecicla.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO propios de la capa de datos. Reflejan el JSON del servicio; el dominio no
// los conoce ni sabe nada de @Serializable. Cuando el nombre de Kotlin difiere
// del JSON se anota con @SerialName.

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val username: String,
    @SerialName("nombreCompleto") val nombre: String = "",
    val rol: String,
    val privilegios: List<String> = emptyList(),
    val expiraEnSegundos: Long = 3600
)

@Serializable
data class UsuarioDto(
    val username: String,
    @SerialName("nombreCompleto") val nombre: String = "",
    val rol: String,
    val privilegios: List<String> = emptyList()
)

@Serializable
data class RutaDto(
    val id: Long,
    val fecha: String,
    val estado: String,
    val puntos: List<PuntoRutaDto> = emptyList()
)

@Serializable
data class PuntoRutaDto(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    @SerialName("materialHabitual") val material: String,
    val secuencia: Int,
    val atendido: Boolean = false
)

@Serializable
data class PesajeDto(
    val id: Long,
    val rutaId: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    val observacion: String = "",
    @SerialName("operario") val operarioUsuario: String,
    val anulado: Boolean = false,
    val motivoAnulacion: String? = null
)

/** Peticion de registro. No lleva peso neto: lo calcula el servicio (RN-03). */
@Serializable
data class PesajeRequestDto(
    val rutaId: Long,
    val puntoId: Long,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String
)

@Serializable
data class CorreccionRequestDto(
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String
)

/** Envoltorio de paginacion comun a todos los listados (seccion 4.2). */
@Serializable
data class PaginaDto<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val ultima: Boolean
)

/** Formato de error comun del servicio (seccion 4.2). */
@Serializable
data class ErrorDto(
    val timestamp: String? = null,
    val status: Int = 0,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null,
    val validationErrors: Map<String, String>? = null
)
