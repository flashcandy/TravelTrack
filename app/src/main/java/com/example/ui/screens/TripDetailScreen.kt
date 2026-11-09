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
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PnrStatusCategory
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
    pendingPnrSecondsRemaining: Int,
    isPnrCheckPending: Boolean,
    showCheckForConfirmed: Boolean,
    onBack: () -> Unit,
    onEditTrip: (Trip) -> Unit,
    onDeleteTrip: (Trip) -> Unit,
    onCheckPnrStatus: (Trip) -> Unit,
    onCheckTelegramBot: (Trip) -> Unit = {},
    onCheckClipboard: () -> Unit = {},
    onCancelPendingCheck: () -> Unit,
    onUpdateStatusManually: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showManualUpdateDialog by remember { mutableStateOf(false) }
    var revealFullPnr by remember { mutableStateOf(false) }

    // Re-hide full PNR automatically whenever the user navigates away or views a different trip
    DisposableEffect(trip?.id) {
        onDispose {
            revealFullPnr = false
        }
    }

    BackHandler {
        revealFullPnr = false
        onBack()
    }

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
                    text = "Remove '${trip.title}' (${trip.maskedReferenceLast4()}) from your device?",
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

    if (showManualUpdateDialog && trip != null) {
        ManualStatusUpdateDialog(
            currentStatus = trip.effectiveStatus,
            onDismiss = { showManualUpdateDialog = false },
            onSave = { updatedStatus ->
                showManualUpdateDialog = false
                onUpdateStatusManually(trip.id, updatedStatus)
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
                                        text = "${trip.type.name} // ${trip.formattedLastChecked()}",
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

                    // 4. Waitlisted Warning Banner
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

                    // 2. LIVE PNR CHECKING: 139 SMS & Telegram Bot (@check_pnr_status_bot) + Auto-Clipboard
                    val canCheckPnr = trip.isPnrCheckEligible(allowConfirmed = showCheckForConfirmed)
                    if (canCheckPnr) {
                        item {
                            NothingCard(
                                borderColor = if (isPnrCheckPending) NothingRed else MaterialTheme.colorScheme.outline
                            ) {
                                if (isPnrCheckPending) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                CircularProgressIndicator(
                                                    color = NothingRed,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "WAITING FOR PNR REPLY...",
                                                        fontFamily = DotoFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    val mins = pendingPnrSecondsRemaining / 60
                                                    val secs = pendingPnrSecondsRemaining % 60
                                                    Text(
                                                        text = String.format(
                                                            "Timeout in %02d:%02d · Copy bot reply & return here (auto-detects clipboard!)",
                                                            mins,
                                                            secs
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "[CANCEL]",
                                                fontFamily = DotoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = NothingRed,
                                                modifier = Modifier
                                                    .clickable(onClick = onCancelPendingCheck)
                                                    .padding(8.dp)
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = onCheckClipboard,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("paste_from_clipboard_pending_button")
                                        ) {
                                            Text(
                                                text = "READ COPIED BOT / 139 REPLY FROM CLIPBOARD NOW",
                                                fontFamily = DotoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = NothingRed
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Option A: 139 SMS Check
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "CHECK LIVE PNR STATUS (SMS 139)",
                                                    fontFamily = DotoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Sends 'PNR ${trip.cleanPnr()}' to 139. Replies are parsed automatically 100% offline.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(NothingRed)
                                                    .clickable { onCheckPnrStatus(trip) }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                                    .testTag("check_pnr_status_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Send,
                                                        contentDescription = null,
                                                        tint = NothingWhite,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "CHECK 139",
                                                        fontFamily = DotoFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = NothingWhite
                                                    )
                                                }
                                            }
                                        }

                                        PerforatedDivider()

                                        // Option B: Telegram @check_pnr_status_bot Check
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "TELEGRAM BOT (@check_pnr_status_bot)",
                                                    fontFamily = DotoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "1. Tap button (copies PNR & opens bot)\n2. Paste & send PNR in Telegram\n3. Copy bot's reply & switch back here — status updates automatically!",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        width = 1.dp,
                                                        color = NothingRed,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable { onCheckTelegramBot(trip) }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                                    .testTag("check_telegram_bot_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Message,
                                                        contentDescription = null,
                                                        tint = NothingRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "TELEGRAM BOT",
                                                        fontFamily = DotoFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = NothingRed
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Boarding Pass Card
                    item {
                        NothingCard {
                            // Single horizontal line showing train / operator name and status pill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = trip.operatorDisplay(),
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusPill(
                                    status = trip.displayStatus(),
                                    statusCategory = trip.statusCategory
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Route Codes + Names
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trip.originName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trip.destinationName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trip.departureTime.ifBlank { "--:--" },
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trip.departureDate,
                                        fontFamily = DotoFontFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "ARRIVAL",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trip.arrivalTime ?: "--:--",
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trip.arrivalDate ?: "N/A",
                                        fontFamily = DotoFontFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                            "PNR NUMBER (TAP TO TOGGLE)"
                                        } else {
                                            "TICKET REFERENCE"
                                        },
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Masked display "•••• 8777" (last 4 digits only), tap to reveal while on this screen
                                    val displayPnr = if (revealFullPnr) {
                                        trip.cleanPnr().ifBlank { trip.reference }
                                    } else {
                                        trip.formattedMaskedPnr()
                                    }
                                    Text(
                                        text = displayPnr,
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clickable { revealFullPnr = !revealFullPnr }
                                            .testTag("detail_reference_value")
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "TOTAL FARE (${trip.currency})",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trip.formattedAmount(),
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        color = NothingRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // LIVE STATUS SECTION
                    item {
                        NothingCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE STATUS",
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (trip.type == TripType.TRAIN) {
                                        Text(
                                            text = "[PASTE BOT REPLY]",
                                            fontFamily = DotoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .clickable { onCheckClipboard() }
                                                .padding(4.dp)
                                                .testTag("paste_bot_reply_button")
                                        )
                                    }
                                    Text(
                                        text = "[UPDATE MANUALLY]",
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = NothingRed,
                                        modifier = Modifier
                                            .clickable { showManualUpdateDialog = true }
                                            .padding(4.dp)
                                            .testTag("update_status_manually_button")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            DetailRow(
                                label = "Current status",
                                value = if (!trip.currentStatus.isNullOrBlank()) trip.currentStatus else "Not checked yet",
                                useDoto = true
                            )

                            DetailRow(
                                label = "Booking status",
                                value = if (!trip.bookingStatus.isNullOrBlank()) trip.bookingStatus else trip.status,
                                useDoto = true
                            )

                            DetailRow(
                                label = "Last checked",
                                value = if (!trip.lastCheckedAt.isNullOrBlank()) {
                                    "${trip.lastCheckedAt} (${trip.formattedLastChecked()})"
                                } else {
                                    "Not checked yet"
                                },
                                useDoto = true
                            )

                            // Raw SMS reply card for debugging / manual verification
                            if (!trip.lastRawSmsReply.isNullOrBlank()) {
                                DetailRow(
                                    label = "Last 139 SMS reply (debug)",
                                    value = trip.lastRawSmsReply,
                                    useDoto = false
                                )
                            }

                            if (!trip.travelClass.isNullOrBlank()) {
                                DetailRow(label = "Class", value = trip.travelClass)
                            }
                            if (!trip.seatNumbers.isNullOrBlank()) {
                                DetailRow(label = "Seats / Berth", value = trip.seatNumbers, useDoto = true)
                            }
                            val passengerText = trip.formattedPassengersDisplay()
                            if (passengerText.isNotBlank()) {
                                DetailRow(label = "Passengers", value = passengerText)
                            }
                            if (!trip.pickupPoint.isNullOrBlank()) {
                                DetailRow(label = "Pickup point", value = trip.pickupPoint)
                            }
                            if (!trip.dropPoint.isNullOrBlank()) {
                                DetailRow(label = "Drop point", value = trip.dropPoint)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualStatusUpdateDialog(
    currentStatus: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(PnrStatusCategory.CONFIRMED) }
    var customStatusText by remember { mutableStateOf(currentStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "MANUAL STATUS UPDATE",
                fontFamily = DotoFontFamily,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select a category and edit the exact status code (e.g. CNF/B2/31, RAC/12, WL/3):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryChip("CONFIRMED", selectedCategory == PnrStatusCategory.CONFIRMED) {
                        selectedCategory = PnrStatusCategory.CONFIRMED
                        if (customStatusText.isBlank() || customStatusText.startsWith("WL") || customStatusText.startsWith("RAC")) {
                            customStatusText = "CNF/B2/31"
                        }
                    }
                    CategoryChip("RAC", selectedCategory == PnrStatusCategory.RAC) {
                        selectedCategory = PnrStatusCategory.RAC
                        if (customStatusText.isBlank() || customStatusText.startsWith("CNF") || customStatusText.startsWith("WL")) {
                            customStatusText = "RAC/12"
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryChip("WAITLISTED", selectedCategory == PnrStatusCategory.WAITLISTED) {
                        selectedCategory = PnrStatusCategory.WAITLISTED
                        if (customStatusText.isBlank() || customStatusText.startsWith("CNF") || customStatusText.startsWith("RAC")) {
                            customStatusText = "WL/3"
                        }
                    }
                    CategoryChip("CANCELLED", selectedCategory == PnrStatusCategory.CANCELLED) {
                        selectedCategory = PnrStatusCategory.CANCELLED
                        customStatusText = "CANCELLED"
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = customStatusText,
                    onValueChange = { customStatusText = it },
                    label = { Text("Exact Status Code") },
                    textStyle = TextStyle(fontFamily = DotoFontFamily, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_status_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(customStatusText) },
                modifier = Modifier.testTag("save_manual_status_button")
            ) {
                Text(
                    text = "SAVE STATUS",
                    fontFamily = DotoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = NothingRed
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCEL", fontFamily = DotoFontFamily)
            }
        }
    )
}

@Composable
private fun CategoryChip(
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
            fontSize = 11.sp,
            color = if (selected) NothingWhite else MaterialTheme.colorScheme.onSurface
        )
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
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = if (useDoto) DotoFontFamily else SpaceGroteskFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
