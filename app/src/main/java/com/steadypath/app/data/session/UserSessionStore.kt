package com.steadypath.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.UserSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

/** Los tokens los guarda Amplify; acá solo persiste la identidad (id, nombre, rol). */
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
    }

    override suspend fun save(user: User) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ID_USUARIO] = user.idUsuario
            prefs[Keys.NOMBRE] = user.nombre
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.ROL] = user.rol
            prefs[Keys.FECHA_REGISTRO] = user.fechaRegistro
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
            fechaRegistro = prefs[Keys.FECHA_REGISTRO].orEmpty()
        )
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
