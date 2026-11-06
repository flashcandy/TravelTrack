package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Trip
import com.example.data.TripType
import com.example.ui.components.NothingCard
import com.example.ui.components.PerforatedDivider
import com.example.ui.components.StatusPill
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingWhite
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun TripDetailScreen(
    trip: Trip?,
    onBack: () -> Unit,
    onEditTrip: (Trip) -> Unit,
    onDeleteTrip: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && trip != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "DELETE TRIP?",
                    fontFamily = DotoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove '${trip.title}' (${trip.reference}) from your device?",
                    fontFamily = SpaceGroteskFontFamily
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteTrip(trip)
                    },
                    modifier = Modifier.testTag("confirm_delete_trip_button")
                ) {
                    Text(
                        text = "DELETE",
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        text = "CANCEL",
                        fontFamily = DotoFontFamily
                    )
                }
            }
        )
    }

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
            if (trip == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TRIP NOT FOUND",
                        fontFamily = DotoFontFamily,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .testTag("trip_detail_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with Edit & Delete
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("detail_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = "BOARDING PASS",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${trip.type.name} // ID #${trip.id}",
                                        fontFamily = DotoFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { onEditTrip(trip) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("edit_trip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.action_edit_trip),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_trip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.action_delete_trip),
                                        tint = NothingRed
                                    )
                                }
                            }
                        }
                    }

                    // Waitlisted Warning Banner when status starts with RLWL, PQWL, RSWL, or WL
                    if (trip.isWaitlisted()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NothingRed)
                                    .padding(16.dp)
                                    .testTag("waitlist_warning_banner")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = "Waitlist Warning",
                                        tint = NothingWhite,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.waitlist_warning_banner),
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = NothingWhite
                                    )
                                }
                            }
                        }
                    }

                    // Main Boarding Pass Card
                    item {
                        NothingCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = trip.operator,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusPill(
                                    status = trip.status,
                                    isWaitlisted = trip.isWaitlisted()
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Big Route Codes + Names
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trip.originCode.ifBlank {
                                            trip.originName.take(3).uppercase()
                                        },
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = trip.originName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "───►",
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = NothingRed,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = trip.destinationCode.ifBlank {
                                            trip.destinationName.take(3).uppercase()
                                        },
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = trip.destinationName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            PerforatedDivider()
                            Spacer(modifier = Modifier.height(18.dp))

                            // Departure & Arrival Times
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "DEPARTURE",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = trip.departureTime.ifBlank { "--:--" },
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = trip.departureDate,
                                        fontFamily = DotoFontFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "ARRIVAL",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = trip.arrivalTime ?: "--:--",
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = trip.arrivalDate ?: "N/A",
                                        fontFamily = DotoFontFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            PerforatedDivider()
                            Spacer(modifier = Modifier.height(18.dp))

                            // Reference (PNR / Ticket No) & Total Amount
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (trip.type == TripType.TRAIN) {
                                            "PNR NUMBER"
                                        } else {
                                            "TICKET REFERENCE"
                                        },
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = trip.reference.ifBlank { "N/A" },
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("detail_reference_value")
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "TOTAL FARE (${trip.currency})",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = trip.formattedAmount(),
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        color = NothingRed
                                    )
                                }
                            }
                        }
                    }

                    // Secondary Details Card (Class, Seats, Passengers, Booking Status, Boarding)
                    item {
                        NothingCard {
                            Text(
                                text = "PASSENGER & TICKET METADATA",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            DetailRow(
                                label = "CURRENT STATUS",
                                value = trip.status,
                                useDoto = true
                            )
                            if (!trip.bookingStatus.isNullOrBlank()) {
                                DetailRow(
                                    label = "BOOKING STATUS",
                                    value = trip.bookingStatus,
                                    useDoto = true
                                )
                            }
                            if (!trip.travelClass.isNullOrBlank()) {
                                DetailRow(
                                    label = "CLASS",
                                    value = trip.travelClass
                                )
                            }
                            if (!trip.seatNumbers.isNullOrBlank()) {
                                DetailRow(
                                    label = "SEATS / BERTH",
                                    value = trip.seatNumbers,
                                    useDoto = true
                                )
                            }
                            if (trip.passengerNames.isNotBlank()) {
                                DetailRow(
                                    label = "PASSENGERS",
                                    value = trip.passengerNames
                                )
                            }
                            if (!trip.pickupPoint.isNullOrBlank()) {
                                DetailRow(
                                    label = "PICKUP POINT",
                                    value = trip.pickupPoint
                                )
                            }
                            if (!trip.dropPoint.isNullOrBlank()) {
                                DetailRow(
                                    label = "DROP POINT",
                                    value = trip.dropPoint
                                )
                            }
                            if (!trip.originStartTime.isNullOrBlank()) {
                                DetailRow(
                                    label = "ORIGIN START TIME",
                                    value = trip.originStartTime,
                                    useDoto = true
                                )
                            }
                            if (!trip.transactionId.isNullOrBlank()) {
                                DetailRow(
                                    label = "TRANSACTION ID",
                                    value = trip.transactionId,
                                    useDoto = true
                                )
                            }
                            DetailRow(
                                label = "WIDGET PRIVACY MASK",
                                value = "••${trip.maskedReferenceLast4()} (passenger names hidden)",
                                useDoto = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    useDoto: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = if (useDoto) DotoFontFamily else SpaceGroteskFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
