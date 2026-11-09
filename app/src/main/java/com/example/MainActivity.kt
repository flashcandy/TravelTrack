package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ScreenDestination
import com.example.ui.TravelTrackViewModel
import com.example.ui.screens.AddTicketScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReviewTicketScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TripDetailScreen
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TravelTrackTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }

    private val viewModel: TravelTrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

            val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.showSmsRationale(false)
                viewModel.setAutoSendSms(granted)
            }

            if (uiState.showSmsPermissionRationale) {
                AlertDialog(
                    onDismissRequest = { viewModel.showSmsRationale(false) },
                    title = {
                        Text(
                            text = "SEND_SMS PERMISSION",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "TravelTrack uses SEND_SMS exclusively to send 'PNR <10-digit PNR>' directly to 139 if you enable auto-sending. Without this permission, the app opens a prefilled message in your default SMS app.",
                            fontFamily = SpaceGroteskFontFamily
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.showSmsRationale(false)
                                sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        ) {
                            Text(
                                text = "CONTINUE",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = NothingRed
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.showSmsRationale(false) }) {
                            Text(text = "CANCEL", fontFamily = DotoFontFamily)
                        }
                    }
                )
            }

            TravelTrackTheme(darkTheme = uiState.settings.darkTheme) {
                TravelTrackAppContent(
                    viewModel = viewModel,
                    currentScreen = currentScreen
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNotificationAccessStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Handle shared PDF or Image ticket from other apps (ACTION_SEND)
        if (intent.action == Intent.ACTION_SEND) {
            val sharedUri = IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java
            ) ?: intent.clipData?.getItemAt(0)?.uri

            if (sharedUri != null) {
                viewModel.processTicketUri(sharedUri, intent.type)
                return
            }

            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.processRawTicketText(sharedText, "share://text")
                return
            }
        }

        // 2. Handle tap on a Glance widget row or departure notification
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, 0L)
        if (tripId > 0L) {
            viewModel.openTripDetailFromIntent(tripId)
        }
    }
}

@Composable
fun TravelTrackAppContent(
    viewModel: TravelTrackViewModel,
    currentScreen: ScreenDestination
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Subtle, short 180ms transition per Nothing OS design guidelines (no bouncy animations)
    Crossfade(
        targetState = currentScreen,
        animationSpec = tween(durationMillis = 180),
        modifier = Modifier.fillMaxSize(),
        label = "nothing_screen_transition"
    ) { destination ->
        when (destination) {
            is ScreenDestination.Home -> {
                HomeScreen(
                    uiState = uiState,
                    onAddTicketClick = { viewModel.navigateTo(ScreenDestination.AddTicket) },
                    onTripClick = { tripId ->
                        viewModel.navigateTo(ScreenDestination.TripDetail(tripId))
                    },
                    onSettingsClick = { viewModel.navigateTo(ScreenDestination.Settings) },
                    onLoadSampleTickets = { viewModel.loadDemoSampleTickets() },
                    onDismissBanner = { viewModel.dismissBannerMessage() }
                )
            }

            is ScreenDestination.AddTicket -> {
                AddTicketScreen(
                    isExtracting = uiState.isExtracting,
                    extractionMessage = uiState.extractionMessage,
                    onBack = { viewModel.navigateBack() },
                    onFileSelected = { uri -> viewModel.processTicketUri(uri) },
                    onParseRawText = { text, source ->
                        viewModel.processRawTicketText(text, source)
                    }
                )
            }

            is ScreenDestination.ReviewDraft -> {
                ReviewTicketScreen(
                    initialDraft = destination.draft,
                    editingTripId = destination.editingTripId,
                    duplicateTrip = destination.duplicateTrip,
                    onBack = { viewModel.navigateBack() },
                    onSave = { editedDraft ->
                        viewModel.saveReviewedDraft(
                            draft = editedDraft,
                            editingTripId = destination.editingTripId,
                            sourceFileUri = destination.sourceFileUri
                        )
                    }
                )
            }

            is ScreenDestination.TripDetail -> {
                val trip = uiState.allTrips.find { it.id == destination.tripId }
                val isPending = uiState.pendingPnrTripId == destination.tripId && uiState.pendingPnrSecondsRemaining > 0
                val secondsLeft = if (uiState.pendingPnrTripId == destination.tripId) uiState.pendingPnrSecondsRemaining else 0
                TripDetailScreen(
                    trip = trip,
                    pendingPnrSecondsRemaining = secondsLeft,
                    isPnrCheckPending = isPending,
                    showCheckForConfirmed = uiState.settings.showCheckForConfirmed,
                    onBack = { viewModel.navigateBack() },
                    onEditTrip = { viewModel.openEditTrip(it) },
                    onDeleteTrip = { viewModel.deleteTrip(it) },
                    onCheckPnrStatus = { viewModel.checkPnrStatusViaSms(it) },
                    onCheckTelegramBot = { viewModel.checkPnrStatusViaTelegramBot(it) },
                    onCancelPendingCheck = { viewModel.cancelPendingPnrCheck() },
                    onUpdateStatusManually = { tripId, status -> viewModel.updateStatusManually(tripId, status) }
                )
            }

            is ScreenDestination.Settings -> {
                SettingsScreen(
                    settings = uiState.settings,
                    totalTripCount = uiState.allTrips.size,
                    widgetTripCount = uiState.widgetUpcomingTrips.size,
                    isNotificationAccessGranted = uiState.isNotificationAccessGranted,
                    onBack = { viewModel.navigateBack() },
                    onToggleDayBefore = { viewModel.setNotifyDayBefore(it) },
                    onToggleTwoHoursBefore = { viewModel.setNotifyTwoHoursBefore(it) },
                    onToggleDarkTheme = { viewModel.setDarkTheme(it) },
                    onToggleAutoCheckPnr = { viewModel.setAutoCheckPnr(it) },
                    onToggleCheckInFinal24Hours = { viewModel.setCheckInFinal24Hours(it) },
                    onToggleShowCheckConfirmed = { viewModel.setShowCheckForConfirmed(it) },
                    onToggleAutoSendSms = { viewModel.setAutoSendSms(it) },
                    onClearPnrHistory = { viewModel.clearPnrCheckHistory() },
                    onRefreshWidgetNow = { viewModel.forceRefreshWidgetsNow() },
                    onDeleteAllData = { viewModel.deleteAllData() }
                )
            }
        }
    }
}
