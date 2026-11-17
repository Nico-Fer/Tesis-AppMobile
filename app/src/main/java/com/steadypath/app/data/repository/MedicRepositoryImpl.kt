package com.steadypath.app.data.repository

import com.google.gson.reflect.TypeToken
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.AssignTemplateRequestDto
import com.steadypath.app.data.dto.CreateInvitationRequestDto
import com.steadypath.app.data.dto.CreateRoutineExerciseDto
import com.steadypath.app.data.dto.CreateRoutineRequestDto
import com.steadypath.app.data.dto.CreateTemplateRequestDto
import com.steadypath.app.data.dto.EditTemplateRequestDto
import com.steadypath.app.data.dto.InvitationDto
import com.steadypath.app.data.dto.PatientSummaryDto
import com.steadypath.app.data.dto.TemplateDto
import com.steadypath.app.data.dto.ToleranceRequestDto
import com.steadypath.app.data.local.LecturaConCache
import com.steadypath.app.data.local.RutinaDao
import com.steadypath.app.data.local.SesionDao
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.model.Tolerance
import com.steadypath.app.domain.repository.MedicRepository
import com.steadypath.app.domain.repository.RoutineExerciseInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * El profesional puede consultar sin red lo último que vio (pacientes, plantillas,
 * invitaciones); todo lo que modifica algo sigue necesitando conexión, porque afecta a
 * otra persona y encolarlo arriesgaría pisar cambios hechos desde otro dispositivo.
 */
class MedicRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller,
    private val lectura: LecturaConCache,
    private val rutinaDao: RutinaDao,
    private val sesionDao: SesionDao
) : MedicRepository {

    override fun getMyPatients(): Flow<Result<List<PatientSummary>>> = lectura.flujo(
        leer = { leerPacientes()?.map { it.toDomain() } },
        llamar = { caller.execute({ apiService.getMyPatients() }) { it } },
        guardar = { pacientes ->
            olvidarALosQueYaNoCorresponden(anteriores = leerPacientes().orEmpty(), actuales = pacientes)
            lectura.guardarInstantanea(CLAVE_PACIENTES, pacientes)
        },
        borrar = { lectura.olvidar(CLAVE_PACIENTES) },
        aDominio = { pacientes -> pacientes.map { it.toDomain() } }
    )

    private suspend fun leerPacientes(): List<PatientSummaryDto>? =
        lectura.leerInstantanea(CLAVE_PACIENTES, object : TypeToken<List<PatientSummaryDto>>() {}.type)

    /**
     * Quien dejó de estar a cargo o revocó el consentimiento no debe seguir visible desde
     * este teléfono: se borra todo lo guardado suyo (rutinas, historial, dolor, progreso),
     * no solo su fila del listado.
     */
    private suspend fun olvidarALosQueYaNoCorresponden(
        anteriores: List<PatientSummaryDto>,
        actuales: List<PatientSummaryDto>
    ) {
        pacientesAOlvidar(anteriores, actuales).forEach { idPaciente ->
            rutinaDao.idsDePaciente(idPaciente).forEach { lectura.olvidar(it) }
            rutinaDao.borrarDePaciente(idPaciente)
            sesionDao.borrarDePaciente(idPaciente)
            lectura.olvidar(idPaciente)
        }
    }

    override fun removePatient(idPaciente: String): Flow<Result<Unit>> = caller.flowCall(
        call = { apiService.removePatient(idPaciente) },
        map = { }
    )

    override fun createRoutine(
        idPaciente: String,
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> = caller.flowCall(
        call = {
            apiService.createRoutine(
                CreateRoutineRequestDto(
                    idPaciente = idPaciente,
                    nombreRutina = nombreRutina,
                    diasAsignados = diasAsignados.map { it.ordinal },
                    ejercicios = ejercicios.map {
                        CreateRoutineExerciseDto(
                            idEjercicio = it.idEjercicio,
                            repeticionesObjetivo = it.repeticionesObjetivo,
                            series = it.series
                        )
                    }
                )
            )
        },
        map = { it.toDomain() }
    )

    override fun setTolerance(
        idPaciente: String,
        idEjercicio: String,
        anguloMinimo: Double,
        anguloMaximo: Double
    ): Flow<Result<Tolerance>> = caller.flowCall(
        call = {
            apiService.setTolerance(
                ToleranceRequestDto(idPaciente, idEjercicio, anguloMinimo, anguloMaximo)
            )
        },
        map = { it.toDomain() }
    )

    override fun createTemplate(
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>> = caller.flowCall(
        call = { apiService.createTemplate(CreateTemplateRequestDto(nombreRutina, ejercicios.toDtos())) },
        map = { it.toDomain() }
    )

    override fun getTemplates(): Flow<Result<List<RoutineTemplate>>> = lectura.instantanea(
        clave = "plantillas",
        tipo = object : TypeToken<List<TemplateDto>>() {}.type,
        call = { apiService.getTemplates() },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun getTemplate(idPlantilla: String): Flow<Result<RoutineTemplate>> =
        lectura.instantanea(
            clave = "plantilla:$idPlantilla",
            tipo = TemplateDto::class.java,
            call = { apiService.getTemplate(idPlantilla) },
            map = { it.toDomain() }
        )

    override fun editTemplate(
        idPlantilla: String,
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>> = caller.flowCall(
        call = { apiService.editTemplate(idPlantilla, EditTemplateRequestDto(nombreRutina, ejercicios.toDtos())) },
        map = { it.toDomain() }
    )

    override fun assignTemplate(
        idPlantilla: String,
        idPaciente: String,
        diasAsignados: List<DiaSemana>
    ): Flow<Result<Routine>> = caller.flowCall(
        call = {
            apiService.assignTemplate(
                idPlantilla,
                AssignTemplateRequestDto(idPaciente, diasAsignados.map { it.ordinal })
            )
        },
        map = { it.toDomain() }
    )

    override fun inviteByEmail(email: String): Flow<Result<Invitation>> = caller.flowCall(
        call = { apiService.createInvitation(CreateInvitationRequestDto(email)) },
        map = { it.toDomain() }
    )

    override fun getInvitations(): Flow<Result<List<Invitation>>> = lectura.instantanea(
        clave = "invitacionesDelProfesional",
        tipo = object : TypeToken<List<InvitationDto>>() {}.type,
        call = { apiService.getInvitations() },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun cancelInvitation(idInvitacion: String): Flow<Result<Unit>> = caller.flowCall(
        call = { apiService.cancelInvitation(idInvitacion) },
        map = { }
    )

    private fun List<RoutineExerciseInput>.toDtos(): List<CreateRoutineExerciseDto> = map {
        CreateRoutineExerciseDto(
            idEjercicio = it.idEjercicio,
            repeticionesObjetivo = it.repeticionesObjetivo,
            series = it.series
        )
    }

    private companion object {
        const val CLAVE_PACIENTES = "pacientesDelProfesional"
    }
}

/**
 * Pacientes cuya información guardada hay que borrar al llegar un listado nuevo: los que
 * estaban y ya no están (desvinculados) y los que figuran con el consentimiento revocado.
 */
internal fun pacientesAOlvidar(
    anteriores: List<PatientSummaryDto>,
    actuales: List<PatientSummaryDto>
): Set<String> {
    val vigentes = actuales.map { it.idPaciente }.toSet()
    val desvinculados = anteriores.map { it.idPaciente }.filterNot { it in vigentes }
    val revocaron = actuales.filter { it.consentimientoRevocado }.map { it.idPaciente }
    return (desvinculados + revocaron).toSet()
}
