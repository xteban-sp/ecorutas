package pe.upeu.ecorecicla.data.local

/**
 * Donde se guarda el JWT. El contrato es comun; la implementacion es de cada
 * plataforma (AlmacenSeguroAndroid: EncryptedSharedPreferences + Android
 * Keystore) y llega inyectada por el platformModule.
 */
interface AlmacenSeguro {
    fun guardarToken(token: String)
    fun leerToken(): String?
    fun borrarToken()
}
