package com.steadypath.app.data.sync

import android.content.Context
import com.steadypath.app.data.local.OperacionPendienteDao
import com.steadypath.app.data.local.OperacionPendienteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que se puede hacer sin red y subir después.
 *
 * [seFusiona] distingue lo que reemplaza de lo que se acumula: dos sesiones son dos hechos
 * y se envían las dos; dos referencias del mismo ejercicio o dos ediciones del perfil son
 * la misma cosa pisada, y enviar la vieja solo gastaría una llamada para que la nueva la
 * reemplace enseguida.
 */
enum class TipoDeOperacion(val seFusiona: Boolean) {
    SESION(false),
    RUTINA_PROPIA(false),
    REFERENCIA_PROPIA(true),
    PERFIL_PACIENTE(true)
}

/**
 * Operación hecha sin red. [cargaJson] es el pedido tal cual se armó en el momento, así
 * que se envía con sus datos originales (por ejemplo la fecha real de la sesión) aunque
 * salga días después.
 *
 * [idUsuario] la ata a su dueño: el backend atribuye cada pedido al usuario del token, y
 * enviarla con la cuenta de otra persona se la adjudicaría a quien no corresponde.
 *
 * [destino] identifica sobre qué recae (la sesión, la rutina, el ejercicio de la
 * referencia): es lo que permite fusionar y consultar si hay algo pendiente para eso.
 */
data class OperacionPendiente(
    val id: String,
    val idUsuario: String,
    val tipo: TipoDeOperacion,
    val destino: String,
    val cargaJson: String
)

interface ColaDeOperaciones {
    suspend fun agregar(operacion: OperacionPendiente)
    suspend fun deUsuario(idUsuario: String): List<OperacionPendiente>
    suspend fun quitar(id: String)

    /** Carga de la última operación pendiente de ese tipo sobre ese destino, si hay. */
    suspend fun cargaPendiente(idUsuario: String, tipo: TipoDeOperacion, destino: String): String?
    suspend fun actualizarCarga(id: String, cargaJson: String)

    /** Cuántas operaciones esperan en el teléfono, para avisarle a la persona. */
    fun cantidad(): Flow<Int>
}

@Singleton
class ColaDeOperacionesRoom @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OperacionPendienteDao
) : ColaDeOperaciones {

    override suspend fun agregar(operacion: OperacionPendiente) {
        if (operacion.tipo.seFusiona) {
            dao.borrarDeDestino(operacion.idUsuario, operacion.tipo.name, operacion.destino)
        }
        dao.insertar(
            OperacionPendienteEntity(
                id = operacion.id,
                idUsuario = operacion.idUsuario,
                tipo = operacion.tipo.name,
                destino = operacion.destino,
                cargaJson = operacion.cargaJson
            )
        )
        // Que salga aunque la persona cierre la aplicación antes de recuperar la red.
        SincronizacionWorker.programar(context)
    }

    override suspend fun deUsuario(idUsuario: String): List<OperacionPendiente> =
        dao.deUsuario(idUsuario).mapNotNull { fila ->
            // Un tipo que esta versión no conoce (fila de una versión futura) se deja en paz.
            val tipo = runCatching { TipoDeOperacion.valueOf(fila.tipo) }.getOrNull()
                ?: return@mapNotNull null
            OperacionPendiente(fila.id, fila.idUsuario, tipo, fila.destino, fila.cargaJson)
        }

    override suspend fun quitar(id: String) = dao.borrar(id)

    override suspend fun cargaPendiente(
        idUsuario: String,
        tipo: TipoDeOperacion,
        destino: String
    ): String? = dao.ultima(idUsuario, tipo.name, destino)?.cargaJson

    override suspend fun actualizarCarga(id: String, cargaJson: String) =
        dao.actualizarCarga(id, cargaJson)

    override fun cantidad(): Flow<Int> = dao.cantidad()
}
