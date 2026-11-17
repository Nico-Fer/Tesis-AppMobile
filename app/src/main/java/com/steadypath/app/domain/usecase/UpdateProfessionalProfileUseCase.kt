package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class UpdateProfessionalProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        nombre: String,
        tipoProfesional: String?,
        matricula: String?,
        institucion: String?
    ): Result<User> =
        authRepository.updateProfessionalProfile(nombre, tipoProfesional, matricula, institucion)
}
