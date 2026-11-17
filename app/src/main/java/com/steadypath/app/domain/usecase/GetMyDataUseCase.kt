package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.MisDatos
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Derecho de acceso (Ley 25.326, art. 14): la persona ve qué datos guarda el sistema sobre
 * ella desde la app, sin depender de que alguien conteste un pedido por escrito.
 */
class GetMyDataUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<MisDatos> = authRepository.getMyData()
}
