package com.trusttap.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trusttap.app.data.AppPreferences
import com.trusttap.app.data.HistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TrustTapApp(
    initialMediaUri: Uri?,
    initialMediaMimeType: String?,
    initialCaption: String?,
    initialSharedText: String?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val historyRepository = remember { HistoryRepository.get(context) }
    val baseUrl by preferences.baseUrl.collectAsState(initial = AppPreferences.DEFAULT_BASE_URL)
    val history by historyRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        onboardingComplete = preferences.hasCompletedOnboarding.first()
    }

    when (onboardingComplete) {
        null -> LoadingScreen()
        false -> WelcomeScreen(
            onContinue = {
                scope.launch {
                    preferences.completeOnboarding()
                    onboardingComplete = true
                }
            }
        )
        true -> TrustTapNavigation(
            baseUrl = baseUrl,
            history = history,
            historyRepository = historyRepository,
            initialMediaUri = initialMediaUri,
            initialMediaMimeType = initialMediaMimeType,
            initialCaption = initialCaption,
            initialSharedText = initialSharedText,
            onSaveBaseUrl = { value -> scope.launch { preferences.setBaseUrl(value) } }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "TrustTap",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = "Welcome to TrustTap",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                text = "Check a photo or video, understand what it shows, and hear the result out loud.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "You can choose media inside the app, or use Share from Facebook, Chrome, Gallery, or another app. TrustTap does not request SMS or storage permissions.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Get started")
            }
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun TrustTapNavigation(
    baseUrl: String,
    history: List<com.trusttap.app.data.HistoryEntity>,
    historyRepository: HistoryRepository,
    initialMediaUri: Uri?,
    initialMediaMimeType: String?,
    initialCaption: String?,
    initialSharedText: String?,
    onSaveBaseUrl: (String) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()
    val showBottomBar = currentRoute == "check" || currentRoute == "history" || currentRoute == "settings"
    val destinations = listOf(
        BottomDestination("check", "Check", Icons.Filled.CheckCircle),
        BottomDestination("history", "History", Icons.Filled.History),
        BottomDestination("settings", "Settings", Icons.Filled.Settings)
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo("check") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "check",
            modifier = Modifier.padding(padding)
        ) {
            composable("check") {
                CheckScreen(
                    initialMediaUri = initialMediaUri,
                    initialMediaMimeType = initialMediaMimeType,
                    initialCaption = initialCaption,
                    initialSharedText = initialSharedText,
                    baseUrl = baseUrl,
                    historyRepository = historyRepository
                )
            }
            composable("history") {
                HistoryScreen(
                    entries = history,
                    onOpen = { id -> navController.navigate("history/$id") }
                )
            }
            composable("history/{entryId}", arguments = listOf(navArgument("entryId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("entryId")
                HistoryDetailScreen(
                    entry = history.firstOrNull { it.id == id },
                    repository = historyRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(baseUrl = baseUrl, onSaveBaseUrl = onSaveBaseUrl)
            }
        }
    }
}
