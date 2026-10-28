package pe.upeu.ecorecicla.domain.model

data class Sesion(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
    // Propiedad agregada: nombre completo para el perfil
    val nombre: String = ""
) {
    /** Solo decide que acciones OFRECE la interfaz; quien autoriza es el servicio. */
    fun puede(privilegio: String): Boolean = privilegio in privilegios
}

/** Nombres de privilegio del contrato de autorizacion (seccion 3.3). */
object Privilegios {
    const val PUNTO_LEER = "PUNTO_LEER"
    const val PESAJE_LEER = "PESAJE_LEER"
    const val PESAJE_CREAR = "PESAJE_CREAR"
    const val PESAJE_EDITAR = "PESAJE_EDITAR"
    const val PESAJE_EDITAR_TODOS = "PESAJE_EDITAR_TODOS"
    const val PESAJE_ANULAR = "PESAJE_ANULAR"
    const val RUTA_CERRAR = "RUTA_CERRAR"
    const val PUNTO_GESTIONAR = "PUNTO_GESTIONAR"
}
