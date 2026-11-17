package com.steadypath.app.data.datasource

import com.steadypath.app.data.dto.AssignTemplateRequestDto
import com.steadypath.app.data.dto.AssignedProfessionalDto
import com.steadypath.app.data.dto.ComplianceDto
import com.steadypath.app.data.dto.MisDatosDto
import com.steadypath.app.data.dto.CreateInvitationRequestDto
import com.steadypath.app.data.dto.CreateOwnRoutineRequestDto
import com.steadypath.app.data.dto.CreateRoutineRequestDto
import com.steadypath.app.data.dto.CreateSessionRequestDto
import com.steadypath.app.data.dto.CalibrateOwnToleranceRequestDto
import com.steadypath.app.data.dto.CreateTemplateRequestDto
import com.steadypath.app.data.dto.EditRoutineRequestDto
import com.steadypath.app.data.dto.EditTemplateRequestDto
import com.steadypath.app.data.dto.EvaRecordDto
import com.steadypath.app.data.dto.ExerciseDto
import com.steadypath.app.data.dto.ExerciseVideoDto
import com.steadypath.app.data.dto.HealthDto
import com.steadypath.app.data.dto.InvitationDto
import com.steadypath.app.data.dto.OwnToleranceDto
import com.steadypath.app.data.dto.PatientSummaryDto
import com.steadypath.app.data.dto.ProgressReportDto
import com.steadypath.app.data.dto.ProvisionRequestDto
import com.steadypath.app.data.dto.MovementReferenceDto
import com.steadypath.app.data.dto.RoutineComplianceDto
import com.steadypath.app.data.dto.RoutineDto
import com.steadypath.app.data.dto.RoutineStateRequestDto
import com.steadypath.app.data.dto.SaveOwnReferenceRequestDto
import com.steadypath.app.data.dto.SavePatientReferenceRequestDto
import com.steadypath.app.data.dto.SessionDto
import com.steadypath.app.data.dto.TemplateDto
import com.steadypath.app.data.dto.ToleranceDto
import com.steadypath.app.data.dto.ToleranceRequestDto
import com.steadypath.app.data.dto.UpdatePatientProfileRequestDto
import com.steadypath.app.data.dto.UpdateProfessionalProfileRequestDto
import com.steadypath.app.data.dto.UserResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RehabApiService {

    /** Único endpoint sin auth. No toca la base: sirve para chequear conectividad. */
    @GET("health")
    suspend fun getHealth(): Response<HealthDto>

    /** Crea o vincula la ficha del usuario autenticado. Idempotente. */
    @POST("api/usuarios/provision")
    suspend fun provisionUser(
        @Body request: ProvisionRequestDto
    ): Response<UserResponseDto>

    /** Identidad y rol del usuario del token. */
    @GET("api/usuarios/me")
    suspend fun getMe(): Response<UserResponseDto>

    /** Qué datos guarda el sistema sobre el usuario (derecho de acceso, Ley 25.326 art. 14). */
    @GET("api/usuarios/mis-datos")
    suspend fun getMisDatos(): Response<MisDatosDto>

    @GET("api/ejercicios")
    suspend fun getExercises(): Response<List<ExerciseDto>>

    /** Lista vacía = sin videos (caso normal); 404 solo si el ejercicio no existe. */
    @GET("api/ejercicios/{idEjercicio}/videos")
    suspend fun getExerciseVideos(
        @Path("idEjercicio") idEjercicio: String
    ): Response<List<ExerciseVideoDto>>

    @POST("api/sesiones")
    suspend fun saveSession(
        @Body request: CreateSessionRequestDto
    ): Response<SessionDto>

    @GET("api/sesiones/historial/{idPaciente}")
    suspend fun getSessionHistory(
        @Path("idPaciente") idPaciente: String
    ): Response<List<SessionDto>>

    @GET("api/sesiones/eva/{idPaciente}")
    suspend fun getEvaHistory(
        @Path("idPaciente") idPaciente: String
    ): Response<List<EvaRecordDto>>

    @GET("api/sesiones/cumplimiento/{idPaciente}")
    suspend fun getCompliance(
        @Path("idPaciente") idPaciente: String
    ): Response<ComplianceDto>

    /** Reporte agregado de progreso; tzOffsetMinutes = offset del dispositivo respecto de UTC. */
    @GET("api/sesiones/progreso/{idPaciente}")
    suspend fun getProgressReport(
        @Path("idPaciente") idPaciente: String,
        @Query("tzOffsetMinutes") tzOffsetMinutes: Int
    ): Response<ProgressReportDto>

    // --- Rutinas (el paciente o su profesional; 403 si no corresponde) ---

    @GET("api/rutinas/paciente/{idPaciente}")
    suspend fun getPatientRoutines(
        @Path("idPaciente") idPaciente: String,
        @Query("soloActivas") soloActivas: Boolean = true
    ): Response<List<RoutineDto>>

    @GET("api/rutinas/{idRutina}")
    suspend fun getRoutine(
        @Path("idRutina") idRutina: String
    ): Response<RoutineDto>

    /** Rutina propia del paciente autenticado; un profesional recibe 403. */
    @POST("api/rutinas")
    suspend fun createOwnRoutine(
        @Body request: CreateOwnRoutineRequestDto
    ): Response<RoutineDto>

    @GET("api/rutinas/{idRutina}/cumplimiento")
    suspend fun getRoutineCompliance(
        @Path("idRutina") idRutina: String
    ): Response<RoutineComplianceDto>

    @GET("api/rutinas/cumplimiento/paciente/{idPaciente}")
    suspend fun getPatientRoutineCompliance(
        @Path("idPaciente") idPaciente: String,
        @Query("soloActivas") soloActivas: Boolean = true
    ): Response<List<RoutineComplianceDto>>

    /** Solo el profesional a cargo; un paciente recibe 403. Devuelve la rutina actualizada. */
    @PATCH("api/rutinas/{idRutina}/estado")
    suspend fun setRoutineState(
        @Path("idRutina") idRutina: String,
        @Body request: RoutineStateRequestDto
    ): Response<RoutineDto>

    // --- Médicos (403 si el token es de un paciente) ---

    @GET("api/medicos/pacientes")
    suspend fun getMyPatients(): Response<List<PatientSummaryDto>>

    /** Desvincula al paciente de este profesional (no borra su cuenta ni su historial). */
    /** Baja de la cuenta propia: borra los datos del usuario y su identidad de acceso. */
    @DELETE("api/usuarios/mi-cuenta")
    suspend fun deleteOwnAccount(): Response<Unit>

    @DELETE("api/medicos/pacientes/{idPaciente}")
    suspend fun removePatient(
        @Path("idPaciente") idPaciente: String
    ): Response<PatientSummaryDto>

    @POST("api/medicos/rutinas")
    suspend fun createRoutine(
        @Body request: CreateRoutineRequestDto
    ): Response<RoutineDto>

    @POST("api/medicos/tolerancias")
    suspend fun setTolerance(
        @Body request: ToleranceRequestDto
    ): Response<ToleranceDto>

    /** Reemplaza días y ejercicios de una rutina ya asignada. Solo el profesional a cargo. */
    @PUT("api/rutinas/{idRutina}/ejercicios")
    suspend fun editRoutineExercises(
        @Path("idRutina") idRutina: String,
        @Body request: EditRoutineRequestDto
    ): Response<RoutineDto>

    // --- Plantillas de rutina (reutilizables, del profesional) ---

    @POST("api/medicos/plantillas")
    suspend fun createTemplate(
        @Body request: CreateTemplateRequestDto
    ): Response<TemplateDto>

    @GET("api/medicos/plantillas")
    suspend fun getTemplates(): Response<List<TemplateDto>>

    @GET("api/medicos/plantillas/{idPlantilla}")
    suspend fun getTemplate(
        @Path("idPlantilla") idPlantilla: String
    ): Response<TemplateDto>

    @PUT("api/medicos/plantillas/{idPlantilla}")
    suspend fun editTemplate(
        @Path("idPlantilla") idPlantilla: String,
        @Body request: EditTemplateRequestDto
    ): Response<TemplateDto>

    /** Crea una rutina real para el paciente indicado, copiando los ejercicios de la plantilla. */
    @POST("api/medicos/plantillas/{idPlantilla}/asignar")
    suspend fun assignTemplate(
        @Path("idPlantilla") idPlantilla: String,
        @Body request: AssignTemplateRequestDto
    ): Response<RoutineDto>

    // --- Invitaciones a pacientes ---

    @POST("api/medicos/invitaciones")
    suspend fun createInvitation(
        @Body request: CreateInvitationRequestDto
    ): Response<InvitationDto>

    @GET("api/medicos/invitaciones")
    suspend fun getInvitations(): Response<List<InvitationDto>>

    @DELETE("api/medicos/invitaciones/{idInvitacion}")
    suspend fun cancelInvitation(
        @Path("idInvitacion") idInvitacion: String
    ): Response<InvitationDto>

    // --- Perfil del profesional ---

    @PUT("api/usuarios/perfil-profesional")
    suspend fun updateProfessionalProfile(
        @Body request: UpdateProfessionalProfileRequestDto
    ): Response<UserResponseDto>

    // --- Perfil del paciente ---

    @PUT("api/usuarios/perfil-paciente")
    suspend fun updatePatientProfile(
        @Body request: UpdatePatientProfileRequestDto
    ): Response<UserResponseDto>

    @DELETE("api/pacientes/mi-profesional")
    suspend fun unlinkProfessional(): Response<UserResponseDto>

    @GET("api/pacientes/mi-profesional")
    suspend fun getMyProfessional(): Response<AssignedProfessionalDto>

    // --- Consentimiento (RF-48): revocar bloquea los datos, no los borra ---

    @POST("api/pacientes/consentimiento/revocar")
    suspend fun revokeConsent(): Response<UserResponseDto>

    @POST("api/pacientes/consentimiento/otorgar")
    suspend fun grantConsent(): Response<UserResponseDto>

    // --- Invitaciones recibidas por el paciente ---

    @GET("api/pacientes/invitaciones")
    suspend fun getMyInvitations(): Response<List<InvitationDto>>

    @POST("api/pacientes/invitaciones/{idInvitacion}/aceptar")
    suspend fun acceptInvitation(
        @Path("idInvitacion") idInvitacion: String
    ): Response<InvitationDto>

    @POST("api/pacientes/invitaciones/{idInvitacion}/rechazar")
    suspend fun rejectInvitation(
        @Path("idInvitacion") idInvitacion: String
    ): Response<InvitationDto>

    // --- Tolerancia propia (autocalibración) ---

    @PUT("api/pacientes/tolerancias")
    suspend fun calibrateOwnTolerance(
        @Body request: CalibrateOwnToleranceRequestDto
    ): Response<OwnToleranceDto>

    @GET("api/pacientes/tolerancias/{idEjercicio}")
    suspend fun getOwnTolerance(
        @Path("idEjercicio") idEjercicio: String
    ): Response<OwnToleranceDto>

    // --- Referencias de movimiento DTW (una por paciente+ejercicio, upsert) ---

    @PUT("api/pacientes/referencias")
    suspend fun saveOwnReference(
        @Body request: SaveOwnReferenceRequestDto
    ): Response<MovementReferenceDto>

    @GET("api/pacientes/referencias/{idEjercicio}")
    suspend fun getOwnReference(
        @Path("idEjercicio") idEjercicio: String
    ): Response<MovementReferenceDto>

    /** El profesional guarda la referencia de un paciente a su cargo (403 si no lo es). */
    @POST("api/medicos/referencias")
    suspend fun savePatientReference(
        @Body request: SavePatientReferenceRequestDto
    ): Response<MovementReferenceDto>
}
