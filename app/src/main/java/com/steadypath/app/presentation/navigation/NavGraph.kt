package com.steadypath.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.steadypath.app.domain.model.UserRole
import com.steadypath.app.presentation.screens.calibration.ExerciseCountdownScreen
import com.steadypath.app.presentation.screens.calibration.PrepareSpaceScreen
import com.steadypath.app.presentation.screens.capture.RecordReferenceScreen
import com.steadypath.app.presentation.screens.capture.ReferenceCaptureScreen
import com.steadypath.app.presentation.screens.confirm.ConfirmSignUpScreen
import com.steadypath.app.presentation.screens.dashboard.DashboardScreen
import com.steadypath.app.presentation.screens.doctor.DoctorRegistrationScreen
import com.steadypath.app.presentation.screens.exercises.ExerciseDetailScreen
import com.steadypath.app.presentation.screens.exercises.ExerciseListScreen
import com.steadypath.app.presentation.screens.login.LoginScreen
import com.steadypath.app.presentation.screens.pain.PainReportScreen
import com.steadypath.app.presentation.screens.patient.EditPatientProfileScreen
import com.steadypath.app.presentation.screens.patient.PatientNotificationSettingsScreen
import com.steadypath.app.presentation.screens.patient.PatientProfileScreen
import com.steadypath.app.presentation.screens.patient.PatientRegistrationScreen
import com.steadypath.app.presentation.screens.settings.CameraPreferenceScreen
import com.steadypath.app.presentation.screens.patient.TextSizeScreen
import com.steadypath.app.presentation.screens.professional.AssignRoutineScreen
import com.steadypath.app.presentation.screens.professional.ChooseAssignMethodScreen
import com.steadypath.app.presentation.screens.professional.ChoosePatientForTemplateScreen
import com.steadypath.app.presentation.screens.professional.ChooseTemplateForPatientScreen
import com.steadypath.app.presentation.screens.professional.ConfirmAssignTemplateScreen
import com.steadypath.app.presentation.screens.professional.CreateTemplateScreen
import com.steadypath.app.presentation.screens.routines.EditAssignedRoutineScreen
import com.steadypath.app.presentation.screens.professional.EditProfessionalProfileScreen
import com.steadypath.app.presentation.screens.professional.NotificationSettingsScreen
import com.steadypath.app.presentation.screens.settings.ChangePasswordScreen
import com.steadypath.app.presentation.screens.settings.PrivacyScreen
import com.steadypath.app.presentation.screens.settings.MyDataScreen
import com.steadypath.app.presentation.screens.professional.ChooseExerciseForReferenceScreen
import com.steadypath.app.presentation.screens.professional.PatientDetailScreen
import com.steadypath.app.presentation.screens.professional.PatientListScreen
import com.steadypath.app.presentation.screens.professional.PatientRoutinesScreen
import com.steadypath.app.presentation.screens.professional.ProfessionalHomeScreen
import com.steadypath.app.presentation.screens.professional.ProfessionalProfileScreen
import com.steadypath.app.presentation.screens.professional.ProfessionalRoutineDetailScreen
import com.steadypath.app.presentation.screens.professional.RoutineTemplateLibraryScreen
import com.steadypath.app.presentation.screens.professional.TemplateDetailScreen
import com.steadypath.app.presentation.screens.progress.ProgressScreen
import com.steadypath.app.presentation.screens.role.RoleSelectionScreen
import com.steadypath.app.presentation.screens.realtime.RealtimeExerciseScreen
import com.steadypath.app.presentation.screens.routines.CreateRoutineScreen
import com.steadypath.app.presentation.screens.routines.RoutineDetailScreen
import com.steadypath.app.presentation.screens.routines.RoutineListScreen
import com.steadypath.app.presentation.screens.session.SessionSummaryScreen
import com.steadypath.app.presentation.screens.splash.SplashScreen

@Composable
fun NavGraph(navController: NavHostController) {

    // Entra al grafo del rol limpiando todo el stack de auth.
    fun navigateToRoleGraph(rol: String) {
        if (rol == UserRole.PROFESIONAL) {
            navController.navigate(ProfesionalGraph) {
                popUpTo(AuthGraph) { inclusive = true }
            }
        } else {
            navController.navigate(PatientGraph) {
                popUpTo(AuthGraph) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthGraph
    ) {
        navigation<AuthGraph>(startDestination = Splash) {
            composable<Splash> {
                SplashScreen(
                    onSessionRestored = { rol -> navigateToRoleGraph(rol) },
                    onNoSession = {
                        navController.navigate(Login) {
                            popUpTo(Splash) { inclusive = true }
                        }
                    }
                )
            }
            composable<Login> {
                LoginScreen(
                    onLoginSuccess = { rol -> navigateToRoleGraph(rol) },
                    onNeedsConfirmation = { email ->
                        navController.navigate(ConfirmSignUp(email))
                    },
                    onRegisterClick = { navController.navigate(RoleSelection) }
                )
            }
            composable<RoleSelection> {
                RoleSelectionScreen(
                    onPatientClick = { navController.navigate(PatientRegistration) },
                    onDoctorClick = { navController.navigate(DoctorRegistration) },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable<PatientRegistration> {
                PatientRegistrationScreen(
                    onRegistrationSuccess = { navigateToRoleGraph(UserRole.PACIENTE) },
                    onPrivacyClick = { navController.navigate(SignUpPrivacy) },
                    onNeedsConfirmation = { email ->
                        navController.navigate(ConfirmSignUp(email))
                    },
                    onLoginClick = {
                        navController.navigate(Login) {
                            popUpTo(Login) { inclusive = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable<DoctorRegistration> {
                DoctorRegistrationScreen(
                    onRegistrationSuccess = { navigateToRoleGraph(UserRole.PROFESIONAL) },
                    onNeedsConfirmation = { email ->
                        navController.navigate(ConfirmSignUp(email))
                    },
                    onLoginClick = {
                        navController.navigate(Login) {
                            popUpTo(Login) { inclusive = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable<SignUpPrivacy> {
                PrivacyScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isPatient = true
                )
            }
            composable<ConfirmSignUp> {
                ConfirmSignUpScreen(
                    onCompleted = { rol -> navigateToRoleGraph(rol) },
                    onGoToLogin = {
                        navController.navigate(Login) {
                            popUpTo(Login) { inclusive = true }
                        }
                    }
                )
            }
        }

        navigation<PatientGraph>(startDestination = Dashboard) {
            composable<Dashboard> {
                DashboardScreen(
                    onRoutinesClick = { navController.navigate(RoutineList) },
                    onProgressClick = { navController.navigate(Progress) },
                    onTodayRoutineClick = { routineId ->
                        navController.navigate(RoutineDetail(routineId))
                    },
                    onSettingsClick = { navController.navigate(PatientProfile) }
                )
            }
            composable<Progress> {
                ProgressScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onRoutinesClick = {
                        navController.navigate(RoutineList) {
                            popUpTo(Dashboard)
                        }
                    },
                    onSettingsClick = { navController.navigate(PatientProfile) }
                )
            }
            composable<RoutineList> {
                RoutineListScreen(
                    onRoutineClick = { routineId ->
                        navController.navigate(RoutineDetail(routineId))
                    },
                    onCreateRoutineClick = { navController.navigate(CreateRoutine) },
                    onProgressClick = {
                        navController.navigate(Progress) {
                            popUpTo(Dashboard)
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onSettingsClick = { navController.navigate(PatientProfile) }
                )
            }
            composable<RoutineDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<RoutineDetail>()
                RoutineDetailScreen(
                    routineId = route.routineId,
                    onExerciseClick = { exerciseId ->
                        navController.navigate(ExerciseDetail(exerciseId))
                    },
                    onSeeCatalogClick = { navController.navigate(ExerciseList) },
                    onStartRoutine = { exerciseId ->
                        navController.navigate(ExerciseDetail(exerciseId))
                    },
                    onEditRoutineClick = { navController.navigate(EditMyRoutine(route.routineId)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<EditMyRoutine> { backStackEntry ->
                val route = backStackEntry.toRoute<EditMyRoutine>()
                EditAssignedRoutineScreen(
                    routineId = route.routineId,
                    onRoutineDeactivated = { navController.popBackStack<RoutineList>(inclusive = false) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<CreateRoutine> {
                CreateRoutineScreen(
                    onRoutineCreated = { navController.popBackStack() },
                    onRecordReference = { id, name, patternSlug ->
                        navController.navigate(RecordOwnReference(id, name, patternSlug))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ReferenceCapture> {
                ReferenceCaptureScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ExerciseList> {
                ExerciseListScreen(
                    onExerciseClick = { exerciseId ->
                        navController.navigate(ExerciseDetail(exerciseId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ExerciseDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<ExerciseDetail>()
                ExerciseDetailScreen(
                    exerciseId = route.exerciseId,
                    onStartExercise = { id, name ->
                        navController.navigate(PrepareSpace(id, name))
                    },
                    onRecordReference = { id, name, patternSlug ->
                        navController.navigate(RecordOwnReference(id, name, patternSlug))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<RecordOwnReference> { backStackEntry ->
                val route = backStackEntry.toRoute<RecordOwnReference>()
                RecordReferenceScreen(
                    exerciseId = route.exerciseId,
                    exerciseName = route.exerciseName,
                    patternSlug = route.patternSlug,
                    patientId = null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<PrepareSpace> { backStackEntry ->
                val route = backStackEntry.toRoute<PrepareSpace>()
                PrepareSpaceScreen(
                    exerciseId = route.exerciseId,
                    exerciseName = route.exerciseName,
                    onStartExercise = { id, name ->
                        navController.navigate(ExerciseCountdown(id, name))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ExerciseCountdown> { backStackEntry ->
                val route = backStackEntry.toRoute<ExerciseCountdown>()
                ExerciseCountdownScreen(
                    exerciseName = route.exerciseName,
                    onCountdownFinished = {
                        navController.navigate(RealtimeExercise(route.exerciseId, route.exerciseName)) {
                            popUpTo<PrepareSpace> { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<RealtimeExercise> { backStackEntry ->
                val route = backStackEntry.toRoute<RealtimeExercise>()
                RealtimeExerciseScreen(
                    exerciseId = route.exerciseId,
                    exerciseName = route.exerciseName,
                    onExerciseCompleted = { _: Int ->
                        navController.navigate(PainReport(route.exerciseId, route.exerciseName)) {
                            popUpTo(ExerciseDetail(route.exerciseId)) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<PainReport> { backStackEntry ->
                val route = backStackEntry.toRoute<PainReport>()
                PainReportScreen(
                    exerciseId = route.exerciseId,
                    exerciseName = route.exerciseName,
                    onNextExercise = { exerciseId ->
                        // Siguiente paso: detalle del ejercicio; "Comenzar ▶" continúa la secuencia
                        navController.navigate(ExerciseDetail(exerciseId)) {
                            popUpTo<RoutineDetail>()
                        }
                    },
                    onFinish = { duration ->
                        navController.navigate(SessionSummary(duration)) {
                            popUpTo(Dashboard)
                        }
                    }
                )
            }
            composable<SessionSummary> { backStackEntry ->
                val route = backStackEntry.toRoute<SessionSummary>()
                SessionSummaryScreen(
                    durationMinutes = route.durationMinutes,
                    onBackToHome = {
                        navController.navigate(Dashboard) {
                            popUpTo(Dashboard) { inclusive = true }
                        }
                    }
                )
            }
            composable<PatientProfile> {
                PatientProfileScreen(
                    onEditProfileClick = { navController.navigate(EditPatientProfile) },
                    onNotificationsClick = { navController.navigate(PatientNotificationSettings) },
                    onChangePasswordClick = { navController.navigate(PatientChangePassword) },
                    onTextSizeClick = { navController.navigate(TextSize) },
                    onCameraClick = { navController.navigate(PatientCameraPreference) },
                    onPrivacyClick = { navController.navigate(PatientPrivacy) },
                    onSignedOut = {
                        navController.navigate(AuthGraph) {
                            popUpTo(PatientGraph) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<EditPatientProfile> {
                EditPatientProfileScreen(
                    onAccountDeleted = {
                        navController.navigate(AuthGraph) {
                            popUpTo(PatientGraph) { inclusive = true }
                        }
                    },
                    onProfileSaved = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<PatientNotificationSettings> {
                PatientNotificationSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<TextSize> {
                TextSizeScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<PatientCameraPreference> {
                CameraPreferenceScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<PatientChangePassword> {
                ChangePasswordScreen(
                    onPasswordChanged = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<PatientPrivacy> {
                PrivacyScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isPatient = true,
                    onViewMyData = { navController.navigate(PatientMyData) }
                )
            }
            composable<PatientMyData> {
                MyDataScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        navigation<ProfesionalGraph>(startDestination = ProfessionalHome) {
            fun signOutToAuth() {
                navController.navigate(AuthGraph) {
                    popUpTo(ProfesionalGraph) { inclusive = true }
                }
            }

            composable<ProfessionalHome> {
                ProfessionalHomeScreen(
                    onPatientsClick = { navController.navigate(PatientList) { popUpTo(ProfessionalHome) } },
                    onRoutinesClick = { navController.navigate(RoutineTemplateLibrary) { popUpTo(ProfessionalHome) } },
                    onPatientClick = { idPaciente, nombre, edad ->
                        navController.navigate(PatientDetail(idPaciente, nombre, edad))
                    },
                    onSettingsClick = { navController.navigate(ProfessionalProfile) },
                    onSignedOut = { signOutToAuth() }
                )
            }
            composable<PatientList> {
                PatientListScreen(
                    onPatientClick = { idPaciente, nombre, edad ->
                        navController.navigate(PatientDetail(idPaciente, nombre, edad))
                    },
                    onHomeClick = {
                        navController.navigate(ProfessionalHome) { popUpTo(ProfessionalHome) { inclusive = true } }
                    },
                    onRoutinesClick = { navController.navigate(RoutineTemplateLibrary) { popUpTo(ProfessionalHome) } },
                    onSettingsClick = { navController.navigate(ProfessionalProfile) }
                )
            }
            composable<PatientDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<PatientDetail>()
                PatientDetailScreen(
                    patientId = route.patientId,
                    patientName = route.patientName,
                    edad = route.edad,
                    onViewRoutinesClick = {
                        navController.navigate(PatientRoutines(route.patientId, route.patientName))
                    },
                    onAssignRoutineClick = {
                        navController.navigate(ChooseAssignMethod(route.patientId, route.patientName))
                    },
                    onRecordReferenceClick = {
                        navController.navigate(ChooseExerciseForReference(route.patientId, route.patientName))
                    },
                    onHomeClick = {
                        navController.navigate(ProfessionalHome) { popUpTo(ProfessionalHome) { inclusive = true } }
                    },
                    onPatientsClick = { navController.navigate(PatientList) { popUpTo(ProfessionalHome) } },
                    onRoutinesClick = { navController.navigate(RoutineTemplateLibrary) { popUpTo(ProfessionalHome) } },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ChooseExerciseForReference> { backStackEntry ->
                val route = backStackEntry.toRoute<ChooseExerciseForReference>()
                ChooseExerciseForReferenceScreen(
                    patientName = route.patientName,
                    onExerciseSelected = { exerciseId, exerciseName, patternSlug ->
                        navController.navigate(
                            RecordPatientReference(route.patientId, exerciseId, exerciseName, patternSlug)
                        )
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<RecordPatientReference> { backStackEntry ->
                val route = backStackEntry.toRoute<RecordPatientReference>()
                RecordReferenceScreen(
                    exerciseId = route.exerciseId,
                    exerciseName = route.exerciseName,
                    patternSlug = route.patternSlug,
                    patientId = route.patientId,
                    // Vuelve al detalle del paciente, salteando el picker de ejercicio.
                    onNavigateBack = { navController.popBackStack<PatientDetail>(inclusive = false) }
                )
            }
            composable<PatientRoutines> { backStackEntry ->
                val route = backStackEntry.toRoute<PatientRoutines>()
                PatientRoutinesScreen(
                    patientId = route.patientId,
                    patientName = route.patientName,
                    onRoutineClick = { routineId ->
                        navController.navigate(
                            ProfessionalRoutineDetail(routineId, route.patientId, route.patientName)
                        )
                    },
                    onAssignClick = {
                        navController.navigate(ChooseAssignMethod(route.patientId, route.patientName))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ChooseAssignMethod> { backStackEntry ->
                val route = backStackEntry.toRoute<ChooseAssignMethod>()
                ChooseAssignMethodScreen(
                    patientName = route.patientName,
                    onUseTemplateClick = {
                        navController.navigate(ChooseTemplateForPatient(route.patientId, route.patientName))
                    },
                    onFromScratchClick = {
                        navController.navigate(AssignRoutine(route.patientId, route.patientName))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ChooseTemplateForPatient> { backStackEntry ->
                val route = backStackEntry.toRoute<ChooseTemplateForPatient>()
                ChooseTemplateForPatientScreen(
                    patientName = route.patientName,
                    onTemplateClick = { templateId, templateName ->
                        navController.navigate(
                            ConfirmAssignTemplate(templateId, templateName, route.patientId, route.patientName)
                        )
                    },
                    onCreateTemplateClick = { navController.navigate(CreateTemplate) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ConfirmAssignTemplate> { backStackEntry ->
                val route = backStackEntry.toRoute<ConfirmAssignTemplate>()
                ConfirmAssignTemplateScreen(
                    templateId = route.templateId,
                    templateName = route.templateName,
                    patientId = route.patientId,
                    patientName = route.patientName,
                    onTemplateAssigned = { navController.popBackStack<PatientDetail>(inclusive = false) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<AssignRoutine> { backStackEntry ->
                val route = backStackEntry.toRoute<AssignRoutine>()
                AssignRoutineScreen(
                    patientId = route.patientId,
                    patientName = route.patientName,
                    onRoutineAssigned = { navController.popBackStack<PatientDetail>(inclusive = false) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ProfessionalRoutineDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<ProfessionalRoutineDetail>()
                ProfessionalRoutineDetailScreen(
                    routineId = route.routineId,
                    onEditClick = {
                        navController.navigate(
                            EditAssignedRoutine(route.routineId, route.patientId, route.patientName)
                        )
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<EditAssignedRoutine> { backStackEntry ->
                val route = backStackEntry.toRoute<EditAssignedRoutine>()
                EditAssignedRoutineScreen(
                    routineId = route.routineId,
                    onRoutineDeactivated = { navController.popBackStack<PatientRoutines>(inclusive = false) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<RoutineTemplateLibrary> {
                RoutineTemplateLibraryScreen(
                    onTemplateClick = { templateId -> navController.navigate(TemplateDetail(templateId)) },
                    onCreateTemplateClick = { navController.navigate(CreateTemplate) },
                    onHomeClick = {
                        navController.navigate(ProfessionalHome) { popUpTo(ProfessionalHome) { inclusive = true } }
                    },
                    onPatientsClick = { navController.navigate(PatientList) { popUpTo(ProfessionalHome) } },
                    onSettingsClick = { navController.navigate(ProfessionalProfile) }
                )
            }
            composable<CreateTemplate> {
                CreateTemplateScreen(
                    onTemplateCreated = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<TemplateDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<TemplateDetail>()
                TemplateDetailScreen(
                    templateId = route.templateId,
                    onAssignClick = { templateId, templateName ->
                        navController.navigate(ChoosePatientForTemplate(templateId, templateName))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ChoosePatientForTemplate> { backStackEntry ->
                val route = backStackEntry.toRoute<ChoosePatientForTemplate>()
                ChoosePatientForTemplateScreen(
                    templateId = route.templateId,
                    templateName = route.templateName,
                    onTemplateAssigned = { patientId, patientName ->
                        navController.navigate(PatientDetail(patientId, patientName)) {
                            popUpTo(RoutineTemplateLibrary)
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ProfessionalProfile> {
                ProfessionalProfileScreen(
                    onEditProfileClick = { navController.navigate(EditProfessionalProfile) },
                    onNotificationsClick = { navController.navigate(NotificationSettings) },
                    onChangePasswordClick = { navController.navigate(ChangePassword) },
                    onPrivacyClick = { navController.navigate(Privacy) },
                    onCameraClick = { navController.navigate(ProfessionalCameraPreference) },
                    onSignedOut = { signOutToAuth() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<EditProfessionalProfile> {
                EditProfessionalProfileScreen(
                    onAccountDeleted = { signOutToAuth() },
                    onProfileSaved = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ProfessionalCameraPreference> {
                CameraPreferenceScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<NotificationSettings> {
                NotificationSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<ChangePassword> {
                ChangePasswordScreen(
                    onPasswordChanged = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Privacy> {
                PrivacyScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onViewMyData = { navController.navigate(ProfessionalMyData) }
                )
            }
            composable<ProfessionalMyData> {
                MyDataScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
