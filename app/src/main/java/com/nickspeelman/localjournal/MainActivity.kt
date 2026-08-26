package com.nickspeelman.localjournal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.notifications.NotificationHelper
import com.nickspeelman.localjournal.notifications.RandomPromptAlarmScheduler
import com.nickspeelman.localjournal.ui.HistoryScreen
import com.nickspeelman.localjournal.ui.HomeScreen
import com.nickspeelman.localjournal.ui.MoodViewModel
import com.nickspeelman.localjournal.ui.MoodViewModelFactory
import com.nickspeelman.localjournal.ui.SettingsScreen
import com.nickspeelman.localjournal.ui.theme.LocalJournalTheme
import com.nickspeelman.localjournal.workers.WeeklySummaryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MoodDatabase.getDatabase(this)
        val repository = MoodRepository(database.moodDao())
        val settingsManager = SettingsManager(this)
        val notificationHelper = NotificationHelper(this)

        notificationHelper.createNotificationChannels()
        scheduleWork()

        setContent {
            LocalJournalTheme {
                MainScreen(repository, settingsManager)
            }
        }
    }

    private fun scheduleWork() {
        appScope.launch {
            // KEEP preserves any prompt that is already waiting. Merely opening the app no
            // longer cancels it and pushes it farther into the future.
            RandomPromptAlarmScheduler.ensureScheduled(this@MainActivity)
        }

        val weeklyWorkRequest = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly_summary",
            ExistingPeriodicWorkPolicy.KEEP,
            weeklyWorkRequest
        )
    }
}

@Composable
fun MainScreen(repository: MoodRepository, settingsManager: SettingsManager) {
    val navController = rememberNavController()
    val viewModel: MoodViewModel = viewModel(
        factory = MoodViewModelFactory(repository, settingsManager)
    )

    val entries by viewModel.allEntries.collectAsState()
    val summary by viewModel.summaryData.collectAsState()
    val userSettings by viewModel.settings.collectAsState()

    val context = LocalContext.current
    val notificationHelper = remember(context) {
        NotificationHelper(context.applicationContext)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var notificationsEnabled by remember {
        mutableStateOf(notificationHelper.canPostNotifications())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = notificationHelper.canPostNotifications()
    }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsEnabled = notificationHelper.canPostNotifications()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == "home",
                    onClick = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = currentRoute == "history",
                    onClick = {
                        navController.navigate("history") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        notificationsEnabled = notificationHelper.canPostNotifications()
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen(summary) }
            composable("history") { HistoryScreen(entries) }
            composable("settings") {
                SettingsScreen(
                    settings = userSettings,
                    notificationsEnabled = notificationsEnabled,
                    onSendTestNotification = {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Grant notification permission, then tap the test button again."
                                )
                            }
                        } else {
                            val sent = notificationHelper.showMoodPrompt()
                            notificationsEnabled = notificationHelper.canPostNotifications()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (sent) {
                                        "Test notification sent"
                                    } else {
                                        "Notifications are disabled in Android settings"
                                    }
                                )
                            }
                        }
                    },
                    onSettingsChanged = { newSettings ->
                        scope.launch {
                            // Persist first, then reschedule. This removes the previous race where
                            // WorkManager could read stale settings (especially the pause flag).
                            viewModel.saveSettings(newSettings)
                            RandomPromptAlarmScheduler.reschedule(context)

                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                            snackbarHostState.showSnackbar("Settings saved")
                        }
                    }
                )
            }
        }
    }
}
