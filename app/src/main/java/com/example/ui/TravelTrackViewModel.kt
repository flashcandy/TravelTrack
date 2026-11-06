package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
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
import com.example.widget.TravelTrackWidgetUpdater
import com.example.widget.WidgetRefreshScheduler
import java.time.LocalDateTime
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
    val statusBannerMessage: String? = null
)

class TravelTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val tripRepository = TripRepository(database.tripDao())
    private val settingsRepository = SettingsRepository(application)
    private val parserRouter = TicketParserRouter()
    private val documentExtractor = TicketDocumentExtractor(application, parserRouter)

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _navigationBackStack = ArrayDeque<ScreenDestination>()

    private val _isExtracting = MutableStateFlow(false)
    private val _extractionMessage = MutableStateFlow<String?>(null)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeTripsUiState> = combine(
        tripRepository.allTrips,
        settingsRepository.settingsFlow,
        _isExtracting,
        _extractionMessage,
        _statusBannerMessage
    ) { trips, settings, isExtracting, extractionMessage, banner ->
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
            statusBannerMessage = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeTripsUiState()
    )

    init {
        WidgetRefreshScheduler.scheduleAll(application)
        TripNotificationScheduler.ensureNotificationChannel(application)
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
            currentStatus = trip.status,
            status = trip.status,
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

    fun forceRefreshWidgetsNow() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            TravelTrackWidgetUpdater.updateAllWidgets(app)
            WidgetRefreshScheduler.scheduleAll(app)
            _statusBannerMessage.value = "WIDGET REFRESHED"
        }
    }
}
