package pe.upeu.ecorecicla

import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import pe.upeu.ecorecicla.data.local.PesajeLocal
import pe.upeu.ecorecicla.data.local.RutaLocal
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.repository.PesajeRepositoryImpl
import pe.upeu.ecorecicla.data.repository.RutaRepositoryImpl
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Se sustituye la red por MockEngine y la base por SQLite en memoria; el resto
 * (repositorios, consultas .sq, casos de uso) es el codigo de produccion.
 */
class OfflineTest {

    private class Escenario(manejador: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val base = baseEnMemoria()
        val rutaLocal = RutaLocal(base)
        val pesajeLocal = PesajeLocal(base)
        val api = EcoApi(clienteDePrueba(AlmacenEnMemoria(tokenDePrueba()), manejador = manejador))
        val pesajes = PesajeRepositoryImpl(api, pesajeLocal, rutaLocal, AlmacenEnMemoria(tokenDePrueba()), RelojFijo)
        val rutas = RutaRepositoryImpl(api, rutaLocal, pesajeLocal, RelojFijo)

        suspend fun conRutaDescargada() = rutaLocal.reemplazar(
            Ruta(2, "2026-10-28", cerrada = false),
            listOf(PuntoAcopio(4, "Supermercados Ayni SA", "Av. Tupac Amaru 815", "NORTE", "CARTON", 1, atendido = false))
        )
    }

    private val datos = DatosPesaje(puntoId = 4, material = "carton", pesoBruto = 10.0, tara = 2.0)

    @Test
    fun registrarSinConexionGuardaPendienteConIdLocalNegativo() = runTest {
        val e = Escenario { throw IOException("modo avion") }
        e.conRutaDescargada()
        val registrar = RegistrarPesajeUseCase(e.pesajes)

        val primero = registrar(datos).getOrThrow()
        val segundo = registrar(datos.copy(pesoBruto = 20.0)).getOrThrow()

        assertEquals(-1, primero.id)
        assertEquals(-2, segundo.id, "Cada pendiente recibe un id provisional distinto y negativo")
        assertTrue(primero.pendiente && segundo.pendiente)
        assertEquals(2, e.pesajeLocal.observarCantidadPendientes().first())
        // La pantalla (que lee de la base local) ya ve el punto como atendido
        assertTrue(e.rutaLocal.observarPuntos("").first().single().atendido)
    }

    @Test
    fun sincronizarSubePrimeroYBajaDespuesSinDuplicarNiPerder() = runTest {
        val peticiones = mutableListOf<String>()
        var hayRed = false
        val e = Escenario { peticion ->
            if (!hayRed) throw IOException("modo avion")
            peticiones += "${peticion.method.value} ${peticion.url.encodedPath.removePrefix("/api/v1/")}"
            when {
                peticion.method == HttpMethod.Post && peticion.url.encodedPath.endsWith("pesajes") ->
                    json(pesajeJson(id = 16), HttpStatusCode.Created)
                peticion.url.encodedPath.endsWith("rutas/del-dia") -> json(RUTA_JSON)
                peticion.url.encodedPath.endsWith("rutas/2/pesajes") ->
                    // El servidor ya tiene el pesaje 16 que acabamos de subir
                    json("""{"contenido":[${pesajeJson(16)}],"pagina":0,"tamanio":200,"totalElementos":1,"totalPaginas":1,"ultima":true}""")
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        e.conRutaDescargada()
        val pendiente = RegistrarPesajeUseCase(e.pesajes)(datos).getOrThrow()
        assertEquals(-1, pendiente.id)

        // Vuelve la senal
        hayRed = true
        val resumen = SincronizarUseCase(e.pesajes, e.rutas)().getOrThrow()

        assertEquals(listOf("POST pesajes", "GET rutas/del-dia", "GET rutas/2/pesajes"), peticiones, "Primero sube, despues baja")
        assertEquals(1, resumen.enviados)
        val enBase = e.pesajeLocal.observarPorPunto(4).first()
        assertEquals(listOf(16L), enBase.map { it.id }, "La fila provisional -1 se sustituyo por la definitiva 16, sin duplicado")
        assertEquals(0, e.pesajeLocal.observarCantidadPendientes().first())
    }

    /** SC-D: el distintivo lee una consulta observable; sube al registrar sin red y vuelve a 0 al sincronizar. */
    @Test
    fun distintivoDePendientesSeActualizaAlRegistrarYAlSincronizar() = runTest {
        var hayRed = false
        val e = Escenario { peticion ->
            if (!hayRed) throw IOException("modo avion")
            when {
                peticion.method == HttpMethod.Post -> json(pesajeJson(id = 30), HttpStatusCode.Created)
                peticion.url.encodedPath.endsWith("rutas/del-dia") -> json(RUTA_JSON)
                else -> json("""{"contenido":[${pesajeJson(30)}],"pagina":0,"tamanio":200,"totalElementos":1,"totalPaginas":1,"ultima":true}""")
            }
        }
        e.conRutaDescargada()
        val contador = e.pesajes.observarCantidadPendientes()
        assertEquals(0, contador.first())

        RegistrarPesajeUseCase(e.pesajes)(datos).getOrThrow()
        assertEquals(1, contador.first(), "Sube al registrar sin conexion")

        hayRed = true
        SincronizarUseCase(e.pesajes, e.rutas)().getOrThrow()
        assertEquals(0, contador.first(), "Vuelve a 0 al sincronizar")
    }
}
