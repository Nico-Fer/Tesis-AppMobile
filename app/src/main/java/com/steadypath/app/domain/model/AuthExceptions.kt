package com.steadypath.app.domain.model

/** La cuenta existe en Cognito pero falta confirmar el código de email. */
class UserNotConfirmedException : Exception("La cuenta no está confirmada")

/** Autenticado en Cognito pero sin ficha en el backend (HTTP 409). */
class NotProvisionedException : Exception("El usuario no fue provisionado")

/** Ya hay una cuenta en Cognito con ese email. */
class AccountAlreadyExistsException : Exception("Ya existe una cuenta con ese email")

/** HTTP 403: el token es válido pero el usuario no puede hacer eso. No reintentar. */
class PermissionDeniedException(
    message: String = "No tenés permisos para esta acción"
) : Exception(message)
