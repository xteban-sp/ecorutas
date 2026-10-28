package pe.upeu.ecorecicla.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.db.ListarPuntos as FilaPunto
import pe.upeu.ecorecicla.db.ObtenerPunto as FilaPuntoUnico
import pe.upeu.ecorecicla.db.Pesaje as FilaPesaje

/** Operacion pendiente de una fila de pesaje. */
object Operacion {
    const val CREAR = "CREAR"
    const val EDITAR = "EDITAR"
}

/**
 * Acceso a las tablas ruta, punto_acopio y sincronizacion. Traduce filas de
 * SQLDelight a dominio. Las consultas de lectura devuelven Flow: emiten de nuevo
 * cada vez que cambia alguna de las tablas que consultan.
 */
class RutaLocal(base: EcoDb) {

    private val rutas = base.rutaQueries
    private val sincronizacion = base.sincronizacionQueries

    fun observarRuta(): Flow<Ruta?> =
        rutas.obtenerRuta().asFlow().mapToOneOrNull(Dispatchers.Default).map { fila ->
            fila?.let { Ruta(it.id, it.fecha, it.estado == "CERRADA") }
        }

    fun observarPuntos(busqueda: String, soloPendientes: Boolean = false): Flow<List<PuntoAcopio>> =
        rutas.listarPuntos(busqueda, if (soloPendientes) 1L else 0L).asFlow().mapToList(Dispatchers.Default).map { filas -> filas.map { it.aDominio() } }

    fun observarPunto(id: Long): Flow<PuntoAcopio?> =
        rutas.obtenerPunto(id).asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.aDominio() }

    fun observarUltimaSincronizacion(): Flow<Long?> =
        sincronizacion.ultima().asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun rutaActual(): Ruta? = enDisco {
        rutas.obtenerRuta().executeAsOneOrNull()?.let { Ruta(it.id, it.fecha, it.estado == "CERRADA") }
    }

    /** Reemplaza ruta y puntos en UNA transaccion: la lista no parpadea mientras se reconstruye. */
    suspend fun reemplazar(ruta: Ruta?, puntos: List<PuntoAcopio>): Unit = enDisco {
        rutas.transaction {
            rutas.borrarRutas()
            rutas.borrarPuntos()
            if (ruta != null) {
                rutas.insertarRuta(ruta.id, ruta.fecha, if (ruta.cerrada) "CERRADA" else "ABIERTA")
                puntos.forEach {
                    rutas.insertarPunto(
                        it.id, it.razonSocial, it.direccion, it.zona, it.materialHabitual,
                        it.secuencia.toLong(), if (it.atendido) 1 else 0
                    )
                }
            }
        }
    }

    suspend fun marcarSincronizado(momentoMs: Long): Unit = enDisco { sincronizacion.registrar(momentoMs) }

    suspend fun borrarTodo(): Unit = enDisco {
        rutas.transaction {
            rutas.borrarPuntos()
            rutas.borrarRutas()
            sincronizacion.borrar()
        }
    }

    private fun FilaPunto.aDominio() = PuntoAcopio(
        id, razon_social, direccion, zona, material_habitual, secuencia.toInt(), atendido_local == 1L
    )

    private fun FilaPuntoUnico.aDominio() = PuntoAcopio(
        id, razon_social, direccion, zona, material_habitual, secuencia.toInt(), atendido_local == 1L
    )
}

/** Acceso a la tabla pesaje, incluida la marca de pendiente y el id provisional. */
class PesajeLocal(base: EcoDb) {

    private val consultas = base.pesajeQueries

    fun observarPorPunto(puntoId: Long): Flow<List<Pesaje>> =
        consultas.listarPorPunto(puntoId).asFlow().mapToList(Dispatchers.Default).map { filas -> filas.map { it.aDominio() } }

    fun observarCantidadPendientes(): Flow<Long> =
        consultas.contarPendientes().asFlow().mapToOne(Dispatchers.Default)

    suspend fun pendientes(): List<Pair<Pesaje, String>> = enDisco {
        consultas.listarPendientes().executeAsList().map { it.aDominio() to (it.operacion ?: Operacion.CREAR) }
    }

    suspend fun obtener(id: Long): Pesaje? = enDisco { consultas.obtenerPorId(id).executeAsOneOrNull()?.aDominio() }

    /**
     * Guarda un pesaje registrado sin red. El id es NEGATIVO (-1, -2, ...): el
     * servicio nunca emite esos valores, asi que no choca con ningun id real.
     */
    suspend fun guardarPendiente(pesaje: Pesaje): Pesaje = enDisco {
        consultas.transactionWithResult {
            val id = consultas.siguienteIdLocal().executeAsOne().expr ?: -1L
            val local = pesaje.copy(id = id, pendiente = true)
            insertar(local, Operacion.CREAR)
            local
        }
    }

    /** Correccion sin red: la fila queda pendiente. Si aun no se habia creado en el servidor, sigue siendo CREAR. */
    suspend fun guardarCorreccionPendiente(pesaje: Pesaje): Unit = enDisco {
        insertar(pesaje.copy(pendiente = true), if (pesaje.id < 0) Operacion.CREAR else Operacion.EDITAR)
    }

    suspend fun guardarSincronizado(pesaje: Pesaje): Unit = enDisco { insertar(pesaje.copy(pendiente = false), null) }

    /** El servidor acepto un pendiente: la fila provisional se sustituye por la definitiva en la MISMA transaccion. */
    suspend fun confirmarEnvio(idLocal: Long, delServidor: Pesaje): Unit = enDisco {
        consultas.transaction {
            consultas.borrarPorId(idLocal)
            insertar(delServidor.copy(pendiente = false), null)
        }
    }

    /** La bajada reemplaza solo lo sincronizado; una fila con cambios pendientes no se pisa. */
    suspend fun reemplazarSincronizados(pesajes: List<Pesaje>): Unit = enDisco {
        consultas.transaction {
            val pendientes = consultas.listarPendientes().executeAsList().map { it.id }.toSet()
            consultas.borrarSincronizados()
            pesajes.filter { it.id !in pendientes }.forEach { insertar(it.copy(pendiente = false), null) }
        }
    }

    suspend fun marcarAnulado(id: Long, motivo: String): Unit = enDisco { consultas.marcarAnulado(motivo, id) }

    suspend fun borrar(id: Long): Unit = enDisco { consultas.borrarPorId(id) }

    suspend fun borrarTodo(): Unit = enDisco { consultas.borrarTodo() }

    private fun insertar(p: Pesaje, operacion: String?) {
        val anulado = p.estado as? EstadoPesaje.Anulado
        consultas.insertar(
            id = p.id,
            ruta_id = p.rutaId,
            punto_id = p.puntoId,
            fecha_hora = p.fechaHora,
            material = p.material,
            peso_bruto = p.pesoBruto,
            tara = p.tara,
            peso_neto = p.pesoNeto,
            observacion = p.observacion,
            operario = p.operario,
            anulado = if (anulado != null) 1 else 0,
            motivo_anulacion = anulado?.motivo,
            pendiente = if (p.pendiente) 1 else 0,
            operacion = operacion
        )
    }

    private fun FilaPesaje.aDominio() = Pesaje(
        id = id,
        puntoId = punto_id,
        fechaHora = fecha_hora,
        material = material,
        pesoBruto = peso_bruto,
        tara = tara,
        pesoNeto = peso_neto,
        operario = operario,
        estado = if (anulado == 1L) EstadoPesaje.Anulado(motivo_anulacion.orEmpty()) else EstadoPesaje.Registrado,
        pendiente = pendiente == 1L,
        rutaId = ruta_id,
        observacion = observacion
    )
}
