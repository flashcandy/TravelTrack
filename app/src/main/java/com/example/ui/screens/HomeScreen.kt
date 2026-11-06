package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.example.R
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Trip
import com.example.data.TripType
import com.example.ui.HomeTripsUiState
import com.example.ui.components.NothingCard
import com.example.ui.components.PerforatedDivider
import com.example.ui.components.StatusPill
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingWhite
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.widget.NothingWidgetPreviewCard

@Composable
fun HomeScreen(
    uiState: HomeTripsUiState,
    onAddTicketClick: () -> Unit,
    onTripClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onLoadSampleTickets: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWidgetPreview by remember { mutableStateOf(false) }
    var compactWidgetMode by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTicketClick,
                containerColor = NothingRed,
                contentColor = NothingWhite,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier
                    .size(60.dp)
                    .testTag("add_ticket_fab")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add Ticket",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
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
                    .testTag("home_trip_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    HomeTopBar(
                        upcomingCount = uiState.upcomingTrips.size,
                        showWidgetPreview = showWidgetPreview,
                        onToggleWidgetPreview = { showWidgetPreview = !showWidgetPreview },
                        onSettingsClick = onSettingsClick
                    )
                }

                // Status feedback banner
                if (uiState.statusBannerMessage != null) {
                    item {
                        NothingCard(
                            borderColor = NothingRed,
                            onClick = onDismissBanner,
                            modifier = Modifier.testTag("status_feedback_banner")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.statusBannerMessage,
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "[DISMISS]",
                                    fontFamily = DotoFontFamily,
                                    fontSize = 10.sp,
                                    color = NothingRed
                                )
                            }
                        }
                    }
                }

                // Interactive Home Screen Widget Preview (2x2 Compact & 4x2 List)
                item {
                    AnimatedVisibility(
                        visible = showWidgetPreview,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180))
                    ) {
                        NothingCard(
                            modifier = Modifier.testTag("home_widget_preview_section")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GLANCE WIDGET // 5-DAY WINDOW",
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SizeChip(
                                        label = "4x2 LIST",
                                        selected = !compactWidgetMode,
                                        onClick = { compactWidgetMode = false }
                                    )
                                    SizeChip(
                                        label = "2x2 COMPACT",
                                        selected = compactWidgetMode,
                                        onClick = { compactWidgetMode = true }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                NothingWidgetPreviewCard(
                                    trips = uiState.widgetUpcomingTrips,
                                    isCompact2x2 = compactWidgetMode,
                                    onTripClick = onTripClick
                                )
                            }
                        }
                    }
                }

                if (uiState.allTrips.isEmpty()) {
                    item {
                        EmptyTripsCard(
                            onAddTicketClick = onAddTicketClick,
                            onLoadSampleTickets = onLoadSampleTickets
                        )
                    }
                } else {
                    // UPCOMING SECTION
                    item {
                        SectionHeader(
                            title = "UPCOMING",
                            count = uiState.upcomingTrips.size,
                            accentDot = true,
                            modifier = Modifier.testTag("section_upcoming_header")
                        )
                    }

                    if (uiState.upcomingTrips.isEmpty()) {
                        item {
                            NothingCard {
                                Text(
                                    text = "NO UPCOMING TRIPS SCHEDULED",
                                    fontFamily = DotoFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(
                            items = uiState.upcomingTrips,
                            key = { "upcoming_${it.id}" }
                        ) { trip ->
                            TripItemCard(
                                trip = trip,
                                isPast = false,
                                onClick = { onTripClick(trip.id) }
                            )
                        }
                    }

                    // PAST SECTION
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "PAST",
                            count = uiState.pastTrips.size,
                            accentDot = false,
                            modifier = Modifier.testTag("section_past_header")
                        )
                    }

                    if (uiState.pastTrips.isEmpty()) {
                        item {
                            NothingCard {
                                Text(
                                    text = "NO PAST TRIPS RECORDED",
                                    fontFamily = DotoFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(
                            items = uiState.pastTrips,
                            key = { "past_${it.id}" }
                        ) { trip ->
                            TripItemCard(
                                trip = trip,
                                isPast = true,
                                onClick = { onTripClick(trip.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    upcomingCount: Int,
    showWidgetPreview: Boolean,
    onToggleWidgetPreview: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_traveltrack_logo),
                    contentDescription = "TravelTrack Logo",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TRAVELTRACK",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "OFFLINE TICKET PARSER // $upcomingCount UPCOMING",
                fontFamily = DotoFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleWidgetPreview,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("toggle_widget_preview_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = "Toggle Widget Preview",
                    tint = if (showWidgetPreview) NothingRed else MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    accentDot: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (accentDot) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontFamily = DotoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "[$count]",
            fontFamily = DotoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripItemCard(
    trip: Trip,
    isPast: Boolean,
    onClick: () -> Unit
) {
    NothingCard(
        onClick = onClick,
        borderColor = if (trip.isWaitlisted() && !isPast) {
            NothingRed.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.outline
        },
        modifier = Modifier.testTag("trip_item_card_${trip.id}")
    ) {
        // Top row: Type icon + Operator + Status Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (trip.type == TripType.TRAIN) {
                        Icons.Outlined.Train
                    } else {
                        Icons.Outlined.DirectionsBus
                    },
                    contentDescription = trip.type.name,
                    tint = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else NothingRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${trip.type.name} // ${trip.operator}",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                status = trip.status,
                isWaitlisted = trip.isWaitlisted()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Route Title + Station codes
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (trip.originCode.isNotBlank() || trip.destinationCode.isNotBlank()) {
                    Text(
                        text = "${trip.originCode.ifBlank { "---" }} ➔ ${trip.destinationCode.ifBlank { "---" }}",
                        fontFamily = DotoFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = trip.formattedAmount(),
                fontFamily = DotoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PerforatedDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Bottom row: Departure Date/Time + PNR/Reference
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "DEPARTURE",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${trip.departureDate}  ${trip.departureTime}",
                    fontFamily = DotoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (trip.type == TripType.TRAIN) "PNR" else "TICKET REF",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (trip.reference.length > 14) {
                        "••${trip.maskedReferenceLast4()}"
                    } else {
                        trip.reference.ifBlank { "N/A" }
                    },
                    fontFamily = DotoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyTripsCard(
    onAddTicketClick: () -> Unit,
    onLoadSampleTickets: () -> Unit
) {
    NothingCard(
        modifier = Modifier.testTag("empty_trips_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(1.dp, NothingRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_traveltrack_logo),
                    contentDescription = "TravelTrack Logo",
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "NO TICKETS ON DEVICE",
                fontFamily = DotoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Upload an IRCTC train ERS or redBus / TSRTC ticket (PDF, JPG, PNG) or share directly from another app. All OCR & parsing happens 100% offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onLoadSampleTickets,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("load_sample_tickets_button")
                ) {
                    Text(
                        text = "LOAD SAMPLES",
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NothingRed)
                        .clickable(onClick = onAddTicketClick)
                        .testTag("empty_state_upload_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ UPLOAD TICKET",
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = NothingWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun SizeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NothingRed else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) NothingRed else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = DotoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (selected) NothingWhite else MaterialTheme.colorScheme.onSurface
        )
    }
}
