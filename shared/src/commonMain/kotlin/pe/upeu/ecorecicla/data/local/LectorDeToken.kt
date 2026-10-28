package pe.upeu.ecorecicla.data.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import pe.upeu.ecorecicla.data.remote.jsonEco
import pe.upeu.ecorecicla.domain.model.Sesion
import kotlin.io.encoding.Base64

/**
 * Lee la carga util del JWT guardado para recuperar la sesion al reabrir la
 * aplicacion sin pedir nada a la red. NO verifica la firma: eso lo hace el
 * servicio en cada peticion. Aqui solo interesa saber si ya vencio (exp) y que
 * acciones ofrecer.
 */
object LectorDeToken {

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    data class Contenido(val sesion: Sesion, val expiraEnSegundos: Long)

    fun leer(token: String): Contenido? = runCatching {
        val partes = token.split(".")
        if (partes.size != 3) return null
        val json = jsonEco.parseToJsonElement(base64Url.decode(partes[1]).decodeToString()) as JsonObject
        Contenido(
            sesion = Sesion(
                token = token,
                usuario = json.getValue("sub").jsonPrimitive.content,
                rol = json["rol"]?.jsonPrimitive?.content.orEmpty(),
                privilegios = json["privilegios"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
                nombre = json["nombre"]?.jsonPrimitive?.content.orEmpty()
            ),
            expiraEnSegundos = json.getValue("exp").jsonPrimitive.long
        )
    }.getOrNull()

    /** Sesion si el token sigue vigente segun su claim exp; null si vencio o no se puede leer. */
    fun sesionVigente(token: String, ahoraMs: Long): Sesion? {
        val contenido = leer(token) ?: return null
        return contenido.sesion.takeIf { contenido.expiraEnSegundos * 1000 > ahoraMs }
    }
}
