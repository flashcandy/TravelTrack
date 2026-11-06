package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PnrStatusCategory
import com.example.data.Trip
import com.example.parser.ConfidenceLevel
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusConfirmedGreen
import com.example.ui.theme.StatusWaitlistAmber
import com.example.ui.theme.mapStatusCategoryToColor

/**
 * Flat Nothing OS card: 1 dp border in low-contrast grey, 16 dp corner radius, no shadow, generous padding.
 */
@Composable
fun NothingCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(backgroundColor)
        .border(width = 1.dp, color = borderColor, shape = shape)
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
        .padding(16.dp)

    Column(
        modifier = baseModifier,
        content = content
    )
}

/**
 * Perforated ticket divider line in Nothing OS style.
 */
@Composable
fun PerforatedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        )
    }
}

/**
 * Status dot + monospace pill badge using unified status-to-color mapping:
 * CNF = green, RAC = amber, any WL variant = amber, CANCELLED = red, UNKNOWN or not checked = grey.
 */
@Composable
fun StatusPill(
    status: String,
    modifier: Modifier = Modifier,
    isWaitlisted: Boolean? = null,
    statusCategory: PnrStatusCategory = when {
        isWaitlisted == true -> PnrStatusCategory.WAITLISTED
        else -> Trip.resolveStatusCategory(status)
    }
) {
    val dotColor = mapStatusCategoryToColor(statusCategory)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
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
            text = status.ifBlank { "NOT CHECKED" }.uppercase(),
            fontFamily = DotoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Confidence badge shown above editable fields on the Review screen.
 */
@Composable
fun ConfidenceBadge(
    level: ConfidenceLevel,
    hint: String,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when (level) {
        ConfidenceLevel.HIGH -> StatusConfirmedGreen
        ConfidenceLevel.MEDIUM -> StatusWaitlistAmber
        ConfidenceLevel.LOW -> StatusWaitlistAmber
        ConfidenceLevel.MANUAL -> NothingRed
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Text(
            text = level.name,
            fontFamily = DotoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = indicatorColor
        )
        Text(
            text = "· $hint",
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
