package com.steadypath.app.presentation.navigation

import kotlinx.serialization.Serializable

// Graph containers
@Serializable data object AuthGraph
@Serializable data object PatientGraph
@Serializable data object ProfesionalGraph

// Auth screens
@Serializable data object Splash
@Serializable data object RoleSelection
@Serializable data object PatientRegistration
@Serializable data object DoctorRegistration
@Serializable data object Login
// Solo viaja el email; la contraseña queda en memoria (PendingRegistrationHolder)
@Serializable data class ConfirmSignUp(val email: String)
// Recuperación de contraseña (RF-03); arranca con el email ya escrito en el login
@Serializable data class ForgotPassword(val email: String = "")

// Patient screens
@Serializable data object Dashboard
@Serializable data object Progress
@Serializable data object RoutineList
@Serializable data class RoutineDetail(val routineId: String)
@Serializable data class EditMyRoutine(val routineId: String)
@Serializable data object CreateRoutine
@Serializable data object ExerciseList
@Serializable data class ExerciseDetail(val exerciseId: String)
// Secuencia previa al ejercicio: PrepareSpace (encuadre) → ExerciseCountdown →
// RealtimeExercise. La referencia propia la exige antes el detalle del ejercicio.
@Serializable data class PrepareSpace(val exerciseId: String, val exerciseName: String)
@Serializable data class ExerciseCountdown(val exerciseId: String, val exerciseName: String)
@Serializable data class PainReport(val exerciseId: String, val exerciseName: String)
@Serializable data class RealtimeExercise(val exerciseId: String, val exerciseName: String)
@Serializable data class SessionSummary(val durationMinutes: Int)
@Serializable data object PatientProfile
@Serializable data object EditPatientProfile
@Serializable data object PatientNotificationSettings
@Serializable data object PatientCameraPreference
@Serializable data object ProfessionalCameraPreference
// Rutas propias en vez de reusar ChangePassword/Privacy del profesional: registrar el
// mismo objeto de ruta en dos grafos anidados distintos es ambiguo para el NavController.
@Serializable data object PatientChangePassword
@Serializable data object PatientPrivacy
/** Mis datos (derecho de acceso): una ruta por grafo, como Privacy. */
@Serializable data object PatientMyData
@Serializable data object ProfessionalMyData
/** Privacidad accesible antes de registrarse: quien decide si acepta debe poder leerla. */
@Serializable data object SignUpPrivacy

// Herramienta interna (solo visible en builds DEBUG): captura de referencias de movimiento
@Serializable data object ReferenceCapture

// Grabación de referencia DTW en producción. Clases separadas por grafo (ver comentario
// sobre PatientChangePassword): el paciente graba la propia, el profesional para un paciente.
@Serializable data class RecordOwnReference(
    val exerciseId: String,
    val exerciseName: String,
    val patternSlug: String
)

// Profesional screens
@Serializable data object ProfessionalHome
@Serializable data object PatientList
@Serializable data class PatientDetail(val patientId: String, val patientName: String, val edad: Int? = null)
@Serializable data class PatientRoutines(val patientId: String, val patientName: String)
@Serializable data class ChooseAssignMethod(val patientId: String, val patientName: String)
@Serializable data class ChooseTemplateForPatient(val patientId: String, val patientName: String)
@Serializable data class AssignRoutine(val patientId: String, val patientName: String)
@Serializable data class ProfessionalRoutineDetail(
    val routineId: String,
    val patientId: String,
    val patientName: String
)
@Serializable data class EditAssignedRoutine(
    val routineId: String,
    val patientId: String,
    val patientName: String
)
@Serializable data object RoutineTemplateLibrary
@Serializable data object CreateTemplate
@Serializable data class TemplateDetail(val templateId: String)
@Serializable data class ChoosePatientForTemplate(val templateId: String, val templateName: String)
@Serializable data class ConfirmAssignTemplate(
    val templateId: String,
    val templateName: String,
    val patientId: String,
    val patientName: String
)
@Serializable data object ProfessionalProfile
@Serializable data object EditProfessionalProfile
@Serializable data object NotificationSettings
@Serializable data object ChangePassword
@Serializable data object Privacy

// Sin entrada de navegación por ahora: ninguno de los mockups de Stitch la contempla.
@Serializable data class AdjustTolerance(
    val patientId: String,
    val exerciseId: String,
    val exerciseName: String
)

// Grabación de referencia DTW por el profesional: elige el ejercicio y graba frente a cámara.
@Serializable data class ChooseExerciseForReference(val patientId: String, val patientName: String)
@Serializable data class RecordPatientReference(
    val patientId: String,
    val exerciseId: String,
    val exerciseName: String,
    val patternSlug: String
)
