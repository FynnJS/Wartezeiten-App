package de.wartezeiten.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.wartezeiten.app.ui.components.UpdateBanner
import de.wartezeiten.app.ui.parks.ParkListRoute
import de.wartezeiten.app.ui.settings.SettingsRoute
import de.wartezeiten.app.ui.statistics.StatisticsRoute
import de.wartezeiten.app.ui.update.UpdateViewModel
import de.wartezeiten.app.ui.watchlist.WatchlistRoute
import de.wartezeiten.app.ui.waitingtimes.WaitingTimesRoute

@Composable
fun WartezeitenApp(
    notificationParkKey: String? = null,
    notificationAttractionId: String? = null,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val settingsViewModel: de.wartezeiten.app.ui.settings.SettingsViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(notificationParkKey, notificationAttractionId) {
        notificationParkKey?.let { parkKey ->
            val route = if (notificationAttractionId.isNullOrBlank()) {
                "parks/$parkKey"
            } else {
                "parks/$parkKey?attractionId=${Uri.encode(notificationAttractionId)}"
            }
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "parks"
        ) {
            composable("parks") {
                ParkListRoute(
                    onParkClick = { park ->
                        navController.navigate("parks/${park.id}")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    },
                    onWatchlistClick = {
                        navController.navigate("watchlist")
                    },
                    onStatisticsClick = {
                        navController.navigate("statistics")
                    },
                    onParkStatisticsClick = { parkKey ->
                        navController.navigate("statistics?parkKey=${Uri.encode(parkKey)}&attractionId=")
                    },
                    onAttractionClick = { parkKey, attractionId ->
                        navController.navigate("parks/${Uri.encode(parkKey)}?attractionId=${Uri.encode(attractionId)}")
                    }
                )
            }
            composable("settings") {
                SettingsRoute(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("watchlist") {
                WatchlistRoute(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "statistics?parkKey={parkKey}&attractionId={attractionId}",
                arguments = listOf(
                    navArgument("parkKey") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("attractionId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) {
                StatisticsRoute(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "parks/{parkKey}?attractionId={attractionId}",
                arguments = listOf(
                    navArgument("parkKey") { type = NavType.StringType },
                    navArgument("attractionId") { type = NavType.StringType; nullable = true }
                )
            ) {
                WaitingTimesRoute(
                    onBackClick = { navController.popBackStack() },
                    onParkStatisticsClick = { parkKey ->
                        navController.navigate("statistics?parkKey=${Uri.encode(parkKey)}&attractionId=")
                    },
                    onAttractionStatisticsClick = { parkKey, attractionId ->
                        navController.navigate("statistics?parkKey=${Uri.encode(parkKey)}&attractionId=${Uri.encode(attractionId)}")
                    }
                )
            }
        }

        val releaseInfo = updateState.releaseInfo
        if (updateState.updateAvailable && updateState.showBanner && releaseInfo != null) {
            UpdateBanner(
                releaseInfo = releaseInfo,
                language = settingsState.language,
                onInstallClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.apkUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                onDismiss = updateViewModel::dismissBanner,
            )
        }
    }
}
