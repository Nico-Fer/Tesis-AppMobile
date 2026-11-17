package com.steadypath.app.data.local

import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineExercise
import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.model.SessionResult

/** Lo creado sin red se lista al final, después de lo que ordenó el servidor. */
const val ORDEN_LOCAL = Int.MAX_VALUE

private const val SEPARADOR_ZONAS = "\n"

private fun List<String>.comoTexto(): String = joinToString(SEPARADOR_ZONAS)

private fun String.comoLista(): List<String> =
    if (isEmpty()) emptyList() else split(SEPARADOR_ZONAS)

fun Exercise.aEntidad(orden: Int) = EjercicioEntity(
    idEjercicio = idEjercicio,
    nombre = nombre,
    descripcion = descripcion,
    urlVideoExplicativo = urlVideoExplicativo,
    zonasAfectadas = zonasAfectadas.comoTexto(),
    nivelDificultad = nivelDificultad,
    duracionMinutos = duracionMinutos,
    patronMovimiento = patronMovimiento,
    orden = orden
)

fun EjercicioEntity.aDominio() = Exercise(
    idEjercicio = idEjercicio,
    nombre = nombre,
    descripcion = descripcion,
    urlVideoExplicativo = urlVideoExplicativo,
    zonasAfectadas = zonasAfectadas.comoLista(),
    nivelDificultad = nivelDificultad,
    duracionMinutos = duracionMinutos,
    patronMovimiento = patronMovimiento
)

fun Routine.aEntidad(pendienteDeSubir: Boolean, orden: Int) = RutinaConEjercicios(
    rutina = RutinaEntity(
        idRutina = idRutina,
        idPaciente = idPaciente,
        idProfesionalAsignador = idProfesionalAsignador,
        nombreRutina = nombreRutina,
        fechaCreacion = fechaCreacion,
        activa = activa,
        diasAsignados = diasAsignados.joinToString(",") { it.ordinal.toString() },
        pendienteDeSubir = pendienteDeSubir,
        orden = orden
    ),
    ejercicios = ejercicios.mapIndexed { indice, ejercicio ->
        RutinaEjercicioEntity(
            idRutinaEjercicio = ejercicio.idRutinaEjercicio,
            idRutina = idRutina,
            idEjercicio = ejercicio.idEjercicio,
            nombreEjercicio = ejercicio.nombreEjercicio,
            repeticionesObjetivo = ejercicio.repeticionesObjetivo,
            series = ejercicio.series,
            duracionMinutos = ejercicio.duracionMinutos,
            zonasAfectadas = ejercicio.zonasAfectadas.comoTexto(),
            nivelDificultad = ejercicio.nivelDificultad,
            orden = indice
        )
    }
)

fun RutinaConEjercicios.aDominio() = Routine(
    idRutina = rutina.idRutina,
    idPaciente = rutina.idPaciente,
    idProfesionalAsignador = rutina.idProfesionalAsignador,
    nombreRutina = rutina.nombreRutina,
    fechaCreacion = rutina.fechaCreacion,
    activa = rutina.activa,
    diasAsignados = rutina.diasAsignados.split(",")
        .mapNotNull { it.toIntOrNull()?.let(DiaSemana.entries::getOrNull) },
    ejercicios = ejercicios.sortedBy { it.orden }.map {
        RoutineExercise(
            idRutinaEjercicio = it.idRutinaEjercicio,
            idEjercicio = it.idEjercicio,
            nombreEjercicio = it.nombreEjercicio,
            repeticionesObjetivo = it.repeticionesObjetivo,
            series = it.series,
            duracionMinutos = it.duracionMinutos,
            zonasAfectadas = it.zonasAfectadas.comoLista(),
            nivelDificultad = it.nivelDificultad
        )
    }
)

fun Session.aEntidad(pendienteDeSubir: Boolean, orden: Int) = SesionConResultados(
    sesion = SesionEntity(
        idSesion = idSesion,
        idPaciente = idPaciente,
        idRutina = idRutina,
        nombreRutina = nombreRutina,
        fechaHoraInicio = fechaHoraInicio,
        duracionMinutos = duracionMinutos,
        estado = estado,
        pendienteDeSubir = pendienteDeSubir,
        orden = orden
    ),
    resultados = resultados.mapIndexed { indice, resultado ->
        ResultadoSesionEntity(
            idResultado = resultado.idResultado,
            idSesion = idSesion,
            idEjercicio = resultado.idEjercicio,
            nombreEjercicio = resultado.nombreEjercicio,
            repeticionesRealizadas = resultado.repeticionesRealizadas,
            repeticionesCorrectas = resultado.repeticionesCorrectas,
            porcentajeCorrectitud = resultado.porcentajeCorrectitud,
            escalaEVA = resultado.escalaEVA,
            orden = indice
        )
    }
)

fun SesionConResultados.aDominio() = Session(
    idSesion = sesion.idSesion,
    idPaciente = sesion.idPaciente,
    idRutina = sesion.idRutina,
    nombreRutina = sesion.nombreRutina,
    fechaHoraInicio = sesion.fechaHoraInicio,
    duracionMinutos = sesion.duracionMinutos,
    estado = sesion.estado,
    resultados = resultados.sortedBy { it.orden }.map {
        SessionResult(
            idResultado = it.idResultado,
            idEjercicio = it.idEjercicio,
            nombreEjercicio = it.nombreEjercicio,
            repeticionesRealizadas = it.repeticionesRealizadas,
            repeticionesCorrectas = it.repeticionesCorrectas,
            porcentajeCorrectitud = it.porcentajeCorrectitud,
            escalaEVA = it.escalaEVA
        )
    }
)

fun PatientTolerance.aEntidad(idUsuario: String, idEjercicio: String) = ToleranciaPropiaEntity(
    idUsuario = idUsuario,
    idEjercicio = idEjercicio.lowercase(),
    tieneCalibracion = tieneCalibracion,
    anguloMinimo = anguloMinimo,
    anguloMaximo = anguloMaximo
)

fun ToleranciaPropiaEntity.aDominio() = PatientTolerance(
    tieneCalibracion = tieneCalibracion,
    anguloMinimo = anguloMinimo,
    anguloMaximo = anguloMaximo
)
