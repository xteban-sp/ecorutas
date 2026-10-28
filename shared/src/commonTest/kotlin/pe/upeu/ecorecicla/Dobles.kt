package pe.upeu.ecorecicla

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.Reloj
import pe.upeu.ecorecicla.data.remote.ApiConfig
import pe.upeu.ecorecicla.data.remote.AvisosDeSesion
import pe.upeu.ecorecicla.data.remote.crearClienteHttp
import pe.upeu.ecorecicla.db.EcoDb
import kotlin.io.encoding.Base64

/**
 * Driver de SQLite en memoria. JDBC solo existe en la JVM, por eso lo aporta
 * androidHostTest (JdbcSqliteDriver). Cada llamada devuelve una base nueva y vacia.
 */
expect fun crearDriverDePrueba(): SqlDriver

fun baseEnMemoria(): EcoDb = EcoDb(crearDriverDePrueba())

/** Sustituye a AlmacenSeguroAndroid: el token vive en una variable. */
class AlmacenEnMemoria(var token: String? = null) : AlmacenSeguro {
    override fun guardarToken(token: String) { this.token = token }
    override fun leerToken(): String? = token
    override fun borrarToken() { token = null }
}

object RelojFijo : Reloj {
    override fun ahoraMs(): Long = 1_793_200_000_000
    override fun ahoraIso(): String = "2026-10-28T10:00:00"
}

/** JWT con la carga indicada (la firma no importa: la verifica el servicio, no la app). */
fun tokenDePrueba(usuario: String = "operario1", exp: Long = 4_102_444_800): String {
    val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val cabecera = b64.encode("""{"alg":"HS256"}""".encodeToByteArray())
    val carga = b64.encode(
        """{"sub":"$usuario","rol":"OPERARIO","privilegios":["PESAJE_CREAR","PUNTO_LEER"],"exp":$exp}""".encodeToByteArray()
    )
    return "$cabecera.$carga.firma"
}

val cabecerasJson = headersOf(HttpHeaders.ContentType, "application/json")

fun MockRequestHandleScope.json(cuerpo: String, estado: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(cuerpo, estado, cabecerasJson)

/** El mismo cliente de produccion (plugins, token, errores), pero con el motor simulado. */
fun clienteDePrueba(
    almacen: AlmacenSeguro,
    avisos: AvisosDeSesion = AvisosDeSesion(),
    manejador: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = crearClienteHttp(
    engine = MockEngine(manejador),
    config = ApiConfig(urlBase = "http://servidor.test/api/v1/"),
    almacen = almacen,
    avisos = avisos
)

fun pesajeJson(id: Long, puntoId: Long = 4, anulado: Boolean = false) = """
    {"id":$id,"rutaId":2,"puntoId":$puntoId,"fechaHora":"2026-10-28T10:00:00","material":"CARTON",
     "pesoBruto":10.0,"tara":2.0,"pesoNeto":8.0,"observacion":"","operario":"operario1",
     "anulado":$anulado,"motivoAnulacion":${if (anulado) "\"Balanza\"" else "null"},"campoNuevo":"x"}
""".trimIndent()

const val RUTA_JSON = """
    {"id":2,"fecha":"2026-10-28","estado":"ABIERTA","operario":"operario1","supervisor":"supervisor1",
     "puntos":[{"id":4,"razonSocial":"Supermercados Ayni SA","direccion":"Av. Tupac Amaru 815","zona":"NORTE",
                "materialHabitual":"CARTON","secuencia":1,"atendido":false,"motivoOmision":null}]}
"""
