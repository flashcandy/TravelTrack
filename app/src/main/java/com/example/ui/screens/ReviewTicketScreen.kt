package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Trip
import com.example.data.TripType
import com.example.parser.ConfidenceLevel
import com.example.parser.DateTimeNormalizer
import com.example.parser.ParsedTicketDraft
import com.example.parser.TicketFormat
import com.example.ui.components.ConfidenceBadge
import com.example.ui.components.NothingCard
import com.example.ui.components.PerforatedDivider
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingWhite
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Locale

@Composable
fun ReviewTicketScreen(
    initialDraft: ParsedTicketDraft,
    editingTripId: Long,
    duplicateTrip: Trip?,
    onBack: () -> Unit,
    onSave: (ParsedTicketDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var type by remember(initialDraft) { mutableStateOf(initialDraft.type) }
    var reference by remember(initialDraft) { mutableStateOf(initialDraft.reference) }
    var operator by remember(initialDraft) { mutableStateOf(initialDraft.operator) }
    var travelClass by remember(initialDraft) { mutableStateOf(initialDraft.travelClass) }
    var originName by remember(initialDraft) { mutableStateOf(initialDraft.originName) }
    var originCode by remember(initialDraft) { mutableStateOf(initialDraft.originCode) }
    var destinationName by remember(initialDraft) { mutableStateOf(initialDraft.destinationName) }
    var destinationCode by remember(initialDraft) { mutableStateOf(initialDraft.destinationCode) }
    var departureDate by remember(initialDraft) { mutableStateOf(initialDraft.departureDate) }
    var departureTime by remember(initialDraft) { mutableStateOf(initialDraft.departureTime) }
    var arrivalDate by remember(initialDraft) { mutableStateOf(initialDraft.arrivalDate) }
    var arrivalTime by remember(initialDraft) { mutableStateOf(initialDraft.arrivalTime) }
    var status by remember(initialDraft) { mutableStateOf(initialDraft.status.ifBlank { initialDraft.currentStatus }) }
    var bookingStatus by remember(initialDraft) { mutableStateOf(initialDraft.bookingStatus) }
    var amountRupeesText by remember(initialDraft) {
        val initialRupees = if (initialDraft.amountPaise > 0L) {
            if (initialDraft.amountPaise % 100L == 0L) {
                (initialDraft.amountPaise / 100L).toString()
            } else {
                String.format(Locale.US, "%.2f", initialDraft.amountPaise / 100.0)
            }
        } else {
            ""
        }
        mutableStateOf(initialRupees)
    }
    var passengerNames by remember(initialDraft) { mutableStateOf(initialDraft.passengerNames) }
    var seatNumbers by remember(initialDraft) { mutableStateOf(initialDraft.seatNumbers) }
    var pickupPoint by remember(initialDraft) { mutableStateOf(initialDraft.pickupPoint) }
    var dropPoint by remember(initialDraft) { mutableStateOf(initialDraft.dropPoint) }
    var originStartTime by remember(initialDraft) { mutableStateOf(initialDraft.originStartTime) }
    var transactionId by remember(initialDraft) { mutableStateOf(initialDraft.transactionId) }
    var showRawText by remember(initialDraft) {
        mutableStateOf(initialDraft.format == TicketFormat.UNKNOWN)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("review_ticket_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("review_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (editingTripId > 0L) "EDIT TRIP" else "REVIEW EXTRACTED FIELDS",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "FORMAT // ${initialDraft.format.displayName.uppercase()}",
                                fontFamily = DotoFontFamily,
                                fontSize = 11.sp,
                                color = if (initialDraft.format == TicketFormat.UNKNOWN) {
                                    NothingRed
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                // Duplicate detection banner
                if (duplicateTrip != null && editingTripId == 0L) {
                    item {
                        NothingCard(
                            borderColor = NothingRed,
                            modifier = Modifier.testTag("duplicate_reference_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = NothingRed
                                )
                                Column {
                                    Text(
                                        text = "DUPLICATE REFERENCE DETECTED",
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = NothingRed
                                    )
                                    Text(
                                        text = "A trip with reference '${duplicateTrip.reference}' (${duplicateTrip.title}) already exists. Saving will update the existing record.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Raw Extracted Text Card (always visible for Unknown tickets, toggleable for known tickets)
                if (initialDraft.rawText.isNotBlank()) {
                    item {
                        NothingCard(
                            borderColor = if (initialDraft.format == TicketFormat.UNKNOWN) {
                                NothingRed
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRawText = !showRawText },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (initialDraft.format == TicketFormat.UNKNOWN) {
                                        "EXTRACTED RAW TEXT (UNKNOWN FORMAT)"
                                    } else {
                                        "EXTRACTED SOURCE TEXT"
                                    },
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (showRawText) "[HIDE]" else "[SHOW]",
                                    fontFamily = DotoFontFamily,
                                    fontSize = 11.sp,
                                    color = NothingRed
                                )
                            }
                            if (showRawText) {
                                Spacer(modifier = Modifier.height(10.dp))
                                PerforatedDivider()
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = initialDraft.rawText,
                                    fontFamily = DotoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("raw_extracted_text_view")
                                )
                            }
                        }
                    }
                }

                // Validation error banner
                if (validationError != null) {
                    item {
                        NothingCard(borderColor = NothingRed) {
                            Text(
                                text = validationError!!,
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NothingRed
                            )
                        }
                    }
                }

                // 1. Mode & Reference Card
                item {
                    NothingCard {
                        Text(
                            text = "01 // TICKET IDENTITY",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TripTypeSelectorButton(
                                label = "TRAIN",
                                selected = type == TripType.TRAIN,
                                onClick = { type = TripType.TRAIN },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("type_train_button")
                            )
                            TripTypeSelectorButton(
                                label = "BUS",
                                selected = type == TripType.BUS,
                                onClick = { type = TripType.BUS },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("type_bus_button")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        ConfidenceBadge(
                            level = initialDraft.fieldConfidences["reference"] ?: ConfidenceLevel.MANUAL,
                            hint = initialDraft.fieldHints["reference"] ?: "PNR or Ticket Number"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it },
                            label = { Text("PNR / Ticket Number") },
                            textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 15.sp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_reference")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ConfidenceBadge(
                            level = initialDraft.fieldConfidences["operator"] ?: ConfidenceLevel.MANUAL,
                            hint = initialDraft.fieldHints["operator"] ?: "Train No./Name or Bus Operator"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = operator,
                            onValueChange = { operator = it },
                            label = { Text("Operator / Train No. & Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_operator")
                        )
                    }
                }

                // 2. Route (Origin & Destination)
                item {
                    NothingCard {
                        Text(
                            text = "02 // ROUTE STATIONS",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ConfidenceBadge(
                            level = initialDraft.fieldConfidences["origin"] ?: ConfidenceLevel.MANUAL,
                            hint = initialDraft.fieldHints["origin"] ?: "Origin city or station"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = originName,
                                onValueChange = { originName = it },
                                label = { Text("Origin Station / City") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.68f)
                                    .testTag("input_origin_name")
                            )
                            OutlinedTextField(
                                value = originCode,
                                onValueChange = { originCode = it.uppercase() },
                                label = { Text("Code") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 15.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.32f)
                                    .testTag("input_origin_code")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ConfidenceBadge(
                            level = initialDraft.fieldConfidences["destination"] ?: ConfidenceLevel.MANUAL,
                            hint = initialDraft.fieldHints["destination"] ?: "Destination city or station"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = destinationName,
                                onValueChange = { destinationName = it },
                                label = { Text("Destination Station / City") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.68f)
                                    .testTag("input_destination_name")
                            )
                            OutlinedTextField(
                                value = destinationCode,
                                onValueChange = { destinationCode = it.uppercase() },
                                label = { Text("Code") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 15.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.32f)
                                    .testTag("input_destination_code")
                            )
                        }
                    }
                }

                // 3. Schedule (Normalized ISO Date yyyy-MM-dd & 24h Time HH:mm)
                item {
                    NothingCard {
                        Text(
                            text = "03 // SCHEDULE (ISO 8601)",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ConfidenceBadge(
                            level = initialDraft.fieldConfidences["departure"] ?: ConfidenceLevel.MANUAL,
                            hint = initialDraft.fieldHints["departure"] ?: "Normalized to yyyy-MM-dd & HH:mm"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = departureDate,
                                onValueChange = { departureDate = it },
                                label = { Text("Departure Date (yyyy-MM-dd)") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.6f)
                                    .testTag("input_departure_date")
                            )
                            OutlinedTextField(
                                value = departureTime,
                                onValueChange = { departureTime = it },
                                label = { Text("Time (HH:mm)") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.4f)
                                    .testTag("input_departure_time")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = arrivalDate,
                                onValueChange = { arrivalDate = it },
                                label = { Text("Arrival Date (optional)") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.6f)
                                    .testTag("input_arrival_date")
                            )
                            OutlinedTextField(
                                value = arrivalTime,
                                onValueChange = { arrivalTime = it },
                                label = { Text("Arrival Time") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.4f)
                                    .testTag("input_arrival_time")
                            )
                        }
                    }
                }

                // 4. Status, Fare & Passengers
                item {
                    NothingCard {
                        Text(
                            text = "04 // STATUS, FARE & PASSENGERS",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ConfidenceBadge(
                            level = initialDraft.fieldConfidences["status"] ?: ConfidenceLevel.MANUAL,
                            hint = initialDraft.fieldHints["status"] ?: "Current or Booking status"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = status,
                                onValueChange = { status = it },
                                label = { Text("Current Status (e.g. RLWL/28, Confirmed)") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.55f)
                                    .testTag("input_status")
                            )
                            OutlinedTextField(
                                value = bookingStatus,
                                onValueChange = { bookingStatus = it },
                                label = { Text("Booking Status") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.45f)
                                    .testTag("input_booking_status")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ConfidenceBadge(
                            level = initialDraft.fieldConfidences["amount"] ?: ConfidenceLevel.MANUAL,
                            hint = initialDraft.fieldHints["amount"] ?: "Amount in INR"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = amountRupeesText,
                                onValueChange = { amountRupeesText = it },
                                label = { Text("Total Fare (INR)") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 15.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.5f)
                                    .testTag("input_amount")
                            )
                            OutlinedTextField(
                                value = seatNumbers,
                                onValueChange = { seatNumbers = it },
                                label = { Text("Seats / Coach") },
                                textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.5f)
                                    .testTag("input_seats")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = travelClass,
                            onValueChange = { travelClass = it },
                            label = { Text("Class (e.g. SLEEPER CLASS (SL))") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_travel_class")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = passengerNames,
                            onValueChange = { passengerNames = it },
                            label = { Text("Passengers (Name, Age, Gender)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_passengers")
                        )

                        if (type == TripType.BUS || pickupPoint.isNotBlank() || originStartTime.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = pickupPoint,
                                    onValueChange = { pickupPoint = it },
                                    label = { Text("Pickup Point") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.5f)
                                )
                                OutlinedTextField(
                                    value = dropPoint,
                                    onValueChange = { dropPoint = it },
                                    label = { Text("Drop Point") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.5f)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = originStartTime,
                                onValueChange = { originStartTime = it },
                                label = { Text("Start Time at Origin") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (transactionId.isNotBlank() || type == TripType.TRAIN) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = transactionId,
                                onValueChange = { transactionId = it },
                                label = { Text("Transaction ID (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Save Button
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NothingRed)
                            .clickable {
                                if (originName.isBlank() && originCode.isBlank()) {
                                    validationError = "ORIGIN STATION OR CITY IS REQUIRED"
                                    return@clickable
                                }
                                if (destinationName.isBlank() && destinationCode.isBlank()) {
                                    validationError = "DESTINATION STATION OR CITY IS REQUIRED"
                                    return@clickable
                                }
                                if (departureDate.isBlank()) {
                                    validationError = "DEPARTURE DATE IS REQUIRED (YYYY-MM-DD)"
                                    return@clickable
                                }
                                val parsedPaise = DateTimeNormalizer.parseAmountToPaise(amountRupeesText) ?: 0L
                                val updatedDraft = initialDraft.copy(
                                    type = type,
                                    operator = operator,
                                    travelClass = travelClass,
                                    originName = originName,
                                    originCode = originCode,
                                    destinationName = destinationName,
                                    destinationCode = destinationCode,
                                    pickupPoint = pickupPoint,
                                    dropPoint = dropPoint,
                                    departureDate = departureDate,
                                    departureTime = departureTime.ifBlank { "00:00" },
                                    arrivalDate = arrivalDate,
                                    arrivalTime = arrivalTime,
                                    originStartTime = originStartTime,
                                    amountPaise = parsedPaise,
                                    reference = reference,
                                    bookingStatus = bookingStatus,
                                    currentStatus = status,
                                    status = status.ifBlank { "Confirmed" },
                                    passengerNames = passengerNames,
                                    seatNumbers = seatNumbers,
                                    transactionId = transactionId
                                )
                                onSave(updatedDraft)
                            }
                            .testTag("save_trip_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SAVE TRIP TO DEVICE",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NothingWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripTypeSelectorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NothingRed else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) NothingRed else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = DotoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (selected) NothingWhite else MaterialTheme.colorScheme.onSurface
        )
    }
}
