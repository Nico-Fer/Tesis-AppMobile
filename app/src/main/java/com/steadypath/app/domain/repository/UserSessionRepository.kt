package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.User

/** Identidad del usuario logueado, persistida entre arranques. */
interface UserSessionRepository {

    suspend fun save(user: User)

    suspend fun get(): User?

    suspend fun clear()
}
