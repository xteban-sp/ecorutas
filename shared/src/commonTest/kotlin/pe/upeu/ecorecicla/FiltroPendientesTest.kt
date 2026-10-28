package pe.upeu.ecorecicla

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.upeu.ecorecicla.data.local.PesajeLocal
import pe.upeu.ecorecicla.data.local.RutaLocal
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import kotlin.test.Test
import kotlin.test.assertEquals

/** SC-A: el filtro "solo pendientes de pesar" se resuelve en la consulta local y se combina con la busqueda. */
class FiltroPendientesTest {

    private fun punto(id: Long, nombre: String, atendido: Boolean = false) =
        PuntoAcopio(id, nombre, "Av. $id", "NORTE", "CARTON", id.toInt(), atendido)

    @Test
    fun filtraPendientesCombinadoConBusquedaYSinRed() = runTest {
        val base = baseEnMemoria()
        val rutas = RutaLocal(base)
        rutas.reemplazar(
            Ruta(2, "2026-10-28", cerrada = false),
            listOf(punto(1, "Textiles Andinos", atendido = true), punto(2, "Textiles del Sur"), punto(3, "Hospital Norte"))
        )
        // Pesaje pendiente registrado sin red en el punto 3: ya no cuenta como pendiente de pesar
        PesajeLocal(base).guardarPendiente(
            Pesaje(0, 3, "2026-10-28T10:00:00", "PAPEL", 10.0, 1.0, 9.0, "operario1", EstadoPesaje.Registrado, true, rutaId = 2)
        )

        assertEquals(listOf(2L), rutas.observarPuntos("", soloPendientes = true).first().map { it.id })
        assertEquals(listOf(2L), rutas.observarPuntos("Textiles", soloPendientes = true).first().map { it.id })
        assertEquals(listOf(1L, 2L), rutas.observarPuntos("Textiles", soloPendientes = false).first().map { it.id })
        assertEquals(emptyList(), rutas.observarPuntos("Hospital", soloPendientes = true).first().map { it.id })
    }
}
