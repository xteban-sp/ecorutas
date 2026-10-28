package pe.upeu.ecorecicla.data.remote.dto

import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.model.Sesion

// Funciones de conversion DTO <-> dominio. Es el unico lugar donde se conocen ambos.

fun LoginResponseDto.aSesion() = Sesion(
    token = token,
    usuario = username,
    rol = rol,
    privilegios = privilegios.toSet(),
    nombre = nombre
)

fun UsuarioDto.aSesion(token: String) = Sesion(
    token = token,
    usuario = username,
    rol = rol,
    privilegios = privilegios.toSet(),
    nombre = nombre
)

fun RutaDto.aDominio() = Ruta(id = id, fecha = fecha, cerrada = estado.equals("CERRADA", ignoreCase = true))

fun PuntoRutaDto.aDominio() = PuntoAcopio(
    id = id,
    razonSocial = razonSocial,
    direccion = direccion,
    zona = zona,
    materialHabitual = material,
    secuencia = secuencia,
    atendido = atendido
)

fun PesajeDto.aDominio() = Pesaje(
    id = id,
    puntoId = puntoId,
    fechaHora = fechaHora,
    material = material,
    pesoBruto = pesoBruto,
    tara = tara,
    pesoNeto = pesoNeto,
    operario = operarioUsuario,
    estado = if (anulado) EstadoPesaje.Anulado(motivoAnulacion.orEmpty()) else EstadoPesaje.Registrado,
    pendiente = false,
    rutaId = rutaId,
    observacion = observacion,
    humedadEstimada = humedadEstimada
)

fun Pesaje.aPeticionRegistro() = PesajeRequestDto(
    rutaId = rutaId,
    puntoId = puntoId,
    material = material,
    pesoBruto = pesoBruto,
    tara = tara,
    observacion = observacion,
    humedadEstimada = humedadEstimada
)

fun Pesaje.aPeticionCorreccion() = CorreccionRequestDto(
    material = material,
    pesoBruto = pesoBruto,
    tara = tara,
    observacion = observacion,
    humedadEstimada = humedadEstimada
)

fun DatosPesaje.aPeticionCorreccion() = CorreccionRequestDto(
    material = material.trim().uppercase(),
    pesoBruto = pesoBruto ?: 0.0,
    tara = tara ?: 0.0,
    observacion = observacion.trim(),
    humedadEstimada = humedadEstimada
)
