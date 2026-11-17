package com.steadypath.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Tablas del modo sin conexión. Guardan la última copia conocida de lo que devuelve el
 * backend más lo que la persona hizo sin red y todavía no se subió. Todo es del usuario
 * con sesión abierta: se vacía al cerrar sesión (ver [BaseLocalDelUsuario]).
 *
 * Las listas simples (zonas, días) van como texto separado y no en tablas hijas: nunca
 * se consultan por separado y se leen siempre enteras.
 */

@Entity(tableName = "ejercicios")
data class EjercicioEntity(
    @PrimaryKey val idEjercicio: String,
    val nombre: String,
    val descripcion: String,
    val urlVideoExplicativo: String?,
    /** Nombres de zona separados por salto de línea. */
    val zonasAfectadas: String,
    val nivelDificultad: String,
    val duracionMinutos: Int,
    val patronMovimiento: String?,
    /** Posición en la respuesta del servidor: el catálogo se muestra en ese orden. */
    val orden: Int
)

@Entity(tableName = "rutinas", indices = [Index("idPaciente")])
data class RutinaEntity(
    @PrimaryKey val idRutina: String,
    val idPaciente: String,
    val idProfesionalAsignador: String?,
    val nombreRutina: String,
    val fechaCreacion: String,
    val activa: Boolean,
    /** Ordinales de [com.steadypath.app.domain.model.DiaSemana] separados por coma. */
    val diasAsignados: String,
    /** Creada sin red: existe solo en el teléfono hasta que la cola de salida la suba. */
    val pendienteDeSubir: Boolean,
    val orden: Int
)

@Entity(
    tableName = "rutina_ejercicios",
    foreignKeys = [
        ForeignKey(
            entity = RutinaEntity::class,
            parentColumns = ["idRutina"],
            childColumns = ["idRutina"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idRutina")]
)
data class RutinaEjercicioEntity(
    @PrimaryKey val idRutinaEjercicio: String,
    val idRutina: String,
    val idEjercicio: String,
    val nombreEjercicio: String,
    val repeticionesObjetivo: Int,
    val series: Int,
    val duracionMinutos: Int,
    val zonasAfectadas: String,
    val nivelDificultad: String,
    val orden: Int
)

data class RutinaConEjercicios(
    @Embedded val rutina: RutinaEntity,
    @Relation(parentColumn = "idRutina", entityColumn = "idRutina")
    val ejercicios: List<RutinaEjercicioEntity>
)

@Entity(tableName = "sesiones", indices = [Index("idPaciente")])
data class SesionEntity(
    @PrimaryKey val idSesion: String,
    val idPaciente: String,
    val idRutina: String?,
    val nombreRutina: String?,
    val fechaHoraInicio: String,
    val duracionMinutos: Double,
    val estado: String,
    /** Hecha sin red: el inicio la cuenta como realizada aunque el servidor no la tenga. */
    val pendienteDeSubir: Boolean,
    val orden: Int
)

@Entity(
    tableName = "sesion_resultados",
    foreignKeys = [
        ForeignKey(
            entity = SesionEntity::class,
            parentColumns = ["idSesion"],
            childColumns = ["idSesion"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idSesion")]
)
data class ResultadoSesionEntity(
    @PrimaryKey val idResultado: String,
    val idSesion: String,
    val idEjercicio: String,
    val nombreEjercicio: String?,
    val repeticionesRealizadas: Int,
    val repeticionesCorrectas: Int,
    val porcentajeCorrectitud: Double,
    val escalaEVA: Int?,
    val orden: Int
)

data class SesionConResultados(
    @Embedded val sesion: SesionEntity,
    @Relation(parentColumn = "idSesion", entityColumn = "idSesion")
    val resultados: List<ResultadoSesionEntity>
)

@Entity(tableName = "tolerancias_propias", primaryKeys = ["idUsuario", "idEjercicio"])
data class ToleranciaPropiaEntity(
    val idUsuario: String,
    val idEjercicio: String,
    val tieneCalibracion: Boolean,
    val anguloMinimo: Double?,
    val anguloMaximo: Double?
)

/**
 * Última respuesta de un endpoint cuyo contenido calcula el servidor (progreso,
 * cumplimiento, historial de dolor, listados del profesional): no se puede recomponer en
 * el teléfono, así que se guarda tal cual llegó. [json] vacío = marca de "esta lista ya se
 * sincronizó alguna vez", que distingue una lista vacía de una que nunca se pidió.
 */
@Entity(tableName = "instantaneas")
data class InstantaneaEntity(
    @PrimaryKey val clave: String,
    val json: String,
    val actualizadoEn: Long
)

/** Cola de salida: lo que la persona hizo sin red, en el orden en que lo hizo. */
@Entity(tableName = "operaciones_pendientes", indices = [Index("id", unique = true)])
data class OperacionPendienteEntity(
    @PrimaryKey(autoGenerate = true) val secuencia: Long = 0,
    val id: String,
    val idUsuario: String,
    val tipo: String,
    val destino: String,
    val cargaJson: String
)
