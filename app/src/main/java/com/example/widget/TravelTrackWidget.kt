package com.example.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.PnrStatusCategory
import com.example.data.SettingsRepository
import com.example.data.Trip
import com.example.data.TripFilter
import com.example.data.TripType
import java.time.LocalDateTime

class TravelTrackWidget : GlanceAppWidget() {

    companion object {
        val COMPACT_2X2 = DpSize(110.dp, 110.dp)
        val LIST_4X2 = DpSize(250.dp, 110.dp)

        private val BgBlack = ColorProvider(Color(0xFF0A0A0A))
        private val CardDark = ColorProvider(Color(0xFF161616))
        private val NothingRed = ColorProvider(Color(0xFFD71921))
        private val TextWhite = ColorProvider(Color(0xFFFFFFFF))
        private val TextMuted = ColorProvider(Color(0xFF9E9E9E))

        // 6. Dot color: green for CONFIRMED, amber for RAC or WAITLISTED, red for CANCELLED, grey for UNKNOWN
        val StatusConfirmedGreen = ColorProvider(Color(0xFF00E676))
        val StatusWaitlistAmber = ColorProvider(Color(0xFFFFB300))
        val StatusCancelledRed = ColorProvider(Color(0xFFD71921))
        val StatusUnknownGrey = ColorProvider(Color(0xFF8E8E8E))

        fun getWidgetDotColor(trip: Trip): ColorProvider {
            return when (trip.statusCategory) {
                PnrStatusCategory.CONFIRMED -> StatusConfirmedGreen
                PnrStatusCategory.RAC, PnrStatusCategory.WAITLISTED -> StatusWaitlistAmber
                PnrStatusCategory.CANCELLED -> StatusCancelledRed
                PnrStatusCategory.UNKNOWN, PnrStatusCategory.CHART_PREPARED -> StatusUnknownGrey
            }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_2X2, LIST_4X2)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.getInstance(context).tripDao()
        val allTrips = runCatching { dao.getAllTripsSnapshot() }.getOrDefault(emptyList())
        val upcoming5Days = TripFilter.filterUpcomingForWidget(
            trips = allTrips,
            now = LocalDateTime.now(),
            maxDays = 5L,
            maxCount = 3
        )
        runCatching { SettingsRepository(context).recordWidgetRefresh() }

        provideContent {
            val size = LocalSize.current
            val isCompact = size.width < 200.dp
            WidgetRootContent(
                trips = upcoming5Days,
                isCompact = isCompact
            )
        }
    }

    @Composable
    private fun WidgetRootContent(
        trips: List<Trip>,
        isCompact: Boolean
    ) {
        val context = LocalContext.current
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BgBlack)
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            // Header — tapping opens the app
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openAppIntent))
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .background(NothingRed)
                        .cornerRadius(4.dp)
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = if (isCompact) "TRIPS · 5D" else "TRAVELTRACK // NEXT 5 DAYS",
                    style = TextStyle(
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "[${trips.size}]",
                    style = TextStyle(
                        color = NothingRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            if (trips.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(CardDark)
                        .cornerRadius(12.dp)
                        .clickable(actionStartActivity(openAppIntent))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.widget_empty_state),
                        style = TextStyle(
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            } else if (isCompact) {
                // 2x2 Compact mode
                val primaryTrip = trips.first()
                CompactWidgetTripCard(context = context, trip = primaryTrip, extraCount = trips.size - 1)
            } else {
                // 4x2 List mode
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    trips.take(3).forEachIndexed { idx, trip ->
                        WidgetTripRow(context = context, trip = trip)
                        if (idx < trips.size - 1) {
                            Spacer(modifier = GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CompactWidgetTripCard(
        context: Context,
        trip: Trip,
        extraCount: Int
    ) {
        val detailIntent = createTripDetailIntent(context, trip.id)
        val dotColor = getWidgetDotColor(trip)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(CardDark)
                .cornerRadius(12.dp)
                .clickable(actionStartActivity(detailIntent))
                .padding(10.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .background(dotColor)
                        .cornerRadius(4.dp)
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "${trip.type.name} · ${trip.effectiveStatus}",
                    style = TextStyle(
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
                Text(
                    text = "#${trip.maskedReferenceLast4()}",
                    style = TextStyle(
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Text(
                text = trip.title,
                style = TextStyle(
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = "${trip.departureDate} ${trip.departureTime}",
                style = TextStyle(
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                ),
                maxLines = 1
            )

            // 6. Show last checked time under trip row
            Text(
                text = trip.formattedLastChecked(),
                style = TextStyle(
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trip.formattedAmount(),
                    style = TextStyle(
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (extraCount > 0) {
                    Text(
                        text = "+$extraCount MORE",
                        style = TextStyle(
                            color = NothingRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun WidgetTripRow(
        context: Context,
        trip: Trip
    ) {
        val detailIntent = createTripDetailIntent(context, trip.id)
        val dotColor = getWidgetDotColor(trip)
        val badgeText = if (trip.type == TripType.TRAIN) "TRAIN" else "BUS"

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(CardDark)
                .cornerRadius(10.dp)
                .clickable(actionStartActivity(detailIntent))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot: green for confirmed, amber for waitlisted/RAC, red for cancelled, grey for unknown
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(dotColor)
                    .cornerRadius(4.dp)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Route + Date/Time + Masked Reference (last 4 only) + Last Checked
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trip.title,
                        style = TextStyle(
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "••${trip.maskedReferenceLast4()}",
                        style = TextStyle(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = "${trip.departureDate} · ${trip.departureTime}",
                    style = TextStyle(
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
                // 6. Show the last checked time under the trip row in small text, e.g. "Checked 2h ago"
                Text(
                    text = "${trip.effectiveStatus} · ${trip.formattedLastChecked()}",
                    style = TextStyle(
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Type (TRAIN or BUS) and Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = badgeText,
                    style = TextStyle(
                        color = NothingRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
                Text(
                    text = trip.formattedAmount(),
                    style = TextStyle(
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }

    private fun createTripDetailIntent(context: Context, tripId: Long): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("traveltrack://trip/$tripId")
            putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}

object TravelTrackWidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        runCatching {
            SettingsRepository(context).recordWidgetRefresh()
            TravelTrackWidget().updateAll(context)
        }
    }

    suspend fun getPlacedWidgetCount(context: Context): Int {
        return runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(TravelTrackWidget::class.java).size
        }.getOrDefault(0)
    }
}
