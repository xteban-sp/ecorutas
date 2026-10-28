package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import pe.upeu.ecorecicla.data.remote.dto.CorreccionRequestDto
import pe.upeu.ecorecicla.data.remote.dto.LoginRequestDto
import pe.upeu.ecorecicla.data.remote.dto.LoginResponseDto
import pe.upeu.ecorecicla.data.remote.dto.PaginaDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.RutaDto
import pe.upeu.ecorecicla.data.remote.dto.UsuarioDto

/**
 * Llamadas al servicio EcoAPI. Rutas relativas a la URL base (/api/v1/).
 * No escribe cabeceras de autorizacion ni maneja errores: de eso se ocupan
 * ClienteHttp (plugin TokenJwt) y MapeoErrores.
 */
class EcoApi(private val cliente: HttpClient) {

    suspend fun login(peticion: LoginRequestDto): LoginResponseDto =
        cliente.post(RUTA_LOGIN) { setBody(peticion) }.body()

    suspend fun usuarioActual(): UsuarioDto = cliente.get("auth/me").body()

    suspend fun rutaDelDia(): RutaDto = cliente.get("rutas/del-dia").body()

    /** Listado paginado: se deserializa como PaginaDto, no como lista. */
    suspend fun pesajesDeRuta(rutaId: Long, pagina: Int = 0, tamanio: Int = 200): PaginaDto<PesajeDto> =
        cliente.get("rutas/$rutaId/pesajes") {
            parameter("pagina", pagina)
            parameter("tamanio", tamanio)
        }.body()

    suspend fun cerrarRuta(rutaId: Long): RutaDto = cliente.post("rutas/$rutaId/cierre").body()

    /** 201 Created con el pesaje y su id definitivo. */
    suspend fun registrarPesaje(peticion: PesajeRequestDto): PesajeDto =
        cliente.post("pesajes") { setBody(peticion) }.body()

    suspend fun corregirPesaje(id: Long, peticion: CorreccionRequestDto): PesajeDto =
        cliente.put("pesajes/$id") { setBody(peticion) }.body()

    /** 204 No Content: no hay cuerpo que convertir, solo se comprueba el codigo. */
    suspend fun anularPesaje(id: Long, motivo: String) {
        cliente.delete("pesajes/$id") { parameter("motivo", motivo) }
    }
}
