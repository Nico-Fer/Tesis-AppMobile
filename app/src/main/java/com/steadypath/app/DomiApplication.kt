package com.steadypath.app

import android.app.Application
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.kotlin.core.Amplify
import com.steadypath.app.data.sync.Sincronizador
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DomiApplication : Application() {

    @Inject
    lateinit var sincronizador: Sincronizador

    override fun onCreate() {
        super.onCreate()
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
        } catch (e: AmplifyException) {
            Log.e("DomiApp", "Error inicializando Amplify", e)
        }
        // Después de Amplify: el envío de la cola de salida necesita el token de Cognito.
        sincronizador.iniciar()
    }
}

