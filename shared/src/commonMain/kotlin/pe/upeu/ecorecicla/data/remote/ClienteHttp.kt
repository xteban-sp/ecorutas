package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.domain.model.ErrorApi

/** Configuracion JSON compartida: tolera claves que el DTO no declara. */
val jsonEco = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/**
 * UNICO punto donde se configura el cliente HTTP. Koin lo registra como single:
 * una sola instancia (y un solo pool de conexiones) para toda la aplicacion.
 *
 * La cabecera Authorization se agrega AQUI, en el plugin TokenJwt, para todas
 * las peticiones salvo el login. Ninguna funcion del API la escribe a mano: si
 * se hiciera funcion por funcion bastaria olvidarla en una para recibir 401.
 */
fun crearClienteHttp(
    engine: HttpClientEngine,
    config: ApiConfig,
    almacen: AlmacenSeguro,
    avisos: AvisosDeSesion
): HttpClient = HttpClient(engine) {
    // 4xx -> ClientRequestException, 5xx -> ServerResponseException (las traduce MapeoErrores)
    expectSuccess = true

    install(ContentNegotiation) { json(jsonEco) }

    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMs
        connectTimeoutMillis = config.connectTimeoutMs
    }

    install(Logging) {
        // Registro reducido: cabeceras en depuracion, nada en produccion.
        level = if (config.registroHttp) LogLevel.HEADERS else LogLevel.NONE
        logger = object : Logger {
            override fun log(message: String) = println("EcoHttp => $message")
        }
        // El token nunca aparece en el registro.
        sanitizeHeader { it == HttpHeaders.Authorization }
    }

    // Cabecera de autorizacion centralizada. Se lee del almacen en cada peticion:
    // tras el login o el cierre de sesion no hay ninguna cache que invalidar.
    install(createClientPlugin("TokenJwt") {
        onRequest { peticion, _ ->
            if (!peticion.url.encodedPath.endsWith(RUTA_LOGIN)) {
                withContext(Dispatchers.IO) { almacen.leerToken() }?.let { token ->
                    peticion.headers[HttpHeaders.Authorization] = "Bearer $token"
                }
            }
        }
    })

    // Un 401 fuera del login significa que el token ya no sirve (vencio o fue
    // alterado): se borra y se avisa para volver al inicio de sesion.
    install(createClientPlugin("SesionVencida") {
        onResponse { respuesta ->
            val ruta = respuesta.call.request.url.encodedPath
            if (respuesta.status == HttpStatusCode.Unauthorized && !ruta.endsWith(RUTA_LOGIN)) {
                withContext(Dispatchers.IO) { almacen.borrarToken() }
                avisos.avisar(ErrorApi.NoAutenticado)
            }
        }
    })

    defaultRequest {
        url(config.urlBase)
        contentType(ContentType.Application.Json)
    }
}
