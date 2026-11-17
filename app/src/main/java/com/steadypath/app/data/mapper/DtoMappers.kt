package com.steadypath.app.data.mapper

import com.steadypath.app.data.dto.AssignedProfessionalDto
import com.steadypath.app.data.dto.ComplianceDto
import com.steadypath.app.data.dto.DailyActivityDto
import com.steadypath.app.data.dto.EvaRecordDto
import com.steadypath.app.data.dto.ExerciseComplianceDto
import com.steadypath.app.data.dto.ExerciseDto
import com.steadypath.app.data.dto.ExerciseVideoDto
import com.steadypath.app.data.dto.InvitationDto
import com.steadypath.app.data.dto.OwnToleranceDto
import com.steadypath.app.data.dto.PatientSummaryDto
import com.steadypath.app.data.dto.ProgressReportDto
import com.steadypath.app.data.dto.RecentSessionDto
import com.steadypath.app.data.dto.RoutineComplianceDto
import com.steadypath.app.data.dto.RoutineDto
import com.steadypath.app.data.dto.RoutineExerciseDto
import com.steadypath.app.data.dto.SessionDto
import com.steadypath.app.data.dto.SessionResultDto
import com.steadypath.app.data.dto.TemplateDto
import com.steadypath.app.data.dto.TemplateExerciseDto
import com.steadypath.app.data.dto.ToleranceDto
import com.steadypath.app.data.dto.UserResponseDto
import com.steadypath.app.data.dto.ZoneProgressDto
import com.steadypath.app.domain.model.AssignedProfessional
import com.steadypath.app.domain.model.Compliance
import com.steadypath.app.domain.model.DailyActivity
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.ExerciseCompliance
import com.steadypath.app.domain.model.ExerciseVideo
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.PainRecord
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.model.RecentSession
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineCompliance
import com.steadypath.app.domain.model.RoutineExercise
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.model.SessionResult
import com.steadypath.app.domain.model.TemplateExercise
import com.steadypath.app.domain.model.Tolerance
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.model.ZoneProgress

// Enum ZonaCorporal del backend, serializado como número. El orden replica al enum C#.
private val ZONAS = mapOf(
    // Zonas amplias legadas (0-4)
    0 to "Hombros",
    1 to "Brazos",
    2 to "Lumbares",
    3 to "Rodillas",
    4 to "Piernas",
    // Músculos específicos — frente (5-12)
    5 to "Pecho",
    6 to "Abdominales",
    7 to "Bíceps",
    8 to "Antebrazos",
    9 to "Cuádriceps",
    10 to "Aductores",
    11 to "Abductores",
    12 to "Tibiales",
    // Músculos específicos — espalda (13-18)
    13 to "Trapecios",
    14 to "Dorsales",
    15 to "Tríceps",
    16 to "Glúteos",
    17 to "Isquiotibiales",
    18 to "Gemelos"
)

fun OwnToleranceDto.toDomain(): PatientTolerance = PatientTolerance(
    tieneCalibracion = tieneCalibracion,
    anguloMinimo = anguloMinimo,
    anguloMaximo = anguloMaximo
)

fun AssignedProfessionalDto.toDomain(): AssignedProfessional = AssignedProfessional(
    tieneProfesionalAsignado = tieneProfesionalAsignado,
    idProfesional = idProfesional,
    nombre = nombre,
    tipoProfesional = tipoProfesional
)

fun UserResponseDto.toDomain(): User = User(
    idUsuario = idUsuario,
    nombre = nombre,
    email = email,
    rol = rol,
    fechaRegistro = fechaRegistro,
    tipoProfesional = tipoProfesional,
    matricula = matricula,
    institucion = institucion,
    telefono = telefono,
    edad = edad,
    consentimientoRevocado = consentimientoRevocadoEn != null
)

fun ExerciseDto.toDomain(): Exercise = Exercise(
    idEjercicio = idEjercicio,
    nombre = nombre,
    descripcion = descripcion,
    urlVideoExplicativo = urlVideoExplicativo,
    zonasAfectadas = zonasAfectadas.map { ZONAS[it] ?: "Zona $it" },
    nivelDificultad = nivelDificultad,
    duracionMinutos = duracionMinutos,
    patronMovimiento = patronMovimiento
)

fun ExerciseVideoDto.toDomain(): ExerciseVideo = ExerciseVideo(
    nombreArchivo = nombreArchivo,
    url = url
)

fun SessionResultDto.toDomain(): SessionResult = SessionResult(
    idResultado = idResultado,
    idEjercicio = idEjercicio,
    nombreEjercicio = nombreEjercicio,
    repeticionesRealizadas = repeticionesRealizadas,
    repeticionesCorrectas = repeticionesCorrectas,
    porcentajeCorrectitud = porcentajeCorrectitud,
    escalaEVA = escalaEVA
)

fun SessionDto.toDomain(): Session = Session(
    idSesion = idSesion,
    idPaciente = idPaciente.orEmpty(),
    idRutina = idRutina,
    nombreRutina = nombreRutina,
    fechaHoraInicio = fechaHoraInicio,
    duracionMinutos = duracionMinutos,
    estado = estado,
    resultados = resultados?.map { it.toDomain() }.orEmpty()
)

fun EvaRecordDto.toDomain(): PainRecord = PainRecord(
    idSesion = idSesion,
    fecha = fecha,
    idEjercicio = idEjercicio,
    nombreEjercicio = nombreEjercicio,
    escalaEVA = escalaEVA
)

fun ComplianceDto.toDomain(): Compliance = Compliance(
    idPaciente = idPaciente,
    porcentajeCumplimiento = porcentajeCumplimiento
)

fun ProgressReportDto.toDomain(): ProgressReport = ProgressReport(
    sesionesTotales = sesionesTotales,
    ejerciciosRealizados = ejerciciosRealizados,
    minutosTotales = minutosTotales,
    diasActivos = diasActivos,
    rachaDias = rachaDias,
    promedioEva = promedioEVA,
    actividadSemanal = actividadSemanal?.map { it.toDomain() }.orEmpty(),
    progresoPorZona = progresoPorZona?.map { it.toDomain() }.orEmpty(),
    historialReciente = historialReciente?.map { it.toDomain() }.orEmpty()
)

fun DailyActivityDto.toDomain(): DailyActivity = DailyActivity(
    fecha = fecha,
    minutos = minutos,
    sesiones = sesiones
)

fun ZoneProgressDto.toDomain(): ZoneProgress = ZoneProgress(
    zona = zona,
    porcentajeCumplimiento = porcentajeCumplimiento
)

fun RecentSessionDto.toDomain(): RecentSession = RecentSession(
    idSesion = idSesion,
    nombreRutina = nombreRutina,
    fechaHoraInicio = fechaHoraInicio,
    duracionMinutos = duracionMinutos,
    estado = estado,
    resultados = resultados?.map { it.toDomain() }.orEmpty()
)

fun RoutineExerciseDto.toDomain(): RoutineExercise = RoutineExercise(
    idRutinaEjercicio = idRutinaEjercicio,
    idEjercicio = idEjercicio,
    nombreEjercicio = nombreEjercicio.orEmpty(),
    repeticionesObjetivo = repeticionesObjetivo,
    series = series,
    duracionMinutos = duracionMinutos,
    zonasAfectadas = zonasAfectadas.map { ZONAS[it] ?: "Zona $it" },
    nivelDificultad = nivelDificultad.orEmpty()
)

fun RoutineDto.toDomain(): Routine = Routine(
    idRutina = idRutina,
    idPaciente = idPaciente.orEmpty(),
    idProfesionalAsignador = idProfesionalAsignador,
    nombreRutina = nombreRutina,
    fechaCreacion = fechaCreacion.orEmpty(),
    activa = activa,
    diasAsignados = diasAsignados.orEmpty().mapNotNull { DiaSemana.entries.getOrNull(it) },
    ejercicios = ejercicios?.map { it.toDomain() }.orEmpty()
)

fun RoutineComplianceDto.toDomain(): RoutineCompliance = RoutineCompliance(
    idRutina = idRutina,
    nombreRutina = nombreRutina,
    activa = activa,
    sesionesRegistradas = sesionesRegistradas,
    repeticionesObjetivo = repeticionesObjetivo,
    repeticionesCorrectas = repeticionesCorrectas,
    porcentajeCumplimiento = porcentajeCumplimiento,
    ejercicios = ejercicios?.map { it.toDomain() }.orEmpty()
)

fun ExerciseComplianceDto.toDomain(): ExerciseCompliance = ExerciseCompliance(
    idEjercicio = idEjercicio,
    nombreEjercicio = nombreEjercicio,
    repeticionesObjetivo = repeticionesObjetivo,
    repeticionesCorrectas = repeticionesCorrectas,
    porcentajeCumplimiento = porcentajeCumplimiento
)

fun PatientSummaryDto.toDomain(): PatientSummary = PatientSummary(
    idPaciente = idPaciente,
    nombre = nombre,
    email = email,
    edad = edad,
    cantidadRutinasActivas = cantidadRutinasActivas,
    fechaUltimaSesion = fechaUltimaSesion,
    consentimientoRevocado = consentimientoRevocado
)

fun ToleranceDto.toDomain(): Tolerance = Tolerance(
    idTolerancia = idTolerancia,
    idPaciente = idPaciente,
    idEjercicio = idEjercicio,
    anguloMinimo = anguloMinimo,
    anguloMaximo = anguloMaximo
)

fun TemplateExerciseDto.toDomain(): TemplateExercise = TemplateExercise(
    idPlantillaRutinaEjercicio = idPlantillaRutinaEjercicio,
    idEjercicio = idEjercicio,
    nombreEjercicio = nombreEjercicio,
    repeticionesObjetivo = repeticionesObjetivo,
    series = series
)

fun TemplateDto.toDomain(): RoutineTemplate = RoutineTemplate(
    idPlantilla = idPlantilla,
    nombreRutina = nombreRutina,
    fechaCreacion = fechaCreacion.orEmpty(),
    cantidadPacientesAsignados = cantidadPacientesAsignados,
    ejercicios = ejercicios.orEmpty().map { it.toDomain() }
)

fun InvitationDto.toDomain(): Invitation = Invitation(
    idInvitacion = idInvitacion,
    email = email,
    estado = estado,
    fechaInvitacion = fechaInvitacion,
    nombreProfesional = nombreProfesional
)
