package com.example.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PnrStatusCategory
import com.example.data.Trip
import com.example.data.TripType
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

/**
 * Compose representation of the Glance widget in both 2x2 (Compact) and 4x2 (List) sizes,
 * used both for Android Studio @Preview and for the interactive Live Widget Preview inside the app.
 */
@Composable
fun NothingWidgetPreviewCard(
    trips: List<Trip>,
    isCompact2x2: Boolean,
    onTripClick: (Long) -> Unit = {},
    onHeaderClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bgBlack = Color(0xFF0A0A0A)
    val cardDark = Color(0xFF161616)
    val borderGrey = Color(0xFF2A2A2A)
    val nothingRed = Color(0xFFD71921)
    val textWhite = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF9E9E9E)

    // 6. Dot color: green for CONFIRMED, amber for RAC or WAITLISTED, red for CANCELLED, grey for UNKNOWN
    val statusGreen = Color(0xFF00E676)
    val statusAmber = Color(0xFFFFB300)
    val statusRed = Color(0xFFD71921)
    val statusGrey = Color(0xFF8E8E8E)

    fun resolveDotColor(trip: Trip): Color {
        return when (trip.statusCategory) {
            PnrStatusCategory.CONFIRMED -> statusGreen
            PnrStatusCategory.RAC, PnrStatusCategory.WAITLISTED -> statusAmber
            PnrStatusCategory.CANCELLED -> statusRed
            PnrStatusCategory.UNKNOWN, PnrStatusCategory.CHART_PREPARED -> statusGrey
        }
    }

    Column(
        modifier = modifier
            .then(
                if (isCompact2x2) Modifier.width(180.dp) else Modifier.fillMaxWidth()
            )
            .clip(RoundedCornerShape(16.dp))
            .background(bgBlack)
            .border(1.dp, borderGrey, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHeaderClick() }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(nothingRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isCompact2x2) "TRIPS · 5D" else "TRAVELTRACK // NEXT 5 DAYS",
                color = textWhite,
                fontFamily = DotoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "[${trips.size}]",
                color = nothingRed,
                fontFamily = DotoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        if (trips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardDark)
                    .border(1.dp, borderGrey, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No trips in the next 5 days",
                    color = textMuted,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 12.sp
                )
            }
        } else if (isCompact2x2) {
            val trip = trips.first()
            val dotColor = resolveDotColor(trip)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardDark)
                    .border(1.dp, borderGrey, RoundedCornerShape(12.dp))
                    .clickable { onTripClick(trip.id) }
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${trip.type.name} · ${trip.effectiveStatus}",
                        color = textMuted,
                        fontFamily = DotoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "#${trip.maskedReferenceLast4()}",
                        color = textMuted,
                        fontFamily = DotoFontFamily,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = trip.title,
                    color = textWhite,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${trip.departureDate} ${trip.departureTime}",
                    color = textMuted,
                    fontFamily = DotoFontFamily,
                    fontSize = 11.sp
                )
                Text(
                    text = trip.formattedLastChecked(),
                    color = textMuted,
                    fontFamily = DotoFontFamily,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trip.formattedAmount(),
                        color = textWhite,
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (trips.size > 1) {
                        Text(
                            text = "+${trips.size - 1} MORE",
                            color = nothingRed,
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                trips.take(3).forEach { trip ->
                    val dotColor = resolveDotColor(trip)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardDark)
                            .border(1.dp, borderGrey, RoundedCornerShape(10.dp))
                            .clickable { onTripClick(trip.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = trip.title,
                                    color = textWhite,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "••${trip.maskedReferenceLast4()}",
                                    color = textMuted,
                                    fontFamily = DotoFontFamily,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "${trip.departureDate} · ${trip.departureTime}",
                                color = textMuted,
                                fontFamily = DotoFontFamily,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${trip.effectiveStatus} · ${trip.formattedLastChecked()}",
                                color = textMuted,
                                fontFamily = DotoFontFamily,
                                fontSize = 9.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = trip.type.name,
                                color = nothingRed,
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Text(
                                text = trip.formattedAmount(),
                                color = textWhite,
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PreviewSampleTrips = listOf(
    Trip(
        id = 1L,
        type = TripType.TRAIN,
        operator = "11020/KONARK EXPRESS",
        title = "Khammam → Secunderabad",
        originName = "KHAMMAM",
        originCode = "KMT",
        destinationName = "SECUNDERABAD JN.",
        destinationCode = "SC",
        departureDateTime = "2026-11-10T06:35",
        arrivalDateTime = "2026-11-10T10:50",
        amount = 20180L,
        reference = "6952208777",
        status = "RLWL/29",
        currentStatus = "RLWL/28",
        lastCheckedAt = "2026-11-06T06:30",
        passengerNames = "SAI CHAND NOMULA"
    ),
    Trip(
        id = 2L,
        type = TripType.BUS,
        operator = "TELANGANA STATE ROAD TRANSPORT CORPORATION",
        title = "Khammam → KPHB",
        originName = "KHAMMAM BS",
        originCode = "KHM",
        destinationName = "KPHB",
        destinationCode = "KPHB",
        departureDateTime = "2026-11-11T23:55",
        amount = 125000L,
        reference = "TS260115130655852823RERD#TG26011518365612WD2H##",
        status = "Confirmed",
        passengerNames = "VIJAYA NOMULA, SAI CHAND NOMULA"
    )
)

@Preview(name = "2x2 Compact Widget", widthDp = 190, heightDp = 165)
@Composable
fun TravelTrackWidgetCompactPreview() {
    NothingWidgetPreviewCard(
        trips = PreviewSampleTrips,
        isCompact2x2 = true
    )
}

@Preview(name = "4x2 List Widget", widthDp = 360, heightDp = 190)
@Composable
fun TravelTrackWidgetListPreview() {
    NothingWidgetPreviewCard(
        trips = PreviewSampleTrips,
        isCompact2x2 = false
    )
}

@Preview(name = "4x2 Empty State Widget", widthDp = 360, heightDp = 150)
@Composable
fun TravelTrackWidgetEmptyPreview() {
    NothingWidgetPreviewCard(
        trips = emptyList(),
        isCompact2x2 = false
    )
}
