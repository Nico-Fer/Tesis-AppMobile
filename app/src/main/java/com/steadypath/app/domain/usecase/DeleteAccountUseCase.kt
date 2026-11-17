package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Baja de la cuenta propia. Sirve a los dos roles: el backend resuelve qué corresponde
 * borrar según quién sea el usuario autenticado.
 *
 * Materializa el derecho de supresión del artículo 16 de la Ley 25.326, que la pantalla
 * de privacidad venía informando sin ofrecer manera de ejercerlo.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.deleteAccount()
}
