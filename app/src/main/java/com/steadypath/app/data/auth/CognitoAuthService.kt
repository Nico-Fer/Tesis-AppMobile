package com.steadypath.app.data.auth

import android.util.Log
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.cognito.result.AWSCognitoAuthSignOutResult
import com.amplifyframework.auth.options.AuthFetchSessionOptions
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.amplifyframework.kotlin.core.Amplify
import com.steadypath.app.domain.model.AccountAlreadyExistsException
import com.steadypath.app.domain.model.UserNotConfirmedException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CognitoSignInResult {
    data object SignedIn : CognitoSignInResult
    data object NeedsConfirmation : CognitoSignInResult
    data object NotSignedIn : CognitoSignInResult
}

/**
 * Único punto de contacto con Amplify/Cognito. El resto de la app no debe
 * importar tipos de Amplify.
 */
@Singleton
class CognitoAuthService @Inject constructor() {

    /**
     * Registra la cuenta; Cognito manda un código de confirmación al email.
     *
     * El [nombre] NO es opcional: el User Pool declara `name` como atributo
     * requerido, así que omitirlo hace fallar el signUp con
     * InvalidParameterException, que Amplify muestra como "One or more
     * parameters are incorrect" — un mensaje que no dice qué parámetro falta.
     *
     * No se puede arreglar quitando el requisito del pool: los atributos del
     * esquema son inmutables, y cambiar `Required` obligaría a recrear el User
     * Pool y perder todas las cuentas.
     */
    suspend fun signUp(email: String, password: String, nombre: String) {
        val options = AuthSignUpOptions.builder()
            .userAttribute(AuthUserAttributeKey.email(), email)
            .userAttribute(AuthUserAttributeKey.name(), nombre)
            .build()
        wrap { Amplify.Auth.signUp(email, password, options) }
    }

    suspend fun confirmSignUp(email: String, code: String) {
        wrap { Amplify.Auth.confirmSignUp(email, code) }
    }

    suspend fun resendSignUpCode(email: String) {
        wrap { Amplify.Auth.resendSignUpCode(email) }
    }

    /**
     * Inicia sesión. Si quedó una sesión de Cognito abierta, la cierra y
     * reintenta con las credenciales que se pasaron.
     *
     * Amplify rechaza loguear encima de una sesión viva ("There is already a
     * user signed in"), y eso dejaba a la persona trabada: una cuenta
     * autenticada pero sin perfil en la base vuelve a la pantalla de login
     * (ver AuthRepositoryImpl.restoreSession) y desde ahí no había forma de
     * entrar ni de completar el registro.
     *
     * Se cierra y se reintenta en lugar de dar por buena la sesión existente,
     * porque podría ser de OTRA cuenta: aceptarla sin más loguearía a la
     * persona equivocada.
     */
    suspend fun signIn(email: String, password: String): CognitoSignInResult = wrap {
        val result = try {
            Amplify.Auth.signIn(email, password)
        } catch (e: Exception) {
            if (e::class.simpleName in SESION_YA_ABIERTA) {
                Log.w(TAG, "Ya había una sesión abierta; se cierra y se reintenta", e)
                signOut()
                Amplify.Auth.signIn(email, password)
            } else {
                throw e
            }
        }
        when {
            result.isSignedIn -> CognitoSignInResult.SignedIn
            result.nextStep.signInStep == AuthSignInStep.CONFIRM_SIGN_UP ->
                CognitoSignInResult.NeedsConfirmation
            else -> CognitoSignInResult.NotSignedIn
        }
    }

    /**
     * Borra los tokens locales y revoca el refresh token en Cognito. Amplify no
     * lanza excepciones acá: el resultado dice si la sesión local se cerró.
     */
    suspend fun signOut() {
        when (val result = Amplify.Auth.signOut()) {
            // Tokens locales borrados; solo falló la revocación remota (p. ej. sin red).
            is AWSCognitoAuthSignOutResult.PartialSignOut ->
                Log.w(TAG, "signOut parcial: no se revocó el refresh token", result.revokeTokenError?.exception)
            // Los tokens siguen guardados: Splash restauraría la sesión.
            is AWSCognitoAuthSignOutResult.FailedSignOut -> {
                Log.e(TAG, "signOut falló", result.exception)
                throw Exception("No se pudo cerrar sesión, intentá de nuevo")
            }
            else -> Unit
        }
    }

    /** Cambia la contraseña del usuario autenticado. Cognito exige la actual para validarla. */
    suspend fun updatePassword(oldPassword: String, newPassword: String) {
        try {
            Amplify.Auth.updatePassword(oldPassword, newPassword)
        } catch (e: Exception) {
            throw when (e::class.simpleName) {
                "NotAuthorizedException" -> Exception("La contraseña actual es incorrecta")
                "InvalidPasswordException" -> Exception(
                    "La contraseña no cumple los requisitos (mínimo 8 caracteres, mayúscula, número y símbolo)"
                )
                "LimitExceededException", "TooManyRequestsException" ->
                    Exception("Demasiados intentos; esperá unos minutos")
                else -> e
            }
        }
    }

    suspend fun isSignedIn(): Boolean =
        runCatching { Amplify.Auth.fetchAuthSession().isSignedIn }.getOrDefault(false)

    /**
     * ID token, NO access token: el backend necesita el claim de email y
     * responde 401 ante el access token.
     */
    suspend fun fetchIdToken(forceRefresh: Boolean = false): String? {
        val options = AuthFetchSessionOptions.builder().forceRefresh(forceRefresh).build()
        val session = runCatching { Amplify.Auth.fetchAuthSession(options) }.getOrNull()
        return (session as? AWSCognitoAuthSession)?.userPoolTokensResult?.value?.idToken
    }

    private inline fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            throw mapError(e)
        }

    // Match por nombre de clase: cubre los tipos de service y de core de Amplify
    // sin acoplar imports a su jerarquía interna.
    private fun mapError(e: Exception): Exception = when (e::class.simpleName) {
        "UserNotConfirmedException" -> UserNotConfirmedException()
        "UsernameExistsException" -> AccountAlreadyExistsException()
        "InvalidPasswordException" -> Exception("La contraseña no cumple los requisitos (mínimo 8 caracteres, mayúscula, número y símbolo)")
        "CodeMismatchException" -> Exception("El código ingresado es incorrecto")
        "CodeExpiredException" -> Exception("El código venció; pedí uno nuevo")
        "UserNotFoundException" -> Exception("No existe una cuenta con ese email")
        "NotAuthorizedException" -> Exception("Email o contraseña incorrectos")
        "LimitExceededException", "TooManyRequestsException" ->
            Exception("Demasiados intentos; esperá unos minutos")
        else -> e
    }

    companion object {
        private const val TAG = "CognitoAuthService"

        /** Nombres de las excepciones de Amplify para "ya hay una sesión abierta". */
        private val SESION_YA_ABIERTA = setOf("SignedInException", "InvalidStateException")
    }
}
