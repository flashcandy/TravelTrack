package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Nothing OS Monochrome & Single Red Accent Palette
val NothingBlack = Color(0xFF000000)
val NothingDarkSurface = Color(0xFF111111)
val NothingDarkCard = Color(0xFF141414)
val NothingDarkBorder = Color(0xFF282828)
val NothingDarkMuted = Color(0xFF8E8E8E)

val NothingWhite = Color(0xFFFFFFFF)
val NothingLightBackground = Color(0xFFF5F5F5)
val NothingLightCard = Color(0xFFFFFFFF)
val NothingLightBorder = Color(0xFFDCDCDC)
val NothingLightMuted = Color(0xFF5C5C5C)

// Single Red Accent #D71921 for primary actions and alerts
val NothingRed = Color(0xFFD71921)
val NothingRedContainerDark = Color(0xFF2C090B)
val NothingRedContainerLight = Color(0xFFFDE8E9)

// Status Dot Colors (Widget, Chip, List & Badges)
// CNF = green, RAC = amber, any WL variant (WL, RLWL, PQWL, RSWL, TQWL, GNWL) = amber, CANCELLED = red, UNKNOWN or not checked = grey
val StatusConfirmedGreen = Color(0xFF00E676)
val StatusWaitlistAmber = Color(0xFFFFB300)
val StatusCancelledRed = Color(0xFFD71921)
val StatusUnknownGrey = Color(0xFF8E8E8E)

fun mapStatusCategoryToColor(category: com.example.data.PnrStatusCategory): Color {
    return when (category) {
        com.example.data.PnrStatusCategory.CONFIRMED -> StatusConfirmedGreen
        com.example.data.PnrStatusCategory.RAC,
        com.example.data.PnrStatusCategory.WAITLISTED -> StatusWaitlistAmber
        com.example.data.PnrStatusCategory.CANCELLED -> StatusCancelledRed
        com.example.data.PnrStatusCategory.UNKNOWN,
        com.example.data.PnrStatusCategory.CHART_PREPARED -> StatusUnknownGrey
    }
}

fun mapStatusToColor(status: String?): Color {
    val category = com.example.data.Trip.resolveStatusCategory(status)
    return mapStatusCategoryToColor(category)
}
