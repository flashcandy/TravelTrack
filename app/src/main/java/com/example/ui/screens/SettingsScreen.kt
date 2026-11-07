package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.AppSettings
import com.example.ui.components.NothingCard
import com.example.ui.components.PerforatedDivider
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.StatusUnknownGrey
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingWhite
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusConfirmedGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    settings: AppSettings,
    totalTripCount: Int,
    widgetTripCount: Int,
    isNotificationAccessGranted: Boolean,
    onBack: () -> Unit,
    onToggleDayBefore: (Boolean) -> Unit,
    onToggleTwoHoursBefore: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleAutoCheckPnr: (Boolean) -> Unit,
    onToggleCheckInFinal24Hours: (Boolean) -> Unit,
    onToggleShowCheckConfirmed: (Boolean) -> Unit,
    onToggleAutoSendSms: (Boolean) -> Unit,
    onClearPnrHistory: () -> Unit,
    onRefreshWidgetNow: () -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showSendSmsRationaleDialog by remember { mutableStateOf(false) }

    var hasSendSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSendSmsPermission = granted
        if (granted) {
            onToggleAutoSendSms(true)
        } else {
            onToggleAutoSendSms(false)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    if (showSendSmsRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showSendSmsRationaleDialog = false },
            title = {
                Text(
                    text = "SEND_SMS PERMISSION",
                    fontFamily = DotoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Enabling 'Send automatically' uses SEND_SMS to send 'PNR <PNR>' directly to 139 without opening the Messages app. All replies are read strictly offline.",
                    fontFamily = SpaceGroteskFontFamily
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSendSmsRationaleDialog = false
                        sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }
                ) {
                    Text(
                        text = "GRANT PERMISSION",
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendSmsRationaleDialog = false }) {
                    Text(text = "CANCEL", fontFamily = DotoFontFamily)
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    text = "DELETE ALL DATA?",
                    fontFamily = DotoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This permanently deletes all $totalTripCount saved trips, cancels scheduled departure alarms, and clears the home screen widget.",
                    fontFamily = SpaceGroteskFontFamily
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        onDeleteAllData()
                    },
                    modifier = Modifier.testTag("confirm_delete_all_button")
                ) {
                    Text(
                        text = "DELETE ALL",
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(
                        text = "CANCEL",
                        fontFamily = DotoFontFamily
                    )
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(
                    text = "CLEAR PNR CHECK HISTORY?",
                    fontFamily = DotoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This removes all past 139 check logs and debugging raw replies from the local database. Trip current statuses will remain unchanged.",
                    fontFamily = SpaceGroteskFontFamily
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearPnrHistory()
                    },
                    modifier = Modifier.testTag("confirm_clear_pnr_history_button")
                ) {
                    Text(
                        text = "CLEAR HISTORY",
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(text = "CANCEL", fontFamily = DotoFontFamily)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("settings_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_traveltrack_logo),
                                contentDescription = "TravelTrack Logo",
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SETTINGS",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "NOTHING OS // 100% OFFLINE PRIVACY",
                                    fontFamily = DotoFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 1. PNR LIVE STATUS CHECKING (139 SMS & Notification Listener)
                item {
                    NothingCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Message,
                                contentDescription = null,
                                tint = NothingRed
                            )
                            Text(
                                text = "139 PNR STATUS CHECKER",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sends 'PNR <PNR>' to Indian Railways 139 service. The app automatically reads replies posted by your Messages app via Notification Listener Service. Strictly offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Notification Listener Access Status Badge & Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "NOTIFICATION ACCESS",
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Badge
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isNotificationAccessGranted) StatusConfirmedGreen else StatusUnknownGrey
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isNotificationAccessGranted) "ENABLED" else "NOT ENABLED",
                                            fontFamily = DotoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isNotificationAccessGranted) StatusConfirmedGreen else StatusUnknownGrey
                                        )
                                    }
                                }

                                Text(
                                    text = if (isNotificationAccessGranted) {
                                        "Access granted. TravelTrack reads incoming 139 notifications and updates your ticket status automatically."
                                    } else {
                                        "Required to read 139 replies automatically from your messaging app notification without requesting SMS read permissions."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        runCatching { context.startActivity(intent) }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_notification_access_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null,
                                        tint = if (isNotificationAccessGranted) MaterialTheme.colorScheme.onSurface else NothingRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isNotificationAccessGranted) "MANAGE NOTIFICATION ACCESS" else "ENABLE NOTIFICATION ACCESS",
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isNotificationAccessGranted) MaterialTheme.colorScheme.onSurface else NothingRed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        PerforatedDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Auto-send toggle
                        SettingToggleRow(
                            title = "Send automatically via SMS",
                            subtitle = "Sends SMS directly using SmsManager instead of opening a draft (requires SEND_SMS permission)",
                            checked = settings.autoSendSms,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (hasSendSmsPermission) {
                                        onToggleAutoSendSms(true)
                                    } else {
                                        showSendSmsRationaleDialog = true
                                    }
                                } else {
                                    onToggleAutoSendSms(false)
                                }
                            },
                            testTag = "toggle_auto_send_sms"
                        )

                        // Auto-check toggle: HIDDEN if notification access is NOT granted
                        if (isNotificationAccessGranted) {
                            SettingToggleRow(
                                title = "Auto check PNR status",
                                subtitle = "Periodic WorkManager check every 6 hours for waitlisted trains within 5 days",
                                checked = settings.autoCheckPnr,
                                onCheckedChange = onToggleAutoCheckPnr,
                                testTag = "toggle_auto_check_pnr"
                            )

                            SettingToggleRow(
                                title = "Check in the final 24 hours",
                                subtitle = "Increase frequency to every 2 hours in the last 24 hours prior to departure",
                                checked = settings.checkInFinal24Hours,
                                onCheckedChange = onToggleCheckInFinal24Hours,
                                testTag = "toggle_check_final_24h"
                            )

                            SettingToggleRow(
                                title = "Show check for confirmed trains",
                                subtitle = "Allow manual 139 check button on confirmed tickets to check coach & seat numbers",
                                checked = settings.showCheckForConfirmed,
                                onCheckedChange = onToggleShowCheckConfirmed,
                                testTag = "toggle_show_check_confirmed"
                            )
                        } else {
                            Text(
                                text = "Auto-check toggle is hidden until Notification Access is enabled above, so replies can be captured automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Clear PNR check history button
                        OutlinedButton(
                            onClick = { showClearHistoryDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("clear_pnr_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CLEAR PNR CHECK HISTORY",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 2. Notifications Card
                item {
                    NothingCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = NothingRed
                            )
                            Text(
                                text = "DEPARTURE ALERTS",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.notification_permission_rationale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("request_notification_permission_button")
                            ) {
                                Text(
                                    text = "GRANT NOTIFICATION PERMISSION",
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = NothingRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        PerforatedDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingToggleRow(
                            title = "1 Day Before Departure",
                            subtitle = "Alert 24 hours prior with route and departure time",
                            checked = settings.notifyDayBefore,
                            onCheckedChange = onToggleDayBefore,
                            testTag = "toggle_notify_day_before"
                        )

                        SettingToggleRow(
                            title = "2 Hours Before Departure",
                            subtitle = "Alert 2 hours prior via exact/idle alarm & boot receiver",
                            checked = settings.notifyTwoHoursBefore,
                            onCheckedChange = onToggleTwoHoursBefore,
                            testTag = "toggle_notify_two_hours_before"
                        )
                    }
                }

                // 3. Home Screen Widget Refresh Info Card
                item {
                    val lastRefreshFormatted = remember(settings.lastWidgetRefreshEpochMillis) {
                        if (settings.lastWidgetRefreshEpochMillis <= 0L) {
                            "PENDING INITIAL SYNC"
                        } else {
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(ZoneId.systemDefault())
                                .format(Instant.ofEpochMilli(settings.lastWidgetRefreshEpochMillis))
                        }
                    }

                    NothingCard {
                        Text(
                            text = "JETPACK GLANCE WIDGET",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Sizes: 2x2 (Compact) and 4x2 (List) via Responsive SizeMode\n" +
                                "• Window: Upcoming trips within next 5 days (max 3 shown)\n" +
                                "• Dot color: Green (CNF), Amber (RAC/WL), Red (Cancelled), Grey (Unknown)\n" +
                                "• Info: Displays last checked time under each row, e.g. 'Checked 2h ago'\n" +
                                "• Schedule: Refreshes immediately on status change + WorkManager every 6h & at midnight",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PerforatedDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TRIPS IN 5-DAY WINDOW",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$widgetTripCount / 3 MAX",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LAST WIDGET REFRESH",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastRefreshFormatted,
                                fontFamily = DotoFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = onRefreshWidgetNow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("refresh_widget_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FORCE WIDGET REFRESH NOW",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 4. Appearance (Nothing OS Full-Dark vs Light)
                item {
                    NothingCard {
                        Text(
                            text = "NOTHING OS DISPLAY THEME",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingToggleRow(
                            title = "Full-Dark Monochrome Theme",
                            subtitle = "Black #000000 base with #D71921 accent (Android 12+ dynamic color disabled)",
                            checked = settings.darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            testTag = "toggle_dark_theme"
                        )
                    }
                }

                // 5. Delete All Data
                item {
                    NothingCard(borderColor = NothingRed) {
                        Text(
                            text = "DATA & PRIVACY (100% OFFLINE)",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All ticket files, OCR text, and Room database records stay strictly on your device. No internet permission is requested.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NothingRed)
                                .clickable { showDeleteAllDialog = true }
                                .testTag("delete_all_data_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteForever,
                                    contentDescription = null,
                                    tint = NothingWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_delete_all_data),
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NothingWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NothingWhite,
                checkedTrackColor = NothingRed
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
