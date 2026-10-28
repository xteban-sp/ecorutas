package pe.upeu.ecorecicla

import pe.upeu.ecorecicla.data.remote.dto.PaginaDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PuntoRutaDto
import pe.upeu.ecorecicla.data.remote.dto.RutaDto
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.data.remote.dto.aPeticionRegistro
import pe.upeu.ecorecicla.data.remote.jsonEco
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversionDtoTest {

    @Test
    fun pesajeAnuladoSeConvierteAlDominioToleraClavesDesconocidas() {
        // "campoNuevo" no existe en el DTO: ignoreUnknownKeys evita el fallo.
        val dto = jsonEco.decodeFromString<PesajeDto>(pesajeJson(id = 15, anulado = true))
        val pesaje = dto.aDominio()

        assertEquals(15, pesaje.id)
        assertEquals("operario1", pesaje.operario)     // @SerialName("operario")
        assertEquals(8.0, pesaje.pesoNeto)
        assertEquals(EstadoPesaje.Anulado("Balanza"), pesaje.estado)
        assertFalse(pesaje.pendiente)
    }

    @Test
    fun listadoSeDeserializaConElEnvoltorioDePaginacion() {
        val cuerpo = """{"contenido":[${pesajeJson(1)},${pesajeJson(2)}],"pagina":0,"tamanio":20,
            "totalElementos":2,"totalPaginas":1,"ultima":true}"""
        val pagina = jsonEco.decodeFromString<PaginaDto<PesajeDto>>(cuerpo)

        assertEquals(2, pagina.contenido.size)
        assertTrue(pagina.ultima)
        assertEquals(EstadoPesaje.Registrado, pagina.contenido.first().aDominio().estado)
    }

    @Test
    fun rutaYPuntosSeConviertenConElNombreAnotado() {
        val ruta = jsonEco.decodeFromString<RutaDto>(RUTA_JSON)
        val punto: PuntoRutaDto = ruta.puntos.single()

        assertFalse(ruta.aDominio().cerrada)
        assertEquals("CARTON", punto.aDominio().materialHabitual) // @SerialName("materialHabitual")
        assertEquals(1, punto.aDominio().secuencia)
    }

    @Test
    fun laPeticionDeRegistroNoEnviaElPesoNeto() {
        val pesaje = jsonEco.decodeFromString<PesajeDto>(pesajeJson(3)).aDominio()
        val json = jsonEco.encodeToString(pesaje.aPeticionRegistro())

        assertFalse("pesoNeto" in json, "El cliente nunca envia el neto (RN-03): $json")
        assertTrue("\"tara\":2.0" in json)
    }
}
