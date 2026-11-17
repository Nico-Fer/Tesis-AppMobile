package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class UpdatePatientProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(nombre: String, telefono: String?): Result<User> =
        authRepository.updatePatientProfile(nombre, telefono)
}
