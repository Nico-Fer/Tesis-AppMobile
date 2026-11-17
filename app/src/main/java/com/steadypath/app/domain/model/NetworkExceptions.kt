package com.steadypath.app.domain.model

/** Sin red: el request no llegó a salir o la respuesta no volvió. Reintentable. */
class SinConexionException(message: String) : Exception(message)

/** HTTP 5xx: el servidor no pudo atender el pedido. Reintentable. */
class ServidorNoDisponibleException(message: String) : Exception(message)

/** HTTP 401: falta el token o venció y no se pudo renovar. Reintentable con la sesión renovada. */
class NoAutenticadoException(message: String) : Exception(message)

/**
 * HTTP 404: lo pedido no existe o dejó de existir para este usuario. Definitivo. Se
 * distingue de un error cualquiera porque es la señal para tirar la copia guardada en el
 * teléfono: seguir mostrándola sería mostrar algo que el servidor ya no reconoce.
 */
class NoEncontradoException(message: String) : Exception(message)

/**
 * HTTP 409 que no es "usuario sin provisionar": el pedido choca con el estado de los datos
 * (una invitación ya pendiente, un identificador de sesión de otro paciente). Definitivo:
 * reintentarlo da el mismo resultado. El mensaje es el del servidor.
 */
class ConflictoException(message: String) : Exception(message)

/**
 * La sesión de ejercicio no se pudo enviar por un problema transitorio y quedó guardada
 * en el teléfono; se envía sola cuando vuelve la conexión. No es una pérdida de datos.
 */
class SesionEnEsperaException : Exception("La sesión quedó guardada en el teléfono")
