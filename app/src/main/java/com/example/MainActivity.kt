package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ScreenDestination
import com.example.ui.TravelTrackViewModel
import com.example.ui.screens.AddTicketScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReviewTicketScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TripDetailScreen
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

            TravelTrackTheme(darkTheme = uiState.settings.darkTheme) {
                TravelTrackAppContent(
                    viewModel = viewModel,
                    currentScreen = currentScreen
                )
            }
        }
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
                TripDetailScreen(
                    trip = trip,
                    onBack = { viewModel.navigateBack() },
                    onEditTrip = { viewModel.openEditTrip(it) },
                    onDeleteTrip = { viewModel.deleteTrip(it) }
                )
            }

            is ScreenDestination.Settings -> {
                SettingsScreen(
                    settings = uiState.settings,
                    totalTripCount = uiState.allTrips.size,
                    widgetTripCount = uiState.widgetUpcomingTrips.size,
                    onBack = { viewModel.navigateBack() },
                    onToggleDayBefore = { viewModel.setNotifyDayBefore(it) },
                    onToggleTwoHoursBefore = { viewModel.setNotifyTwoHoursBefore(it) },
                    onToggleDarkTheme = { viewModel.setDarkTheme(it) },
                    onRefreshWidgetNow = { viewModel.forceRefreshWidgetsNow() },
                    onDeleteAllData = { viewModel.deleteAllData() }
                )
            }
        }
    }
}
