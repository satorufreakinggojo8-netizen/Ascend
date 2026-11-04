package com.cadence.fit.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.screens.BodyMapScreen
import com.cadence.fit.ui.screens.CoachProfileScreen
import com.cadence.fit.ui.screens.CoachesScreen
import com.cadence.fit.ui.screens.ExerciseDetailScreen
import com.cadence.fit.ui.screens.HistoryScreen
import com.cadence.fit.ui.screens.HomeScreen
import com.cadence.fit.ui.screens.LibraryScreen
import com.cadence.fit.ui.screens.LoggerScreen
import com.cadence.fit.ui.screens.NutritionScreen
import com.cadence.fit.ui.screens.OnboardingScreen
import com.cadence.fit.ui.screens.PlanScreen
import com.cadence.fit.ui.screens.ProgressScreen
import com.cadence.fit.ui.screens.RoutineEditorScreen
import com.cadence.fit.ui.screens.ScheduleScreen
import com.cadence.fit.ui.screens.SessionDetailScreen
import com.cadence.fit.ui.screens.SessionSummaryScreen
import com.cadence.fit.ui.screens.ProfileScreen
import com.cadence.fit.ui.screens.SettingsScreen
import com.cadence.fit.ui.screens.SleepScreen
import com.cadence.fit.ui.screens.SplitsScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PLAN = "plan"
    const val SCHEDULE = "schedule"
    const val ROUTINE_EDITOR = "routine"
    const val LOGGER = "logger"
    const val LIBRARY = "library"
    const val EXERCISE = "exercise"
    const val HISTORY = "history"
    const val SESSION = "session"
    const val SUMMARY = "summary"
    const val PROGRESS = "progress"
    const val BODY_MAP = "bodymap"
    const val SLEEP = "sleep"
    const val COACHES = "coaches"
    const val COACH = "coach"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val NUTRITION = "nutrition"
    const val SPLITS = "splits"

    fun routineEditor(routineId: String?) = "$ROUTINE_EDITOR?routineId=${routineId.orEmpty()}"

    fun logger(sessionId: String) = "$LOGGER?sessionId=$sessionId"

    fun exercise(exerciseId: String, routineExerciseId: String? = null) =
        "$EXERCISE?exerciseId=$exerciseId&routineExerciseId=${routineExerciseId.orEmpty()}"

    fun session(sessionId: String) = "$SESSION?sessionId=$sessionId"

    fun summary(sessionId: String) = "$SUMMARY?sessionId=$sessionId"

    fun coach(coachId: String) = "$COACH?coachId=$coachId"

    /** History, optionally narrowed to the week starting [weekStartEpochDay]. */
    fun history(weekStartEpochDay: Long? = null) =
        if (weekStartEpochDay == null) HISTORY else "$HISTORY?weekOf=$weekStartEpochDay"
}

private data class BottomDestination(val route: String, val label: String, val icon: CadenceIcon)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", CadenceIcon.Home),
    BottomDestination(Routes.PLAN, "Plan", CadenceIcon.Plan),
    BottomDestination(Routes.LIBRARY, "Exercises", CadenceIcon.Book),
    BottomDestination(Routes.PROGRESS, "Progress", CadenceIcon.Progress),
    BottomDestination(Routes.BODY_MAP, "Body", CadenceIcon.Body),
)

private val tabRoutes: Set<String> = bottomDestinations.map { it.route }.toSet()

private fun NavBackStackEntry.isTab(): Boolean =
    tabRoutes.contains(destination.route?.substringBefore('?'))

@Composable
fun CadenceApp(viewModel: CadenceViewModel) {
    // Only the loading flag is read here. Collecting the whole state at the root
    // re-composed every screen on every logged set; each screen already reads
    // exactly the state it needs.
    val loaded by remember(viewModel) {
        viewModel.state.map { it.loaded }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = viewModel.state.value.loaded)
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingMessage = messages.firstOrNull()
    LaunchedEffect(pendingMessage?.id) {
        val message = pendingMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        viewModel.dismissMessage(message.id)
    }

    if (!loaded) {
        LoadingScreen()
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('?')
    val showsBottomBar = bottomDestinations.any { it.route == currentRoute }

    val startDestination = remember {
        if (viewModel.state.value.profile.onboardingComplete) Routes.HOME else Routes.ONBOARDING
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
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
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                // Tab switches cross-fade in place. Pushing a screen slides it in
                // so the stack has a direction, and popping reverses that.
                enterTransition = {
                    if (initialState.isTab() && targetState.isTab()) {
                        fadeIn(tween(190))
                    } else {
                        slideInHorizontally(tween(280), initialOffsetX = { it / 10 }) +
                            fadeIn(tween(220))
                    }
                },
                exitTransition = {
                    if (initialState.isTab() && targetState.isTab()) {
                        fadeOut(tween(120))
                    } else {
                        fadeOut(tween(160))
                    }
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(280), targetOffsetX = { it / 10 }) +
                        fadeOut(tween(180))
                },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        initialProfile = viewModel.state.value.profile,
                        initialUnit = viewModel.state.value.settings.weightUnit,
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
                        onOpenProgress = {
                            navController.navigate(Routes.PROGRESS) {
                                launchSingleTop = true
                            }
                        },
                        onOpenSleep = { navController.navigate(Routes.SLEEP) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onBuildPlan = { navController.navigate(Routes.ONBOARDING) },
                        onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                        onOpenCoaches = { navController.navigate(Routes.COACHES) },
                    )
                }

                composable(Routes.PLAN) {
                    PlanScreen(
                        viewModel = viewModel,
                        onOpenRoutine = { routineId -> navController.navigate(Routes.routineEditor(routineId)) },
                        onNewRoutine = { navController.navigate(Routes.routineEditor(null)) },
                        onStartRoutine = { routineId -> viewModel.startSession(routineId) },
                        onStartAndOpen = { sessionId -> navController.navigate(Routes.logger(sessionId)) },
                        onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                        onOpenCoaches = { navController.navigate(Routes.COACHES) },
                        onOpenSplits = { navController.navigate(Routes.SPLITS) },
                    )
                }

                composable(Routes.SPLITS) {
                    SplitsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onApplied = { navController.popBackStack() },
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenSplits = { navController.navigate(Routes.SPLITS) },
                        onOpenNutrition = { navController.navigate(Routes.NUTRITION) },
                        onRestartOnboarding = { navController.navigate(Routes.ONBOARDING) },
                    )
                }

                composable(Routes.NUTRITION) {
                    NutritionScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SCHEDULE) {
                    ScheduleScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onStartAndOpen = { sessionId -> navController.navigate(Routes.logger(sessionId)) },
                        onOpenSession = { sessionId -> navController.navigate(Routes.session(sessionId)) },
                        onEditRoutine = { routineId -> navController.navigate(Routes.routineEditor(routineId)) },
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
                        onFinished = { savedSessionId ->
                            if (savedSessionId == null) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Routes.summary(savedSessionId)) {
                                    popUpTo(Routes.HOME)
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                    )
                }

                composable(
                    route = "${Routes.SUMMARY}?sessionId={sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    SessionSummaryScreen(
                        viewModel = viewModel,
                        sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                        onDone = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onOpenSession = { sessionId -> navController.navigate(Routes.session(sessionId)) },
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                    )
                }

                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        viewModel = viewModel,
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                        onOpenHistory = { navController.navigate(Routes.history()) },
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

                composable(
                    route = "${Routes.HISTORY}?weekOf={weekOf}",
                    arguments = listOf(navArgument("weekOf") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    HistoryScreen(
                        viewModel = viewModel,
                        onOpenSession = { sessionId -> navController.navigate(Routes.session(sessionId)) },
                        onBack = { navController.popBackStack() },
                        weekStartEpochDay = entry.arguments?.getLong("weekOf")?.takeIf { it >= 0L },
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
                        onOpenHistory = { navController.navigate(Routes.history()) },
                        onOpenBodyMap = { navController.navigate(Routes.BODY_MAP) },
                        onOpenWeekHistory = { epochDay -> navController.navigate(Routes.history(epochDay)) },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    )
                }

                composable(Routes.BODY_MAP) {
                    BodyMapScreen(viewModel = viewModel)
                }

                composable(Routes.SLEEP) {
                    SleepScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }

                composable(Routes.COACHES) {
                    CoachesScreen(
                        onOpenCoach = { coachId -> navController.navigate(Routes.coach(coachId)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = "${Routes.COACH}?coachId={coachId}",
                    arguments = listOf(navArgument("coachId") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    CoachProfileScreen(
                        viewModel = viewModel,
                        coachId = entry.arguments?.getString("coachId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenRoutine = { routineId ->
                            navController.navigate(Routes.routineEditor(routineId))
                        },
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                        onOpenSleep = { navController.navigate(Routes.SLEEP) },
                        onOpenProgress = { navController.navigate(Routes.PROGRESS) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenSleep = { navController.navigate(Routes.SLEEP) },
                        onRestartOnboarding = { navController.navigate(Routes.ONBOARDING) },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    )
                }
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
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                bottomDestinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val background by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            Color.Transparent
                        },
                        label = "navBackground",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(background)
                            .clickable(role = Role.Tab) { onNavigate(destination.route) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.12f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "navIconScale",
                        )
                        CadenceIcon(
                            destination.icon,
                            size = 21.dp * iconScale,
                            contentDescription = destination.label,
                            tint = tint,
                        )
                        Spacer(Modifier.size(3.dp))
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val transition = rememberInfiniteTransition(label = "loading")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "brandPulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(78.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                CadenceIcon(
                    CadenceIcon.Plan,
                    size = 36.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(20.dp))
            Text("Cadence", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(6.dp))
            Text(
                "Loading your training",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(20.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
