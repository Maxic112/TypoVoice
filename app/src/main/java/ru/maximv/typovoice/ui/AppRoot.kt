package ru.maximv.typovoice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.settings.AboutScreen
import ru.maximv.typovoice.ui.settings.AssistantScreen
import ru.maximv.typovoice.ui.settings.FeedbackScreen
import ru.maximv.typovoice.ui.settings.InsertScreen
import ru.maximv.typovoice.ui.settings.LanguageScreen
import ru.maximv.typovoice.ui.settings.UpdatesScreen
import ru.maximv.typovoice.ui.settings.LaunchScreen
import ru.maximv.typovoice.ui.settings.OverlayScreen
import ru.maximv.typovoice.ui.settings.PermissionsScreen
import ru.maximv.typovoice.ui.settings.RecordingScreen
import ru.maximv.typovoice.ui.settings.ReliabilityScreen
import ru.maximv.typovoice.ui.settings.ServersScreen
import ru.maximv.typovoice.ui.settings.SettingsScreen

/** Navigation routes: the home screen is the root, history and settings open from it. */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val HISTORY_QUEUE = "history?queue=true"
    const val SETTINGS = "settings"
    const val SERVERS = "settings/servers"
    const val LAUNCH = "settings/launch"
    const val ASSISTANT = "settings/launch/assistant"
    const val OVERLAY = "settings/launch/overlay"
    const val RECORDING = "settings/recording"
    const val INSERT = "settings/insert"
    const val RELIABILITY = "settings/reliability"
    const val FEEDBACK = "settings/feedback"
    const val PERMISSIONS = "settings/permissions"
    const val LANGUAGE = "settings/language"
    const val UPDATES = "settings/updates"
    const val ABOUT = "settings/about"
}

@Composable
fun AppRoot(container: AppContainer, openHistory: Boolean, onHistoryOpened: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(openHistory) {
        if (openHistory) {
            nav.openHistory(Routes.HISTORY)
            onHistoryOpened()
        }
    }

    NavHost(nav, startDestination = Routes.HOME) {
        val back: () -> Unit = { nav.popBackStack() }
        composable(Routes.HOME) { HomeScreen(container, nav::navigate) }
        composable(
            "history?queue={queue}",
            arguments = listOf(navArgument("queue") { type = NavType.BoolType; defaultValue = false }),
        ) { HistoryScreen(container, onBack = back, startWithQueue = it.arguments?.getBoolean("queue") == true) }
        composable(Routes.SETTINGS) { SettingsScreen(container, back, nav::navigate) }
        composable(Routes.SERVERS) { ServersScreen(container, back) }
        composable(Routes.LAUNCH) { LaunchScreen(container, back, nav::navigate) }
        composable(Routes.ASSISTANT) { AssistantScreen(container, back) }
        composable(Routes.OVERLAY) { OverlayScreen(container, back) }
        composable(Routes.RECORDING) { RecordingScreen(container, back) }
        composable(Routes.INSERT) { InsertScreen(container, back) }
        composable(Routes.RELIABILITY) { ReliabilityScreen(container, back) { nav.openHistory(Routes.HISTORY_QUEUE) } }
        composable(Routes.FEEDBACK) { FeedbackScreen(container, back) }
        composable(Routes.PERMISSIONS) { PermissionsScreen(back) }
        composable(Routes.LANGUAGE) { LanguageScreen(back) }
        composable(Routes.UPDATES) { UpdatesScreen(container, back) }
        composable(Routes.ABOUT) { AboutScreen(back) }
    }
}

/** Opens history on top of the home screen; back always returns home. */
private fun NavHostController.openHistory(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME)
        launchSingleTop = true
    }
}
