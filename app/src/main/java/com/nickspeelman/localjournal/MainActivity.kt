package com.nickspeelman.localjournal

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nickspeelman.localjournal.analytics.MonthlyReportAnalytics
import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.backup.BackupPolicyStore
import com.nickspeelman.localjournal.analytics.WeeklyReportAnalytics
import com.nickspeelman.localjournal.data.AsideBackupCodec
import com.nickspeelman.localjournal.data.JournalCsvExporter
import com.nickspeelman.localjournal.data.JournalFileIO
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.ManualBackupManager
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.ReportLockScreenPrivacy
import com.nickspeelman.localjournal.data.RelockPolicy
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.notifications.MonthlyReportScheduler
import com.nickspeelman.localjournal.notifications.BackupReminderScheduler
import com.nickspeelman.localjournal.notifications.NotificationHelper
import com.nickspeelman.localjournal.notifications.RandomPromptAlarmScheduler
import com.nickspeelman.localjournal.notifications.TestNotificationScheduler
import com.nickspeelman.localjournal.notifications.TestNotificationType
import com.nickspeelman.localjournal.notifications.WeeklyReportScheduler
import com.nickspeelman.localjournal.privacy.PrivacyChangePolicy
import com.nickspeelman.localjournal.privacy.PrivacyPreset
import com.nickspeelman.localjournal.privacy.PrivacyPresets
import com.nickspeelman.localjournal.privacy.PrivacySummaryBuilder
import com.nickspeelman.localjournal.security.AuthenticationCoordinator
import com.nickspeelman.localjournal.security.applyAsideWindowPrivacy
import com.nickspeelman.localjournal.feedback.BetaFeedback
import com.nickspeelman.localjournal.startup.InstallStateResolver
import com.nickspeelman.localjournal.ui.AddMoodEntryDialog
import com.nickspeelman.localjournal.ui.AddNoteToSavedEntryDialog
import com.nickspeelman.localjournal.ui.EditEntryDialog
import com.nickspeelman.localjournal.ui.HashtagExplorerScreen
import com.nickspeelman.localjournal.ui.HashtagTrendScreen
import com.nickspeelman.localjournal.ui.HistoryFilter
import com.nickspeelman.localjournal.ui.HistoryScreen
import com.nickspeelman.localjournal.ui.HomeScreen
import com.nickspeelman.localjournal.ui.MoodViewModel
import com.nickspeelman.localjournal.ui.MoodViewModelFactory
import com.nickspeelman.localjournal.ui.OnboardingScreen
import com.nickspeelman.localjournal.ui.PrivacyScreen
import com.nickspeelman.localjournal.ui.ReportsScreen
import com.nickspeelman.localjournal.ui.SettingsScreen
import com.nickspeelman.localjournal.ui.theme.LocalJournalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private data class NotificationNoteRequest(
    val entryId: Int,
    val rating: Int
)

private const val PRIVACY_POLICY_URL = "https://nickspeelman.github.io/aside/privacy/"

class MainActivity : FragmentActivity() {
    private lateinit var authenticationCoordinator: AuthenticationCoordinator
    private val notificationNoteRequest = mutableStateOf<NotificationNoteRequest?>(null)
    private val notificationEntryRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Resolve fresh-install vs upgrade before Room or new Alpha 2 preferences can create files
        // that would make a genuinely new installation look old.
        val installStateResolver = InstallStateResolver(this)
        val isFreshInstall = installStateResolver.isFreshInstall()

        val database = MoodDatabase.getDatabase(this)
        val manualBackupManager = ManualBackupManager(this)
        val repository = MoodRepository(database.moodDao(), manualBackupManager)
        val settingsManager = SettingsManager(this)
        val notificationHelper = NotificationHelper(this)
        authenticationCoordinator = AuthenticationCoordinator(this)

        notificationHelper.createNotificationChannels()

        val openWeeklyReport = intent.getBooleanExtra(
            NotificationHelper.EXTRA_OPEN_WEEKLY_REPORT,
            false
        )
        val openMonthlyReport = intent.getBooleanExtra(
            NotificationHelper.EXTRA_OPEN_MONTHLY_REPORT, false
        )
        val openBackup = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_BACKUP, false)
        notificationNoteRequest.value = notificationNoteRequestFrom(intent)
        notificationEntryRequest.value = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CHECKIN_ENTRY, false)
        dismissTappedCheckInNotification(intent)

        setContent {
            LocalJournalTheme {
                AsideRoot(
                    activity = this,
                    repository = repository,
                    settingsManager = settingsManager,
                    manualBackupManager = manualBackupManager,
                    installStateResolver = installStateResolver,
                    isFreshInstall = isFreshInstall,
                    authenticationCoordinator = authenticationCoordinator,
                    startOnWeeklyReport = openWeeklyReport,
                    startOnMonthlyReport = openMonthlyReport,
                    startOnBackup = openBackup,
                    notificationNoteRequest = notificationNoteRequest.value,
                    onNotificationNoteRequestHandled = ::clearNotificationNoteRequest,
                    notificationEntryRequest = notificationEntryRequest.value,
                    onNotificationEntryRequestHandled = ::clearNotificationEntryRequest,
                    scheduleWork = ::scheduleWork
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationNoteRequest.value = notificationNoteRequestFrom(intent)
        notificationEntryRequest.value = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CHECKIN_ENTRY, false)
        dismissTappedCheckInNotification(intent)
    }

    /**
     * NotificationCompat.setAutoCancel(true) asks SystemUI to remove a notification after its
     * content intent is opened. Explicitly cancel the active check-in as well so the behavior is
     * deterministic across lock-screen/unlock handoffs and OEM SystemUI implementations.
     */
    private fun dismissTappedCheckInNotification(intent: Intent) {
        val openedCheckIn = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CHECKIN_ENTRY, false)
        val openedSavedEntry =
            intent.getIntExtra(NotificationHelper.EXTRA_ADD_NOTE_ENTRY_ID, -1) > 0

        if (openedCheckIn || openedSavedEntry) {
            androidx.core.app.NotificationManagerCompat.from(this).cancel(
                NotificationHelper.NOTIFICATION_PROMPT_ID
            )
        }
    }

    private fun clearNotificationEntryRequest() {
        notificationEntryRequest.value = false
        intent.removeExtra(NotificationHelper.EXTRA_OPEN_CHECKIN_ENTRY)
    }

    private fun clearNotificationNoteRequest() {
        notificationNoteRequest.value = null
        intent.removeExtra(NotificationHelper.EXTRA_ADD_NOTE_ENTRY_ID)
        intent.removeExtra(NotificationHelper.EXTRA_ADD_NOTE_RATING)
    }

    private fun notificationNoteRequestFrom(intent: Intent): NotificationNoteRequest? {
        val entryId = intent.getIntExtra(NotificationHelper.EXTRA_ADD_NOTE_ENTRY_ID, -1)
        val rating = intent.getIntExtra(NotificationHelper.EXTRA_ADD_NOTE_RATING, 0)
        return if (entryId > 0 && rating in 1..5) {
            NotificationNoteRequest(entryId = entryId, rating = rating)
        } else {
            null
        }
    }

    private fun scheduleWork() {
        lifecycleScope.launch(Dispatchers.IO) {
            // KEEP preserves work that is already waiting. Merely opening the app no longer
            // pushes either the next mood prompt or reports farther into the future.
            RandomPromptAlarmScheduler.ensureScheduled(this@MainActivity)
            WeeklyReportScheduler.ensureScheduled(this@MainActivity)
            MonthlyReportScheduler.ensureScheduled(this@MainActivity)
            BackupReminderScheduler.ensureScheduled(this@MainActivity)
        }
    }
}

@Composable
private fun AsideRoot(
    activity: MainActivity,
    repository: MoodRepository,
    settingsManager: SettingsManager,
    manualBackupManager: ManualBackupManager,
    installStateResolver: InstallStateResolver,
    isFreshInstall: Boolean,
    authenticationCoordinator: AuthenticationCoordinator,
    startOnWeeklyReport: Boolean,
    startOnMonthlyReport: Boolean,
    startOnBackup: Boolean,
    notificationNoteRequest: NotificationNoteRequest?,
    onNotificationNoteRequestHandled: () -> Unit,
    notificationEntryRequest: Boolean,
    onNotificationEntryRequestHandled: () -> Unit,
    scheduleWork: () -> Unit
) {
    val viewModel: MoodViewModel = viewModel(
        factory = MoodViewModelFactory(repository, settingsManager, manualBackupManager)
    )
    val scope = rememberCoroutineScope()

    var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }
    var initialPrivacy by remember { mutableStateOf<PrivacySettings?>(null) }
    var backupPolicy by remember { mutableStateOf(AndroidBackupPolicy.MAXIMUM_PRIVACY) }
    val backupPolicyStore = remember { BackupPolicyStore(activity.applicationContext) }
    var alpha4BackupIntroPending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsManager.initializeAlpha2(isFreshInstall)
        val wasAlreadyAlpha4 = settingsManager.initializeAlpha4(isFreshInstall)
        backupPolicy = withContext(Dispatchers.IO) {
            if (!backupPolicyStore.exists()) {
                val initialBackupPolicy = when {
                    isFreshInstall -> PrivacyPresets.backupPolicyFor(PrivacyPreset.BALANCED, Build.VERSION.SDK_INT)
                    !wasAlreadyAlpha4 -> AndroidBackupPolicy.LEGACY_ALPHA3
                    else -> AndroidBackupPolicy.MAXIMUM_PRIVACY
                }
                backupPolicyStore.write(initialBackupPolicy)
            }
            backupPolicyStore.read()
        }
        alpha4BackupIntroPending = settingsManager.alpha4BackupIntroPendingFlow.first()
        manualBackupManager.initialize(isFreshInstall)
        installStateResolver.markInitialized()
        initialPrivacy = settingsManager.privacySettingsFlow.first()
        onboardingComplete = settingsManager.onboardingCompleteFlow.first()
        if (onboardingComplete == true) scheduleWork()
    }

    val startupPrivacy = initialPrivacy
    if (onboardingComplete == null || startupPrivacy == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val privacySettings by settingsManager.privacySettingsFlow.collectAsState(initial = startupPrivacy)
    ApplyWindowPrivacy(activity, privacySettings)

    if (onboardingComplete == false) {
        OnboardingScreen(
            canUseJournalUnlock = authenticationCoordinator.canAuthenticate(),
            onComplete = { settings, privacy, newBackupPolicy ->
                scope.launch {
                    backupPolicy = withContext(Dispatchers.IO) {
                        backupPolicyStore.write(newBackupPolicy)
                        backupPolicyStore.read()
                    }
                    viewModel.completeOnboarding(settings, privacy)
                    scheduleWork()
                    onboardingComplete = true
                }
            }
        )
    } else {
        MainScreen(
            viewModel = viewModel,
            privacySettings = privacySettings,
            backupPolicy = backupPolicy,
            alpha4BackupIntroPending = alpha4BackupIntroPending,
            onDismissAlpha4BackupIntro = {
                settingsManager.dismissAlpha4BackupIntroduction()
                alpha4BackupIntroPending = false
            },
            onBackupPolicyChanged = { newPolicy ->
                backupPolicy = withContext(Dispatchers.IO) {
                    backupPolicyStore.write(newPolicy)
                    backupPolicyStore.read()
                }
            },
            authenticationCoordinator = authenticationCoordinator,
            startOnWeeklyReport = startOnWeeklyReport,
            startOnMonthlyReport = startOnMonthlyReport,
            startOnBackup = startOnBackup,
            notificationNoteRequest = notificationNoteRequest,
            onNotificationNoteRequestHandled = onNotificationNoteRequestHandled,
            notificationEntryRequest = notificationEntryRequest,
            onNotificationEntryRequestHandled = onNotificationEntryRequestHandled
        )
    }
}

@Composable
private fun ApplyWindowPrivacy(activity: MainActivity, privacy: PrivacySettings) {
    SideEffect { applyAsideWindowPrivacy(activity, privacy) }
}

@Composable
private fun MainScreen(
    viewModel: MoodViewModel,
    privacySettings: PrivacySettings,
    backupPolicy: AndroidBackupPolicy,
    alpha4BackupIntroPending: Boolean,
    onDismissAlpha4BackupIntro: suspend () -> Unit,
    onBackupPolicyChanged: suspend (AndroidBackupPolicy) -> Unit,
    authenticationCoordinator: AuthenticationCoordinator,
    startOnWeeklyReport: Boolean = false,
    startOnMonthlyReport: Boolean = false,
    startOnBackup: Boolean = false,
    notificationNoteRequest: NotificationNoteRequest? = null,
    onNotificationNoteRequestHandled: () -> Unit = {},
    notificationEntryRequest: Boolean = false,
    onNotificationEntryRequestHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val entries by viewModel.allEntries.collectAsState()
    val summary by viewModel.summaryData.collectAsState()
    val userSettings by viewModel.settings.collectAsState()
    val privacyIntroPending by viewModel.privacyIntroPending.collectAsState()
    val backupState by viewModel.manualBackupState.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationHelper = remember(context) {
        NotificationHelper(context.applicationContext)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var notificationsEnabled by remember {
        mutableStateOf(notificationHelper.canPostNotifications())
    }

    var showAddEntryDialog by remember { mutableStateOf(false) }
    var notificationEntryDialogActive by remember { mutableStateOf(false) }
    var manualEntryRating by remember { mutableStateOf<Int?>(null) }
    var manualEntryText by remember { mutableStateOf("") }
    var notificationNoteText by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf<HistoryFilter?>(null) }
    var hashtagTrendTag by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<MoodEntry?>(null) }
    var pendingRestore by remember { mutableStateOf<AsideBackupCodec.DecodedBackup?>(null) }
    var showBackupStoragePrivacyNotice by remember { mutableStateOf(false) }
    var dismissBackupStoragePrivacyNotice by remember { mutableStateOf(false) }

    var journalUnlocked by remember { mutableStateOf(!privacySettings.requireJournalUnlock) }
    var backgroundedAtElapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(notificationNoteRequest?.entryId) {
        if (notificationNoteRequest != null) {
            notificationNoteText = ""
            showAddEntryDialog = false
            editingEntry = null
        }
    }

    LaunchedEffect(notificationEntryRequest) {
        if (notificationEntryRequest) {
            // Continue a notification check-in directly in capture UI. Journal reading can remain
            // locked; this screen creates a new entry and reveals no existing journal content.
            manualEntryRating = null
            manualEntryText = ""
            notificationNoteText = ""
            editingEntry = null
            notificationEntryDialogActive = true
            showAddEntryDialog = true
            onNotificationEntryRequestHandled()
        }
    }

    LaunchedEffect(privacySettings.requireJournalUnlock) {
        journalUnlocked = !privacySettings.requireJournalUnlock
    }

    LaunchedEffect(journalUnlocked, privacySettings.requireJournalUnlock) {
        if (privacySettings.requireJournalUnlock && !journalUnlocked) {
            // Do not leave a note-edit dialog visible if the journal re-locks while Aside is away.
            editingEntry = null
        }
    }

    DisposableEffect(
        lifecycleOwner,
        privacySettings.requireJournalUnlock,
        privacySettings.relockPolicy
    ) {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val observer = LifecycleEventObserver { _, event ->
            if (!privacySettings.requireJournalUnlock) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAtElapsed = SystemClock.elapsedRealtime()
                    if (privacySettings.relockPolicy == RelockPolicy.IMMEDIATELY) {
                        journalUnlocked = false
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val awayFor = if (backgroundedAtElapsed == 0L) 0L
                    else SystemClock.elapsedRealtime() - backgroundedAtElapsed
                    when (privacySettings.relockPolicy) {
                        RelockPolicy.IMMEDIATELY -> Unit
                        RelockPolicy.AFTER_1_MINUTE -> if (awayFor >= 60_000L) journalUnlocked = false
                        RelockPolicy.AFTER_5_MINUTES -> if (awayFor >= 5 * 60_000L) journalUnlocked = false
                        RelockPolicy.WHEN_PHONE_LOCKS -> if (keyguard.isDeviceLocked) journalUnlocked = false
                    }
                    backgroundedAtElapsed = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (
                    privacySettings.requireJournalUnlock &&
                    privacySettings.relockPolicy == RelockPolicy.WHEN_PHONE_LOCKS &&
                    intent?.action == Intent.ACTION_SCREEN_OFF
                ) {
                    journalUnlocked = false
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(screenOffReceiver, filter)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(screenOffReceiver) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = notificationHelper.canPostNotifications()
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val snapshot = viewModel.journalSnapshot()
                        JournalFileIO.writeText(
                            context.contentResolver,
                            uri,
                            JournalCsvExporter.encode(snapshot)
                        )
                    }
                }
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) "Journal exported" else "Couldn't export journal"
                )
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        // Capture the revision first. If the journal changes while the file is being
                        // assembled/written, the completed backup remains conservatively stale.
                        val representedRevision = viewModel.currentJournalRevision()
                        val snapshot = viewModel.journalSnapshot()
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        val versionName = packageInfo.versionName ?: "unknown"
                        @Suppress("DEPRECATION")
                        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            packageInfo.versionCode.toLong()
                        }
                        val createdAt = System.currentTimeMillis()
                        val json = AsideBackupCodec.encode(
                            entries = snapshot, appVersionName = versionName, appVersionCode = versionCode,
                            createdAtEpochMillis = createdAt
                        )
                        JournalFileIO.writeText(context.contentResolver, uri, json)
                        viewModel.recordSuccessfulBackup(createdAt, snapshot.size, representedRevision)
                    }
                }
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) "Journal backup saved" else "Couldn't save journal backup"
                )
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val raw = JournalFileIO.readText(context.contentResolver, uri)
                        AsideBackupCodec.decode(raw)
                    }
                }
                if (result.isSuccess) {
                    pendingRestore = result.getOrThrow()
                } else {
                    snackbarHostState.showSnackbar(
                        result.exceptionOrNull()?.message ?: "Couldn't read this backup"
                    )
                }
            }
        }
    }

    fun showAuthError(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun withFreshAuthentication(reason: String, action: () -> Unit) {
        if (!privacySettings.requireJournalUnlock) {
            action()
            return
        }
        authenticationCoordinator.authenticate(
            title = "Confirm it's you",
            subtitle = reason,
            onSuccess = action,
            onError = ::showAuthError
        )
    }

    fun requestPrivacyChange(
        newSettings: PrivacySettings,
        newBackupPolicy: AndroidBackupPolicy,
        onApplied: () -> Unit = {}
    ) {
        if (
            newSettings.requireJournalUnlock &&
            !privacySettings.requireJournalUnlock &&
            !authenticationCoordinator.canAuthenticate()
        ) {
            showAuthError("Set up a screen lock, fingerprint, or supported face unlock in Android settings first.")
            return
        }

        val save: () -> Unit = {
            scope.launch {
                val oldBackupPolicy = backupPolicy
                val backupChanged = newBackupPolicy != oldBackupPolicy
                val result = runCatching {
                    if (backupChanged) {
                        onBackupPolicyChanged(newBackupPolicy)
                    }
                    try {
                        viewModel.savePrivacySettings(newSettings)
                    } catch (error: Throwable) {
                        if (backupChanged) {
                            runCatching { onBackupPolicyChanged(oldBackupPolicy) }
                        }
                        throw error
                    }
                }
                if (result.isSuccess) {
                    onApplied()
                } else {
                    showAuthError("Couldn't save privacy settings")
                }
            }
            Unit
        }

        if (
            PrivacyChangePolicy.requiresFreshAuthentication(
                oldSettings = privacySettings,
                newSettings = newSettings,
                oldBackupPolicy = backupPolicy,
                newBackupPolicy = newBackupPolicy
            )
        ) {
            authenticationCoordinator.authenticate(
                title = "Confirm privacy change",
                subtitle = "This change makes part of your journal less protected.",
                onSuccess = save,
                onError = ::showAuthError
            )
        } else {
            save()
        }
    }

    fun unlockJournal() {
        if (!privacySettings.requireJournalUnlock) {
            journalUnlocked = true
            return
        }
        authenticationCoordinator.authenticate(
            title = "Unlock Aside",
            subtitle = "View your journal",
            onSuccess = { journalUnlocked = true },
            onError = ::showAuthError
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val canReadJournal = !privacySettings.requireJournalUnlock || journalUnlocked
    val privacySummary = remember(privacySettings, backupPolicy) {
        PrivacySummaryBuilder.build(privacySettings, Build.VERSION.SDK_INT, backupPolicy)
    }

    if (privacyIntroPending && notificationNoteRequest == null && !notificationEntryRequest && !notificationEntryDialogActive) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { viewModel.dismissPrivacyIntroduction() }
            },
            title = { Text("New privacy controls") },
            text = {
                Text(
                    "Aside now gives you more control over what appears on your lock screen and who can open your journal. Your previous behavior has been preserved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { viewModel.dismissPrivacyIntroduction() }
                        navController.navigate("privacy") { launchSingleTop = true }
                    }
                ) { Text("Review privacy settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = { scope.launch { viewModel.dismissPrivacyIntroduction() } }
                ) { Text("Not now") }
            }
        )
    }

    if (alpha4BackupIntroPending && !privacyIntroPending && notificationNoteRequest == null && !notificationEntryRequest && !notificationEntryDialogActive) {
        AlertDialog(
            onDismissRequest = { scope.launch { onDismissAlpha4BackupIntro() } },
            title = { Text("New Android backup controls") },
            text = { Text("Aside can now control whether your journal contents are included in Android cloud backups or device transfers. Your previous Android backup behavior has been preserved. Ordinary app settings may still be backed up.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { onDismissAlpha4BackupIntro() }
                    navController.navigate("privacy") { launchSingleTop = true }
                }) { Text("Review backup settings") }
            },
            dismissButton = { TextButton(onClick = { scope.launch { onDismissAlpha4BackupIntro() } }) { Text("Not now") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Capture stays available even while journal reading is locked.
            if (currentRoute == "home") {
                FloatingActionButton(onClick = { showAddEntryDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add entry")
                }
            }
        },
        bottomBar = {
            NavigationBar {
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
                    selected = currentRoute == "hashtagTrend" || currentRoute == "hashtagExplorer" ||
                        (currentRoute == "history" && (historyFilter == null || historyFilter is HistoryFilter.Hashtag)),
                    onClick = {
                        historyFilter = null
                        navController.navigate("history") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Reports") },
                    label = { Text("Reports") },
                    selected = currentRoute == "reports" ||
                        (currentRoute == "history" &&
                            (historyFilter is HistoryFilter.Date || historyFilter is HistoryFilter.WeekdayRange)),
                    onClick = {
                        if (
                            currentRoute == "history" &&
                            (historyFilter is HistoryFilter.Date || historyFilter is HistoryFilter.WeekdayRange)
                        ) {
                            historyFilter = null
                            navController.popBackStack()
                        } else {
                            navController.navigate("reports") {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings" || currentRoute == "privacy",
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
            startDestination = when { startOnBackup -> "settings"; startOnWeeklyReport || startOnMonthlyReport -> "reports"; else -> "home" },
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                JournalContentGate(
                    protected = privacySettings.requireJournalUnlock,
                    unlocked = journalUnlocked,
                    onUnlock = ::unlockJournal
                ) {
                    HomeScreen(
                        summary = summary,
                        entryCount = entries.size,
                        onHashtagClick = { tag ->
                            historyFilter = HistoryFilter.Hashtag(tag)
                            navController.navigate("history") { launchSingleTop = true }
                        },
                        onViewAllHashtags = {
                            navController.navigate("hashtagExplorer") { launchSingleTop = true }
                        }
                    )
                }
            }
            composable("hashtagExplorer") {
                JournalContentGate(
                    protected = privacySettings.requireJournalUnlock,
                    unlocked = journalUnlocked,
                    onUnlock = ::unlockJournal
                ) {
                    HashtagExplorerScreen(
                        entries = entries,
                        onClose = { navController.popBackStack() },
                        onHashtagClick = { tag, range ->
                            historyFilter = HistoryFilter.Hashtag(
                                tag = tag,
                                range = range,
                                allTime = range == null
                            )
                            navController.navigate("history") { launchSingleTop = true }
                        }
                    )
                }
            }
            composable("history") {
                JournalContentGate(
                    protected = privacySettings.requireJournalUnlock,
                    unlocked = journalUnlocked,
                    onUnlock = ::unlockJournal
                ) {
                    HistoryScreen(
                        entries = entries,
                        filter = historyFilter,
                        onCloseFilter = historyFilter?.let {
                            {
                                historyFilter = null
                                navController.popBackStack()
                                Unit
                            }
                        },
                        onViewHashtagTrend = { tag ->
                            hashtagTrendTag = tag
                            navController.navigate("hashtagTrend") { launchSingleTop = true }
                        },
                        onEditEntry = { entry -> editingEntry = entry },
                        onDeleteEntry = { entry ->
                            scope.launch {
                                viewModel.deleteEntry(entry)
                                val snackbarResult = snackbarHostState.showSnackbar(
                                    message = "Entry deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (snackbarResult == SnackbarResult.ActionPerformed) {
                                    val undoResult = runCatching { viewModel.restoreDeletedEntry(entry) }
                                    if (undoResult.isFailure) {
                                        snackbarHostState.showSnackbar("Couldn't restore entry")
                                    }
                                }
                            }
                        }
                    )
                }
            }
            composable("hashtagTrend") {
                JournalContentGate(
                    protected = privacySettings.requireJournalUnlock,
                    unlocked = journalUnlocked,
                    onUnlock = ::unlockJournal
                ) {
                    val tag = hashtagTrendTag ?: (historyFilter as? HistoryFilter.Hashtag)?.tag
                    if (tag != null) {
                        val hashtagFilter = (historyFilter as? HistoryFilter.Hashtag)
                            ?.takeIf { it.tag.equals(tag, ignoreCase = true) }
                        HashtagTrendScreen(
                            entries = entries,
                            tag = tag,
                            onClose = { navController.popBackStack() },
                            initialRange = hashtagFilter?.range,
                            initialAllTime = hashtagFilter?.allTime == true
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }
            }
            composable("reports") {
                JournalContentGate(
                    protected = privacySettings.requireJournalUnlock,
                    unlocked = journalUnlocked,
                    onUnlock = ::unlockJournal
                ) {
                    ReportsScreen(
                        entries = entries,
                        settings = userSettings,
                        openWeeklyReport = startOnWeeklyReport,
                        openMonthlyReport = startOnMonthlyReport,
                        onOpenDayEntries = { date ->
                            historyFilter = HistoryFilter.Date(date)
                            navController.navigate("history") { launchSingleTop = true }
                        },
                        onOpenWeekdayEntries = { dayOfWeek, startDate, endDate ->
                            historyFilter = HistoryFilter.WeekdayRange(dayOfWeek, startDate, endDate)
                            navController.navigate("history") { launchSingleTop = true }
                        },
                        onFreshAuthentication = ::withFreshAuthentication
                    )
                }
            }
            composable("settings") {
                SettingsScreen(
                    settings = userSettings,
                    notificationsEnabled = notificationsEnabled,
                    entryCount = entries.size,
                    backupState = backupState,
                    androidBackupPolicy = backupPolicy,
                    canRevealJournalMetadata = canReadJournal,
                    privacyStatusTitle = privacySummary.title,
                    privacyStatusSubtitle = privacySummary.subtitle,
                    onOpenPrivacy = { navController.navigate("privacy") { launchSingleTop = true } },
                    onOpenPrivacyPolicy = {
                        val result = runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(PRIVACY_POLICY_URL)
                                )
                            )
                        }
                        if (result.isFailure) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Couldn't open the privacy policy")
                            }
                        }
                    },
                    onExportJournal = {
                        withFreshAuthentication("Export your journal") {
                            csvExportLauncher.launch("Aside-journal-${LocalDate.now()}.csv")
                        }
                    },
                    onBackupJournal = {
                        withFreshAuthentication("Create a full journal backup") {
                            if (backupState.storagePrivacyNoticeDismissed) {
                                backupLauncher.launch("Aside-backup-${LocalDate.now()}.json")
                            } else {
                                dismissBackupStoragePrivacyNotice = false
                                showBackupStoragePrivacyNotice = true
                            }
                        }
                    },
                    onBackupReminderChanged = { enabled, days ->
                        scope.launch {
                            viewModel.setBackupReminder(enabled, days)
                            BackupReminderScheduler.sync(context, enabled)
                        }
                    },
                    onRestoreBackup = {
                        restoreLauncher.launch(
                            arrayOf("application/json", "text/json", "application/octet-stream")
                        )
                    },
                    onDeleteAllJournalData = {
                        withFreshAuthentication("Permanently delete all journal data") {
                            scope.launch {
                                viewModel.deleteAllJournalData()
                                snackbarHostState.showSnackbar("All journal data deleted")
                            }
                        }
                    },
                    onSendTestNotification = { delaySeconds ->
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
                        } else if (delaySeconds > 0L) {
                            TestNotificationScheduler.schedule(
                                context = context,
                                type = TestNotificationType.CHECK_IN,
                                delaySeconds = delaySeconds
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Test check-in scheduled for about ${delaySeconds} seconds. Lock your phone now."
                                )
                            }
                        } else {
                            scope.launch {
                                val latestPrivacy = viewModel.currentPrivacySettings()
                                val sent = notificationHelper.showMoodPrompt(latestPrivacy, userSettings.checkInNotificationTimeoutMinutes)
                                notificationsEnabled = notificationHelper.canPostNotifications()
                                snackbarHostState.showSnackbar(
                                    if (sent) "Test check-in sent"
                                    else "Notifications are disabled in Android settings"
                                )
                            }
                        }
                    },
                    onSendTestWeeklyReport = { delaySeconds ->
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
                                    "Grant notification permission, then tap the weekly test again."
                                )
                            }
                        } else {
                            withFreshAuthentication("Generate a test weekly report") {
                                val zoneId = ZoneId.systemDefault()
                                val report = WeeklyReportAnalytics.build(
                                    entries = entries,
                                    settings = userSettings,
                                    endDate = LocalDate.now(zoneId),
                                    zoneId = zoneId
                                )
                                if (report == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "No check-ins in the last 7 days to report"
                                        )
                                    }
                                } else if (delaySeconds > 0L) {
                                    TestNotificationScheduler.schedule(
                                        context = context,
                                        type = TestNotificationType.WEEKLY_REPORT,
                                        delaySeconds = delaySeconds
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Test weekly report scheduled for about ${delaySeconds} seconds. Lock your phone now."
                                        )
                                    }
                                } else {
                                    scope.launch {
                                        val latestPrivacy = viewModel.currentPrivacySettings()
                                        val sent = notificationHelper.showWeeklyReport(
                                            report,
                                            userSettings,
                                            latestPrivacy
                                        )
                                        val message = if (!sent) {
                                            "Notifications are disabled in Android settings"
                                        } else {
                                            when (latestPrivacy.reportLockScreenPrivacy) {
                                                ReportLockScreenPrivacy.FULL_REPORT ->
                                                    "Test weekly report sent"
                                                ReportLockScreenPrivacy.NOTIFICATION_ONLY ->
                                                    "Test weekly report sent. Lock your phone to verify the generic lock-screen version."
                                                ReportLockScreenPrivacy.HIDDEN ->
                                                    "Test weekly report sent with report details removed; it is also hidden on a secure lock screen."
                                            }
                                        }
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            }
                        }
                    },
                    onSendTestMonthlyReport = { delaySeconds ->
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
                                    "Grant notification permission, then tap the monthly test again."
                                )
                            }
                        } else {
                            withFreshAuthentication("Generate a test monthly report") {
                                val zoneId = ZoneId.systemDefault()
                                val report = MonthlyReportAnalytics.build(
                                    entries = entries,
                                    settings = userSettings,
                                    endDate = LocalDate.now(zoneId),
                                    zoneId = zoneId
                                )
                                if (report == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "No check-ins in the last 30 days to report"
                                        )
                                    }
                                } else if (delaySeconds > 0L) {
                                    TestNotificationScheduler.schedule(
                                        context = context,
                                        type = TestNotificationType.MONTHLY_REPORT,
                                        delaySeconds = delaySeconds
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Test monthly report scheduled for about ${delaySeconds} seconds. Lock your phone now."
                                        )
                                    }
                                } else {
                                    scope.launch {
                                        val latestPrivacy = viewModel.currentPrivacySettings()
                                        val sent = notificationHelper.showMonthlyReport(
                                            report,
                                            userSettings,
                                            latestPrivacy
                                        )
                                        val message = if (!sent) {
                                            "Notifications are disabled in Android settings"
                                        } else {
                                            when (latestPrivacy.reportLockScreenPrivacy) {
                                                ReportLockScreenPrivacy.FULL_REPORT ->
                                                    "Test monthly report sent"
                                                ReportLockScreenPrivacy.NOTIFICATION_ONLY ->
                                                    "Test monthly report sent. Lock your phone to verify the generic lock-screen version."
                                                ReportLockScreenPrivacy.HIDDEN ->
                                                    "Test monthly report sent with report details removed; it is also hidden on a secure lock screen."
                                            }
                                        }
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            }
                        }
                    },
                    onBetaFeedback = { BetaFeedback.launch(context) },
                    onSettingsChanged = { newSettings ->
                        scope.launch {
                            val oldSettings = userSettings
                            // Persist first, then reschedule. WorkManager/AlarmManager cannot race
                            // this write and see stale pause, quiet-hour, or report settings.
                            viewModel.saveSettings(newSettings)
                            if (RandomPromptAlarmScheduler.schedulingInputsChanged(oldSettings, newSettings)) {
                                RandomPromptAlarmScheduler.reschedule(context, newSettings)
                            }
                            if (WeeklyReportScheduler.schedulingInputsChanged(oldSettings, newSettings)) {
                                WeeklyReportScheduler.reschedule(context, newSettings)
                            }
                            if (MonthlyReportScheduler.schedulingInputsChanged(oldSettings, newSettings)) {
                                MonthlyReportScheduler.reschedule(context, newSettings)
                            }
                            if (oldSettings.backupReminderEnabled != newSettings.backupReminderEnabled) {
                                BackupReminderScheduler.sync(context, newSettings.backupReminderEnabled)
                            }

                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar("Saved")
                        }
                    }
                )
            }
            composable("privacy") {
                PrivacyScreen(
                    settings = privacySettings,
                    backupPolicy = backupPolicy,
                    canUseJournalUnlock = authenticationCoordinator.canAuthenticate(),
                    onBack = { navController.popBackStack() },
                    onPrivacyChanged = { newPrivacy, newBackupPolicy ->
                        requestPrivacyChange(newPrivacy, newBackupPolicy) {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar("Saved")
                            }
                        }
                    },
                    onMessage = ::showAuthError
                )
            }
        }
    }

    notificationNoteRequest?.let { request ->
        AddNoteToSavedEntryDialog(
            rating = request.rating,
            input = notificationNoteText,
            onInputChange = { notificationNoteText = it },
            onDismiss = {
                notificationNoteText = ""
                onNotificationNoteRequestHandled()
            },
            onSave = {
                val noteText = notificationNoteText
                notificationNoteText = ""
                onNotificationNoteRequestHandled()
                scope.launch {
                    val updated = viewModel.addNoteToEntry(request.entryId, noteText)
                    snackbarHostState.showSnackbar(
                        if (updated) "Note added" else "Couldn't find that check-in"
                    )
                }
            }
        )
    }

    if (showAddEntryDialog) {
        AddMoodEntryDialog(
            rating = manualEntryRating,
            input = manualEntryText,
            onRatingChange = { manualEntryRating = it },
            onInputChange = { manualEntryText = it },
            onDismiss = {
                showAddEntryDialog = false
                notificationEntryDialogActive = false
                manualEntryRating = null
                manualEntryText = ""
            },
            onSave = {
                val ratingToSave = manualEntryRating
                val contentToSave = manualEntryText
                showAddEntryDialog = false
                notificationEntryDialogActive = false
                manualEntryRating = null
                manualEntryText = ""
                scope.launch {
                    viewModel.addEntry(ratingToSave, contentToSave)
                    snackbarHostState.showSnackbar("Entry saved")
                }
            }
        )
    }

    editingEntry?.let { entry ->
        if (canReadJournal) {
            EditEntryDialog(
                entry = entry,
                use24Hour = userSettings.use24Hour,
                onDismiss = { editingEntry = null },
                onSave = { updated ->
                    editingEntry = null
                    scope.launch {
                        viewModel.updateEntry(updated)
                        snackbarHostState.showSnackbar("Entry updated")
                    }
                }
            )
        }
    }

    if (showBackupStoragePrivacyNotice) {
        AlertDialog(
            onDismissRequest = { showBackupStoragePrivacyNotice = false },
            title = { Text("Choose where to save your backup") },
            text = {
                Column {
                    Text("Your backup contains your journal data. Aside does not upload it, but the location you choose might. If you save it to cloud storage or a folder that is automatically backed up, your data may leave this device.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Once your backup leaves Aside, how it is stored and protected depends on the service or location you choose.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = dismissBackupStoragePrivacyNotice,
                            onCheckedChange = { dismissBackupStoragePrivacyNotice = it }
                        )
                        Text("Don't show this again")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackupStoragePrivacyNotice = false
                    if (dismissBackupStoragePrivacyNotice) {
                        scope.launch { viewModel.dismissBackupStoragePrivacyNotice() }
                    }
                    backupLauncher.launch("Aside-backup-${LocalDate.now()}.json")
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupStoragePrivacyNotice = false }) { Text("Cancel") }
            }
        )
    }

    pendingRestore?.let { backup ->
        val created = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(backup.metadata.createdAtEpochMillis))
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                val currentJournalText = if (canReadJournal) {
                    "your current ${entries.size} ${if (entries.size == 1) "journal entry" else "journal entries"}"
                } else {
                    "your current journal"
                }
                Text(
                    "This backup contains ${backup.entries.size} " +
                        (if (backup.entries.size == 1) "journal entry" else "journal entries") +
                        " and was created $created. Restoring it will replace $currentJournalText. " +
                        "Entries that aren't in this backup will be permanently removed. " +
                        "Your settings won't change."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        withFreshAuthentication("Replace your journal with this backup") {
                            pendingRestore = null
                            scope.launch {
                                val result = runCatching {
                                    viewModel.replaceJournal(backup.entries)
                                    viewModel.recordSuccessfulRestore(backup.metadata.createdAtEpochMillis, backup.entries.size)
                                }
                                snackbarHostState.showSnackbar(
                                    if (result.isSuccess) "Journal restored" else "Couldn't restore journal"
                                )
                            }
                        }
                    }
                ) { Text("Replace journal") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun JournalContentGate(
    protected: Boolean,
    unlocked: Boolean,
    onUnlock: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!protected || unlocked) {
        content()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Text("Journal locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Unlock with your phone's fingerprint, supported face unlock, PIN, pattern, or password to view journal contents.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onUnlock) { Text("Unlock journal") }
            Text(
                "You can still use + to make a new check-in without opening your journal.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
