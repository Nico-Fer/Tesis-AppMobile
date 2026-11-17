package com.steadypath.app.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ante un 401 renueva el ID token con el refresh token y reintenta UNA vez.
 * Si el 401 persiste (token del tipo equivocado, refresh vencido), no insiste:
 * la app debe mandar al usuario al login.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authService: CognitoAuthService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null

        val freshToken = runBlocking { authService.fetchIdToken(forceRefresh = true) }
            ?: return null

        val alreadySent = response.request.header("Authorization")
        if (alreadySent == "Bearer $freshToken") return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }
}
