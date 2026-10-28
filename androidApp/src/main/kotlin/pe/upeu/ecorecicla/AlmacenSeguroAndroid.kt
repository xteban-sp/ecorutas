package pe.upeu.ecorecicla

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.MasterKey
import pe.upeu.ecorecicla.data.local.AlmacenSeguro

/**
 * Preferencias cifradas: la clave y el valor se cifran con una llave AES-256
 * que custodia el Android Keystore (almacen de claves del sistema), fuera del
 * alcance de la app y de quien copie sus archivos. Unas SharedPreferences
 * comunes dejarian el JWT en texto plano en data/data/<paquete>/shared_prefs.
 */
// security-crypto 1.1.0 marca estas clases como obsoletas, pero siguen siendo la
// API de preferencias cifradas respaldadas por el Keystore que pide la evaluacion.
@Suppress("DEPRECATION")
class AlmacenSeguroAndroid(contexto: Context) : AlmacenSeguro {

    private val prefs: SharedPreferences by lazy {
        val llave = MasterKey.Builder(contexto)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            contexto,
            ARCHIVO,
            llave,
            PrefKeyEncryptionScheme.AES256_SIV,
            PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun guardarToken(token: String) {
        prefs.edit().putString(CLAVE, token).commit()
    }

    override fun leerToken(): String? = prefs.getString(CLAVE, null)

    override fun borrarToken() {
        prefs.edit().remove(CLAVE).commit()
    }

    private companion object {
        const val ARCHIVO = "ecorecicla_sesion_segura"
        const val CLAVE = "jwt"
    }
}
