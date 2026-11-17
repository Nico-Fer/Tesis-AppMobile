package com.steadypath.app.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Agrega `Authorization: Bearer <ID token>` a todo request salvo /health. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authService: CognitoAuthService
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath == "/health") {
            return chain.proceed(request)
        }
        val token = runBlocking { authService.fetchIdToken() }
            ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
