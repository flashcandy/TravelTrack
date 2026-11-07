package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.PnrStatusCategory
import com.example.data.PnrStatusCheck
import com.example.data.SettingsRepository
import com.example.data.Trip
import com.example.data.TripFilter
import com.example.data.TripRepository
import com.example.notifications.TripNotificationScheduler
import com.example.parser.ConfidenceLevel
import com.example.parser.DateTimeNormalizer
import com.example.parser.ParsedTicketDraft
import com.example.parser.SampleTickets
import com.example.parser.TicketDocumentExtractor
import com.example.parser.TicketFormat
import com.example.parser.TicketParserRouter
import com.example.sms.PnrCheckDispatchResult
import com.example.sms.PnrReplyListenerService
import com.example.sms.PnrStatusScheduler
import com.example.sms.PnrStatusSender
import com.example.sms.SmsSendResult
import com.example.widget.TravelTrackWidgetUpdater
import com.example.widget.WidgetRefreshScheduler
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScreenDestination {
    data object Home : ScreenDestination
    data object AddTicket : ScreenDestination
    data class ReviewDraft(
        val draft: ParsedTicketDraft,
        val editingTripId: Long = 0L,
        val sourceFileUri: String? = null,
        val duplicateTrip: Trip? = null
    ) : ScreenDestination
    data class TripDetail(val tripId: Long) : ScreenDestination
    data object Settings : ScreenDestination
}

data class HomeTripsUiState(
    val allTrips: List<Trip> = emptyList(),
    val upcomingTrips: List<Trip> = emptyList(),
    val pastTrips: List<Trip> = emptyList(),
    val widgetUpcomingTrips: List<Trip> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isExtracting: Boolean = false,
    val extractionMessage: String? = null,
    val statusBannerMessage: String? = null,
    val pendingPnrTripId: Long? = null,
    val pendingPnrSecondsRemaining: Int = 0,
    val showSmsPermissionRationale: Boolean = false,
    val isNotificationAccessGranted: Boolean = false
)

class TravelTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val tripRepository = TripRepository(database.tripDao(), database.pnrStatusCheckDao())
    private val settingsRepository = SettingsRepository(application)
    private val parserRouter = TicketParserRouter()
    private val documentExtractor = TicketDocumentExtractor(application, parserRouter)

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _navigationBackStack = ArrayDeque<ScreenDestination>()

    private val _isExtracting = MutableStateFlow(false)
    private val _extractionMessage = MutableStateFlow<String?>(null)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)

    private val _pendingPnrTripId = MutableStateFlow<Long?>(null)
    private val _pendingPnrSecondsRemaining = MutableStateFlow(0)
    private val _showSmsPermissionRationale = MutableStateFlow(false)
    private val _isNotificationAccessGranted = MutableStateFlow(false)
    private var pnrTimeoutJob: Job? = null

    val uiState: StateFlow<HomeTripsUiState> = combine(
        tripRepository.allTrips,
        settingsRepository.settingsFlow,
        _isExtracting,
        _extractionMessage,
        combine(
            _statusBannerMessage,
            _pendingPnrTripId,
            _pendingPnrSecondsRemaining,
            _showSmsPermissionRationale,
            _isNotificationAccessGranted
        ) { banner, pendingId, seconds, showRationale, notifGranted ->
            Tuple5(banner, pendingId, seconds, showRationale, notifGranted)
        }
    ) { trips, settings, isExtracting, extractionMessage, tuple ->
        val now = LocalDateTime.now()
        val (upcoming, past) = TripFilter.partitionUpcomingAndPast(trips, now)
        val widgetTrips = TripFilter.filterUpcomingForWidget(trips, now, maxDays = 5L, maxCount = 3)
        HomeTripsUiState(
            allTrips = trips,
            upcomingTrips = upcoming,
            pastTrips = past,
            widgetUpcomingTrips = widgetTrips,
            settings = settings,
            isExtracting = isExtracting,
            extractionMessage = extractionMessage,
            statusBannerMessage = tuple.a,
            pendingPnrTripId = tuple.b,
            pendingPnrSecondsRemaining = tuple.c,
            showSmsPermissionRationale = tuple.d,
            isNotificationAccessGranted = tuple.e
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeTripsUiState()
    )

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    init {
        WidgetRefreshScheduler.scheduleAll(application)
        TripNotificationScheduler.ensureNotificationChannel(application)
        PnrReplyListenerService.ensurePnrNotificationChannel(application)
        refreshNotificationAccessStatus()

        // Observe settings to sync periodic PNR worker
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                if (settings.autoCheckPnr) {
                    PnrStatusScheduler.schedule(getApplication())
                } else {
                    PnrStatusScheduler.cancel(getApplication())
                }
            }
        }
    }

    fun refreshNotificationAccessStatus() {
        _isNotificationAccessGranted.value =
            PnrReplyListenerService.isNotificationListenerGranted(getApplication())
    }

    fun navigateTo(destination: ScreenDestination) {
        val current = _currentScreen.value
        if (current != destination) {
            _navigationBackStack.addLast(current)
            _currentScreen.value = destination
        }
    }

    fun navigateBack(): Boolean {
        return if (_navigationBackStack.isNotEmpty()) {
            _currentScreen.value = _navigationBackStack.removeLast()
            true
        } else if (_currentScreen.value != ScreenDestination.Home) {
            _currentScreen.value = ScreenDestination.Home
            true
        } else {
            false
        }
    }

    fun navigateHomeClearStack() {
        _navigationBackStack.clear()
        _currentScreen.value = ScreenDestination.Home
    }

    fun openTripDetailFromIntent(tripId: Long) {
        if (tripId > 0L) {
            _navigationBackStack.clear()
            _navigationBackStack.addLast(ScreenDestination.Home)
            _currentScreen.value = ScreenDestination.TripDetail(tripId)
        }
    }

    fun dismissBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun showSmsRationale(show: Boolean) {
        _showSmsPermissionRationale.value = show
    }

    // --- PNR Status SMS Checking ---

    fun checkPnrStatusViaSms(trip: Trip, onLaunchPrefilledIntent: ((Intent) -> Unit)? = null) {
        viewModelScope.launch {
            val pnrDigits = trip.cleanPnr()
            if (pnrDigits.length != 10) {
                _statusBannerMessage.value = "ERROR: PNR must be 10 digits (found: '${trip.reference}')"
                return@launch
            }

            val settings = settingsRepository.getSettingsSnapshot()
            val dispatchResult = PnrStatusSender.dispatchCheck(
                context = getApplication(),
                trip = trip,
                autoSendRequested = settings.autoSendSms,
                isManualTrigger = true
            )

            when (dispatchResult) {
                is PnrCheckDispatchResult.AutoSent -> {
                    _statusBannerMessage.value = "SENT 'PNR $pnrDigits' TO 139 // WAITING FOR REPLY..."
                    startPnrTimeoutCountdown(trip.id)
                }
                is PnrCheckDispatchResult.PrefilledIntent -> {
                    _statusBannerMessage.value = "OPENING SMS DRAFT // TAP SEND TO 139"
                    if (onLaunchPrefilledIntent != null) {
                        onLaunchPrefilledIntent(dispatchResult.intent)
                    } else {
                        dispatchResult.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching {
                            getApplication<Application>().startActivity(dispatchResult.intent)
                        }
                    }
                    startPnrTimeoutCountdown(trip.id)
                }
                is PnrCheckDispatchResult.Error -> {
                    _statusBannerMessage.value = "SMS DISPATCH FAILED: ${dispatchResult.message}"
                }
            }
        }
    }

    private fun startPnrTimeoutCountdown(tripId: Long) {
        pnrTimeoutJob?.cancel()
        _pendingPnrTripId.value = tripId
        _pendingPnrSecondsRemaining.value = 180

        pnrTimeoutJob = viewModelScope.launch {
            var remaining = 180
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1
                _pendingPnrSecondsRemaining.value = remaining

                // If check resolved in database, end waiting
                val latestCheck = tripRepository.getLatestCheckForTrip(tripId)
                if (latestCheck != null && latestCheck.replyReceivedAt != null) {
                    break
                }
            }
            _pendingPnrTripId.value = null
            _pendingPnrSecondsRemaining.value = 0
        }
    }

    fun cancelPendingPnrCheck() {
        pnrTimeoutJob?.cancel()
        _pendingPnrTripId.value = null
        _pendingPnrSecondsRemaining.value = 0
    }

    fun updateStatusManually(tripId: Long, exactStatus: String) {
        viewModelScope.launch {
            val cleaned = exactStatus.trim()
            if (cleaned.isNotBlank()) {
                tripRepository.updateStatusManually(tripId, cleaned)
                val app = getApplication<Application>()
                TravelTrackWidgetUpdater.updateAllWidgets(app)
                _statusBannerMessage.value = "STATUS UPDATED // $cleaned"
            }
        }
    }

    fun observePnrChecksForTrip(tripId: Long): Flow<List<PnrStatusCheck>> {
        return tripRepository.observeChecksForTrip(tripId)
    }

    fun clearPnrCheckHistory() {
        viewModelScope.launch {
            tripRepository.clearPnrCheckHistory()
            _statusBannerMessage.value = "PNR CHECK HISTORY CLEARED"
        }
    }

    // --- Ticket File Extraction & Processing ---

    fun processTicketUri(uri: Uri, mimeTypeHint: String? = null) {
        viewModelScope.launch {
            _isExtracting.value = true
            _extractionMessage.value = "EXTRACTING TICKET DATA // PDFBOX & ML KIT OCR..."
            if (_currentScreen.value != ScreenDestination.AddTicket) {
                navigateTo(ScreenDestination.AddTicket)
            }
            try {
                val draft = documentExtractor.extractAndParse(uri, mimeTypeHint)
                val duplicate = tripRepository.findDuplicateByReference(draft.reference)
                _currentScreen.value = ScreenDestination.ReviewDraft(
                    draft = draft,
                    editingTripId = 0L,
                    sourceFileUri = uri.toString(),
                    duplicateTrip = duplicate
                )
            } finally {
                _isExtracting.value = false
                _extractionMessage.value = null
            }
        }
    }

    fun processRawTicketText(rawText: String, sourceLabel: String = "sample://ticket") {
        viewModelScope.launch {
            _isExtracting.value = true
            _extractionMessage.value = "PARSING TICKET LABELS..."
            try {
                val draft = parserRouter.parse(rawText)
                val duplicate = tripRepository.findDuplicateByReference(draft.reference)
                _currentScreen.value = ScreenDestination.ReviewDraft(
                    draft = draft,
                    editingTripId = 0L,
                    sourceFileUri = sourceLabel,
                    duplicateTrip = duplicate
                )
            } finally {
                _isExtracting.value = false
                _extractionMessage.value = null
            }
        }
    }

    fun openEditTrip(trip: Trip) {
        val draft = ParsedTicketDraft(
            format = when {
                trip.operator.contains("IRCTC", ignoreCase = true) || trip.type.name == "TRAIN" ->
                    TicketFormat.IRCTC_TRAIN
                else -> TicketFormat.REDBUS_TSRTC
            },
            type = trip.type,
            operator = trip.operator,
            travelClass = trip.travelClass.orEmpty(),
            originName = trip.originName,
            originCode = trip.originCode,
            destinationName = trip.destinationName,
            destinationCode = trip.destinationCode,
            pickupPoint = trip.pickupPoint.orEmpty(),
            dropPoint = trip.dropPoint.orEmpty(),
            departureDate = trip.departureDate,
            departureTime = trip.departureTime,
            arrivalDate = trip.arrivalDate.orEmpty(),
            arrivalTime = trip.arrivalTime.orEmpty(),
            originStartTime = trip.originStartTime.orEmpty(),
            amountPaise = trip.amount,
            currency = trip.currency,
            reference = trip.reference,
            bookingStatus = trip.bookingStatus.orEmpty(),
            currentStatus = trip.currentStatus.orEmpty(),
            status = trip.effectiveStatus,
            passengerNames = trip.passengerNames,
            seatNumbers = trip.seatNumbers.orEmpty(),
            transactionId = trip.transactionId.orEmpty(),
            rawText = trip.rawExtractedText.orEmpty(),
            fieldConfidences = mapOf(
                "reference" to ConfidenceLevel.HIGH,
                "operator" to ConfidenceLevel.HIGH,
                "origin" to ConfidenceLevel.HIGH,
                "destination" to ConfidenceLevel.HIGH,
                "departure" to ConfidenceLevel.HIGH,
                "amount" to ConfidenceLevel.HIGH,
                "status" to ConfidenceLevel.HIGH
            ),
            fieldHints = mapOf(
                "reference" to "Saved trip reference",
                "operator" to "Saved operator",
                "origin" to "Saved origin",
                "destination" to "Saved destination",
                "departure" to "Saved departure",
                "amount" to "Saved amount",
                "status" to "Saved status"
            )
        )
        navigateTo(
            ScreenDestination.ReviewDraft(
                draft = draft,
                editingTripId = trip.id,
                sourceFileUri = trip.sourceFileUri,
                duplicateTrip = null
            )
        )
    }

    fun saveReviewedDraft(
        draft: ParsedTicketDraft,
        editingTripId: Long,
        sourceFileUri: String?,
        overwriteDuplicate: Boolean = true
    ) {
        viewModelScope.launch {
            val normalizedDepDate = DateTimeNormalizer.normalizeDate(draft.departureDate)
                ?: draft.departureDate.trim()
            val normalizedDepTime = DateTimeNormalizer.normalizeTime(draft.departureTime)
                ?: draft.departureTime.trim()
            val normalizedArrDate = DateTimeNormalizer.normalizeDate(draft.arrivalDate)
                ?: draft.arrivalDate.trim()
            val normalizedArrTime = DateTimeNormalizer.normalizeTime(draft.arrivalTime)
                ?: draft.arrivalTime.trim()

            val cleanedDraft = draft.copy(
                departureDate = normalizedDepDate,
                departureTime = normalizedDepTime,
                arrivalDate = normalizedArrDate,
                arrivalTime = normalizedArrTime
            )

            val trip = cleanedDraft.toTrip(
                sourceFileUri = sourceFileUri,
                existingId = editingTripId
            )

            val savedId = tripRepository.saveTrip(trip, overwriteDuplicate = overwriteDuplicate)
            val app = getApplication<Application>()
            TravelTrackWidgetUpdater.updateAllWidgets(app)
            TripNotificationScheduler.syncAllTripAlerts(app)

            _statusBannerMessage.value = "SAVED // ${trip.title}"
            _navigationBackStack.clear()
            _navigationBackStack.addLast(ScreenDestination.Home)
            _currentScreen.value = ScreenDestination.TripDetail(savedId)
        }
    }

    fun loadDemoSampleTickets() {
        viewModelScope.launch {
            val trainDraft = parserRouter.parse(SampleTickets.SAMPLE_IRCTC_ERS_TEXT)
            val busDraft = parserRouter.parse(SampleTickets.SAMPLE_REDBUS_TSRTC_TEXT)
            tripRepository.saveTrip(trainDraft.toTrip(sourceFileUri = "sample://irctc_ers"))
            tripRepository.saveTrip(busDraft.toTrip(sourceFileUri = "sample://redbus_tsrtc"))
            val app = getApplication<Application>()
            TravelTrackWidgetUpdater.updateAllWidgets(app)
            TripNotificationScheduler.syncAllTripAlerts(app)
            _statusBannerMessage.value = "LOADED SAMPLE IRCTC & REDBUS TICKETS"
        }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            TripNotificationScheduler.cancelAlertsForTrip(app, trip.id)
            tripRepository.deleteTrip(trip)
            TravelTrackWidgetUpdater.updateAllWidgets(app)
            _statusBannerMessage.value = "DELETED // ${trip.title}"
            navigateHomeClearStack()
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val all = tripRepository.getAllTripsSnapshot()
            all.forEach { TripNotificationScheduler.cancelAlertsForTrip(app, it.id) }
            tripRepository.deleteAllTrips()
            TravelTrackWidgetUpdater.updateAllWidgets(app)
            _statusBannerMessage.value = "ALL TICKET DATA DELETED"
            navigateHomeClearStack()
        }
    }

    // --- Settings Updates ---

    fun setNotifyDayBefore(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyDayBefore(enabled)
            TripNotificationScheduler.syncAllTripAlerts(getApplication())
        }
    }

    fun setNotifyTwoHoursBefore(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyTwoHoursBefore(enabled)
            TripNotificationScheduler.syncAllTripAlerts(getApplication())
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkTheme(dark)
        }
    }

    fun setAutoCheckPnr(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoCheckPnr(enabled)
        }
    }

    fun setCheckInFinal24Hours(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCheckInFinal24Hours(enabled)
        }
    }

    fun setShowCheckForConfirmed(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowCheckForConfirmed(enabled)
        }
    }

    fun setAutoSendSms(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSendSms(enabled)
        }
    }

    fun forceRefreshWidgetsNow() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            TravelTrackWidgetUpdater.updateAllWidgets(app)
            WidgetRefreshScheduler.scheduleAll(app)
            _statusBannerMessage.value = "WIDGET REFRESHED"
        }
    }
}
