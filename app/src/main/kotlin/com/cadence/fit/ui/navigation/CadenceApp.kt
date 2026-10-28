package com.cadence.fit.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceProgressBar
import com.cadence.fit.ui.screens.BodyMapScreen
import com.cadence.fit.ui.screens.ExerciseDetailScreen
import com.cadence.fit.ui.screens.HistoryScreen
import com.cadence.fit.ui.screens.HomeScreen
import com.cadence.fit.ui.screens.LibraryScreen
import com.cadence.fit.ui.screens.LoggerScreen
import com.cadence.fit.ui.screens.OnboardingScreen
import com.cadence.fit.ui.screens.PlanScreen
import com.cadence.fit.ui.screens.ProgressScreen
import com.cadence.fit.ui.screens.RoutineEditorScreen
import com.cadence.fit.ui.screens.SessionDetailScreen
import com.cadence.fit.ui.screens.SettingsScreen
import com.cadence.fit.ui.screens.SleepScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PLAN = "plan"
    const val ROUTINE_EDITOR = "routine"
    const val LOGGER = "logger"
    const val LIBRARY = "library"
    const val EXERCISE = "exercise"
    const val HISTORY = "history"
    const val SESSION = "session"
    const val PROGRESS = "progress"
    const val BODY_MAP = "bodymap"
    const val SLEEP = "sleep"
    const val SETTINGS = "settings"

    fun routineEditor(routineId: String?) = "$ROUTINE_EDITOR?routineId=${routineId.orEmpty()}"

    fun logger(sessionId: String) = "$LOGGER?sessionId=$sessionId"

    fun exercise(exerciseId: String, routineExerciseId: String? = null) =
        "$EXERCISE?exerciseId=$exerciseId&routineExerciseId=${routineExerciseId.orEmpty()}"

    fun session(sessionId: String) = "$SESSION?sessionId=$sessionId"
}

private data class BottomDestination(val route: String, val label: String, val icon: CadenceIcon)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", CadenceIcon.Home),
    BottomDestination(Routes.PLAN, "Plan", CadenceIcon.Plan),
    BottomDestination(Routes.LIBRARY, "Exercises", CadenceIcon.Book),
    BottomDestination(Routes.PROGRESS, "Progress", CadenceIcon.Progress),
    BottomDestination(Routes.BODY_MAP, "Body", CadenceIcon.Body),
)

@Composable
fun CadenceApp(viewModel: CadenceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingMessage = messages.firstOrNull()
    LaunchedEffect(pendingMessage?.id) {
        val message = pendingMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        viewModel.dismissMessage(message.id)
    }

    if (!state.loaded) {
        LoadingScreen()
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('?')
    val showsBottomBar = bottomDestinations.any { it.route == currentRoute }

    val startDestination = remember { if (state.profile.onboardingComplete) Routes.HOME else Routes.ONBOARDING }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        initialProfile = state.profile,
                        initialUnit = state.settings.weightUnit,
                        onComplete = { profile, unit ->
                            viewModel.finishOnboarding(profile, unit)
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = viewModel,
                        onStartWorkout = { routineId ->
                            viewModel.startSession(routineId)
                        },
                        onOpenSession = { sessionId -> navController.navigate(Routes.logger(sessionId)) },
                        onOpenPlan = { navController.navigate(Routes.PLAN) },
                        onOpenSleep = { navController.navigate(Routes.SLEEP) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onBuildPlan = { navController.navigate(Routes.ONBOARDING) },
                    )
                }

                composable(Routes.PLAN) {
                    PlanScreen(
                        viewModel = viewModel,
                        onOpenRoutine = { routineId -> navController.navigate(Routes.routineEditor(routineId)) },
                        onNewRoutine = { navController.navigate(Routes.routineEditor(null)) },
                        onStartRoutine = { routineId -> viewModel.startSession(routineId) },
                        onStartAndOpen = { sessionId -> navController.navigate(Routes.logger(sessionId)) },
                    )
                }

                composable(
                    route = "${Routes.ROUTINE_EDITOR}?routineId={routineId}",
                    arguments = listOf(navArgument("routineId") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    val routineId = entry.arguments?.getString("routineId").orEmpty()
                    RoutineEditorScreen(
                        viewModel = viewModel,
                        routineId = routineId.takeIf { it.isNotBlank() },
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                    )
                }

                composable(
                    route = "${Routes.LOGGER}?sessionId={sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    LoggerScreen(
                        viewModel = viewModel,
                        sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                        onFinished = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                    )
                }

                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        viewModel = viewModel,
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }

                composable(
                    route = "${Routes.EXERCISE}?exerciseId={exerciseId}&routineExerciseId={routineExerciseId}",
                    arguments = listOf(
                        navArgument("exerciseId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("routineExerciseId") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    ExerciseDetailScreen(
                        viewModel = viewModel,
                        exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                        routineExerciseId = entry.arguments?.getString("routineExerciseId")
                            ?.takeIf { it.isNotBlank() },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(
                        viewModel = viewModel,
                        onOpenSession = { sessionId -> navController.navigate(Routes.session(sessionId)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = "${Routes.SESSION}?sessionId={sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    SessionDetailScreen(
                        viewModel = viewModel,
                        sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                    )
                }

                composable(Routes.PROGRESS) {
                    ProgressScreen(
                        viewModel = viewModel,
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }

                composable(Routes.BODY_MAP) {
                    BodyMapScreen(viewModel = viewModel)
                }

                composable(Routes.SLEEP) {
                    SleepScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenSleep = { navController.navigate(Routes.SLEEP) },
                        onRestartOnboarding = { navController.navigate(Routes.ONBOARDING) },
                    )
                }
            }

            if (showsBottomBar) {
                CadenceBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun CadenceBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bottomDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onNavigate(destination.route) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CadenceIcon(
                        destination.icon,
                        size = 22.dp,
                        contentDescription = destination.label,
                        tint = tint,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CadenceIcon(
                CadenceIcon.Plan,
                size = 40.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(18.dp))
            CadenceProgressBar(
                fraction = 0.4f,
                modifier = Modifier.fillMaxWidth(0.35f),
            )
        }
    }
}
