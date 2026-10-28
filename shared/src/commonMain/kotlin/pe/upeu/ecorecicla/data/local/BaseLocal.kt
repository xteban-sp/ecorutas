package pe.upeu.ecorecicla.data.local

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Abre la base local. El esquema (.sq) y las consultas son comunes; lo unico que
 * cambia por plataforma es quien abre el archivo: AndroidSqliteDriver en la app
 * (BaseLocalAndroid) y un driver JDBC en memoria en las pruebas.
 */
interface BaseLocal {
    fun crearDriver(): SqlDriver
}

const val NOMBRE_BASE_LOCAL = "ecorecicla.db"

/** Hora del dispositivo. Interfaz para poder fijarla en las pruebas. */
interface Reloj {
    fun ahoraMs(): Long

    /** Fecha y hora local en ISO-8601 (yyyy-MM-ddTHH:mm:ss), mismo formato que el servicio. */
    fun ahoraIso(): String
}

object RelojDelSistema : Reloj {
    override fun ahoraMs(): Long = Clock.System.now().toEpochMilliseconds()

    override fun ahoraIso(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString().take(19)
}

/** SQLite hace E/S de disco: las escrituras van fuera del hilo principal. */
internal suspend fun <T> enDisco(bloque: () -> T): T = withContext(Dispatchers.IO) { bloque() }
