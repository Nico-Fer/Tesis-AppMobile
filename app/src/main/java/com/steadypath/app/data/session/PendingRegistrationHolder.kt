package com.steadypath.app.data.session

import com.steadypath.app.domain.model.ProvisionData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Datos del registro en curso, solo en memoria (la contraseña nunca se
 * persiste ni viaja en rutas de navegación). Si el proceso muere, el flujo
 * degrada a login manual tras confirmar el código.
 */
@Singleton
class PendingRegistrationHolder @Inject constructor() {

    var email: String? = null
        private set
    var password: String? = null
        private set
    var provisionData: ProvisionData? = null
        private set

    fun store(email: String, password: String, data: ProvisionData) {
        this.email = email
        this.password = password
        this.provisionData = data
    }

    fun clear() {
        email = null
        password = null
        provisionData = null
    }
}
