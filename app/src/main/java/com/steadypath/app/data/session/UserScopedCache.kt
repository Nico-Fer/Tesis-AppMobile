package com.steadypath.app.data.session

/**
 * Caché con datos del usuario autenticado que no debe sobrevivir al logout: si otra
 * cuenta entra en el mismo dispositivo, vería lo del anterior. Se registra con
 * `@IntoSet` y [com.steadypath.app.data.repository.AuthRepositoryImpl.signOut] los vacía.
 */
interface UserScopedCache {
    suspend fun clear()
}
