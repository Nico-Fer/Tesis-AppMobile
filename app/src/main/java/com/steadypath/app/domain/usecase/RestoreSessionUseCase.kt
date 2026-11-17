package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

/** Identidad al arrancar la app; null = no hay sesión, ir a Login. */
class RestoreSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): User? = authRepository.restoreSession()
}
