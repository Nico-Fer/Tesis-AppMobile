package com.steadypath.app

import android.app.Application
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.kotlin.core.Amplify
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DomiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
        } catch (e: AmplifyException) {
            Log.e("DomiApp", "Error inicializando Amplify", e)
        }
    }
}

