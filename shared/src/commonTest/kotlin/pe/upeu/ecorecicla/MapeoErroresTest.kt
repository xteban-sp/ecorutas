package pe.upeu.ecorecicla

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import pe.upeu.ecorecicla.data.remote.AvisosDeSesion
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.ejecutarLlamada
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.comoErrorApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapeoErroresTest {

    private suspend fun errorDe(estado: HttpStatusCode, cuerpo: String): ErrorApi {
        val api = EcoApi(clienteDePrueba(AlmacenEnMemoria(tokenDePrueba())) { json(cuerpo, estado) })
        return ejecutarLlamada { api.rutaDelDia() }.exceptionOrNull()!!.comoErrorApi()
    }

    @Test
    fun el400ConservaLosMensajesPorCampo() = runTest {
        val almacen = AlmacenEnMemoria(tokenDePrueba())
        val api = EcoApi(clienteDePrueba(almacen) {
            json(
                """{"status":400,"error":"Bad Request","message":"Datos invalidos","path":"/api/v1/pesajes",
                   "validationErrors":{"tara":"La tara debe ser menor que el peso bruto"}}""",
                HttpStatusCode.BadRequest
            )
        })
        val error = ejecutarLlamada { api.rutaDelDia() }.exceptionOrNull()!!.comoErrorApi()

        assertEquals(ErrorApi.Validacion(mapOf("tara" to "La tara debe ser menor que el peso bruto")), error)
    }

    @Test
    fun el403SeDistingueDel401YNoCierraLaSesion() = runTest {
        val almacen = AlmacenEnMemoria(tokenDePrueba())
        val api = EcoApi(clienteDePrueba(almacen) {
            json("""{"status":403,"message":"Solo puede corregir los pesajes que usted registro"}""", HttpStatusCode.Forbidden)
        })
        val error = ejecutarLlamada { api.rutaDelDia() }.exceptionOrNull()!!.comoErrorApi()

        assertEquals(ErrorApi.SinPrivilegio("Solo puede corregir los pesajes que usted registro"), error)
        assertEquals(tokenDePrueba(), almacen.token, "Con 403 el token se conserva")
    }

    @Test
    fun el401BorraElTokenYAvisaElFinDeSesion() = runTest {
        val almacen = AlmacenEnMemoria(tokenDePrueba())
        val avisos = AvisosDeSesion()
        var cabeceraEnviada: String? = null
        val api = EcoApi(clienteDePrueba(almacen, avisos) { peticion ->
            cabeceraEnviada = peticion.headers[HttpHeaders.Authorization]
            json("""{"status":401,"message":"El token ha vencido"}""", HttpStatusCode.Unauthorized)
        })
        val aviso = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { avisos.sesionTerminada.first() }

        val error = ejecutarLlamada { api.rutaDelDia() }.exceptionOrNull()!!.comoErrorApi()

        assertEquals("Bearer ${tokenDePrueba()}", cabeceraEnviada, "La cabecera la agrega el cliente de forma centralizada")
        assertEquals(ErrorApi.NoAutenticado, error)
        assertNull(almacen.token)
        assertEquals(ErrorApi.NoAutenticado, aviso.await())
    }

    @Test
    fun conflictoNoEncontradoServidorYSinRedTienenSuPropioTipo() = runTest {
        assertEquals(ErrorApi.Conflicto("La ruta ya esta cerrada"), errorDe(HttpStatusCode.Conflict, """{"message":"La ruta ya esta cerrada"}"""))
        assertEquals(ErrorApi.NoEncontrado("No existe"), errorDe(HttpStatusCode.NotFound, """{"message":"No existe"}"""))
        assertEquals(ErrorApi.Servidor, errorDe(HttpStatusCode.InternalServerError, """{"message":"x"}"""))

        val api = EcoApi(clienteDePrueba(AlmacenEnMemoria()) { throw IOException("sin red") })
        assertEquals(ErrorApi.SinConexion, ejecutarLlamada { api.rutaDelDia() }.exceptionOrNull()!!.comoErrorApi())
    }
}
