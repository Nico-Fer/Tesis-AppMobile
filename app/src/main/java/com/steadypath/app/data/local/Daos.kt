package com.steadypath.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EjercicioDao {
    @Query("SELECT * FROM ejercicios ORDER BY orden")
    abstract suspend fun todos(): List<EjercicioEntity>

    @Query("SELECT * FROM ejercicios WHERE idEjercicio IN (:ids)")
    abstract suspend fun porIds(ids: List<String>): List<EjercicioEntity>

    @Query("DELETE FROM ejercicios")
    protected abstract suspend fun borrarTodos()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertar(ejercicios: List<EjercicioEntity>)

    @Transaction
    open suspend fun reemplazar(ejercicios: List<EjercicioEntity>) {
        borrarTodos()
        insertar(ejercicios)
    }
}

@Dao
abstract class RutinaDao {
    @Transaction
    @Query("SELECT * FROM rutinas WHERE idPaciente = :idPaciente ORDER BY orden, fechaCreacion")
    abstract suspend fun dePaciente(idPaciente: String): List<RutinaConEjercicios>

    @Transaction
    @Query("SELECT * FROM rutinas WHERE idRutina = :idRutina")
    abstract suspend fun porId(idRutina: String): RutinaConEjercicios?

    @Query("SELECT idRutina FROM rutinas WHERE idPaciente = :idPaciente")
    abstract suspend fun idsDePaciente(idPaciente: String): List<String>

    @Query("DELETE FROM rutinas WHERE idRutina = :idRutina")
    abstract suspend fun borrar(idRutina: String)

    @Query("DELETE FROM rutinas WHERE idPaciente = :idPaciente")
    abstract suspend fun borrarDePaciente(idPaciente: String)

    @Query(
        "DELETE FROM rutinas WHERE idPaciente = :idPaciente AND pendienteDeSubir = 0 " +
            "AND (:soloActivas = 0 OR activa = 1)"
    )
    protected abstract suspend fun borrarSincronizadas(idPaciente: String, soloActivas: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertarRutina(rutina: RutinaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertarEjercicios(ejercicios: List<RutinaEjercicioEntity>)

    /** Borra antes de insertar para que no queden ejercicios de la versión anterior. */
    @Transaction
    open suspend fun guardar(rutina: RutinaConEjercicios) {
        borrar(rutina.rutina.idRutina)
        insertarRutina(rutina.rutina)
        insertarEjercicios(rutina.ejercicios)
    }

    /**
     * Pisa lo sincronizado del paciente con la respuesta del servidor y conserva lo creado
     * sin red. Con [soloActivas] la respuesta no dice nada de las inactivas, que no se tocan.
     */
    @Transaction
    open suspend fun reemplazarDePaciente(
        idPaciente: String,
        soloActivas: Boolean,
        rutinas: List<RutinaConEjercicios>
    ) {
        borrarSincronizadas(idPaciente, soloActivas)
        rutinas.forEach { guardar(it) }
    }
}

@Dao
abstract class SesionDao {
    /** Primero lo hecho sin red (lo más reciente), después el orden del servidor. */
    @Transaction
    @Query("SELECT * FROM sesiones WHERE idPaciente = :idPaciente ORDER BY pendienteDeSubir DESC, orden")
    abstract suspend fun dePaciente(idPaciente: String): List<SesionConResultados>

    @Query("DELETE FROM sesiones WHERE idSesion = :idSesion")
    abstract suspend fun borrar(idSesion: String)

    @Query("DELETE FROM sesiones WHERE idPaciente = :idPaciente")
    abstract suspend fun borrarDePaciente(idPaciente: String)

    @Query("UPDATE sesiones SET pendienteDeSubir = 0 WHERE idSesion = :idSesion")
    abstract suspend fun marcarSincronizada(idSesion: String)

    @Query("UPDATE sesiones SET idRutina = :idNuevo WHERE idRutina = :idAnterior")
    abstract suspend fun reasignarRutina(idAnterior: String, idNuevo: String)

    @Query("DELETE FROM sesiones WHERE idPaciente = :idPaciente AND pendienteDeSubir = 0")
    protected abstract suspend fun borrarSincronizadas(idPaciente: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertarSesion(sesion: SesionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertarResultados(resultados: List<ResultadoSesionEntity>)

    @Transaction
    open suspend fun guardar(sesion: SesionConResultados) {
        borrar(sesion.sesion.idSesion)
        insertarSesion(sesion.sesion)
        insertarResultados(sesion.resultados)
    }

    /**
     * El historial del servidor reemplaza lo sincronizado. Una sesión pendiente que ya
     * figura en la respuesta fue recibida: queda la versión del servidor.
     */
    @Transaction
    open suspend fun reemplazarDePaciente(idPaciente: String, sesiones: List<SesionConResultados>) {
        borrarSincronizadas(idPaciente)
        sesiones.forEach { guardar(it) }
    }
}

@Dao
interface ToleranciaPropiaDao {
    @Query("SELECT * FROM tolerancias_propias WHERE idUsuario = :idUsuario AND idEjercicio = :idEjercicio")
    suspend fun obtener(idUsuario: String, idEjercicio: String): ToleranciaPropiaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(tolerancia: ToleranciaPropiaEntity)
}

@Dao
interface InstantaneaDao {
    @Query("SELECT * FROM instantaneas WHERE clave = :clave")
    suspend fun obtener(clave: String): InstantaneaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(instantanea: InstantaneaEntity)

    @Query("DELETE FROM instantaneas WHERE clave = :clave")
    suspend fun borrar(clave: String)

    /** [patron] es un LIKE: borra todas las copias que mencionan un identificador. */
    @Query("DELETE FROM instantaneas WHERE clave LIKE :patron")
    suspend fun borrarLasQueCoinciden(patron: String)
}

@Dao
interface OperacionPendienteDao {
    @Insert
    suspend fun insertar(operacion: OperacionPendienteEntity)

    @Query("SELECT * FROM operaciones_pendientes WHERE idUsuario = :idUsuario ORDER BY secuencia")
    suspend fun deUsuario(idUsuario: String): List<OperacionPendienteEntity>

    @Query(
        "SELECT * FROM operaciones_pendientes WHERE idUsuario = :idUsuario AND tipo = :tipo " +
            "AND destino = :destino ORDER BY secuencia DESC LIMIT 1"
    )
    suspend fun ultima(idUsuario: String, tipo: String, destino: String): OperacionPendienteEntity?

    @Query("DELETE FROM operaciones_pendientes WHERE id = :id")
    suspend fun borrar(id: String)

    @Query("DELETE FROM operaciones_pendientes WHERE idUsuario = :idUsuario AND tipo = :tipo AND destino = :destino")
    suspend fun borrarDeDestino(idUsuario: String, tipo: String, destino: String)

    @Query("UPDATE operaciones_pendientes SET cargaJson = :cargaJson WHERE id = :id")
    suspend fun actualizarCarga(id: String, cargaJson: String)

    @Query("SELECT COUNT(*) FROM operaciones_pendientes")
    fun cantidad(): Flow<Int>
}
