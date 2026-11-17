package com.steadypath.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.UserSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

/**
 * Los tokens los guarda Amplify; acá persiste la identidad completa (la que devuelve
 * `/me`), incluido si el paciente revocó el consentimiento. Es lo que permite entrar y ver
 * o editar el perfil sin red: antes solo se guardaban id, nombre y rol, y el resto se
 * perdía al no poder consultar al servidor.
 */
@Singleton
class UserSessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) : UserSessionRepository {

    private object Keys {
        val ID_USUARIO = stringPreferencesKey("id_usuario")
        val NOMBRE = stringPreferencesKey("nombre")
        val EMAIL = stringPreferencesKey("email")
        val ROL = stringPreferencesKey("rol")
        val FECHA_REGISTRO = stringPreferencesKey("fecha_registro")
        val CONSENTIMIENTO_REVOCADO = booleanPreferencesKey("consentimiento_revocado")
        val TELEFONO = stringPreferencesKey("telefono")
        val EDAD = intPreferencesKey("edad")
        val TIPO_PROFESIONAL = stringPreferencesKey("tipo_profesional")
        val MATRICULA = stringPreferencesKey("matricula")
        val INSTITUCION = stringPreferencesKey("institucion")
    }

    override suspend fun save(user: User) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ID_USUARIO] = user.idUsuario
            prefs[Keys.NOMBRE] = user.nombre
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.ROL] = user.rol
            prefs[Keys.FECHA_REGISTRO] = user.fechaRegistro
            prefs[Keys.CONSENTIMIENTO_REVOCADO] = user.consentimientoRevocado
            prefs.guardarOQuitar(Keys.TELEFONO, user.telefono)
            prefs.guardarOQuitar(Keys.EDAD, user.edad)
            prefs.guardarOQuitar(Keys.TIPO_PROFESIONAL, user.tipoProfesional)
            prefs.guardarOQuitar(Keys.MATRICULA, user.matricula)
            prefs.guardarOQuitar(Keys.INSTITUCION, user.institucion)
        }
    }

    override suspend fun get(): User? {
        val prefs = context.sessionDataStore.data.first()
        val id = prefs[Keys.ID_USUARIO] ?: return null
        return User(
            idUsuario = id,
            nombre = prefs[Keys.NOMBRE].orEmpty(),
            email = prefs[Keys.EMAIL].orEmpty(),
            rol = prefs[Keys.ROL].orEmpty(),
            fechaRegistro = prefs[Keys.FECHA_REGISTRO].orEmpty(),
            consentimientoRevocado = prefs[Keys.CONSENTIMIENTO_REVOCADO] ?: false,
            telefono = prefs[Keys.TELEFONO],
            edad = prefs[Keys.EDAD],
            tipoProfesional = prefs[Keys.TIPO_PROFESIONAL],
            matricula = prefs[Keys.MATRICULA],
            institucion = prefs[Keys.INSTITUCION]
        )
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    /** Un dato que el usuario borró no debe quedar con el valor anterior. */
    private fun <T> MutablePreferences.guardarOQuitar(clave: Preferences.Key<T>, valor: T?) {
        if (valor == null) remove(clave) else set(clave, valor)
    }
}
