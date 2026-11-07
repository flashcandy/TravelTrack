package com.example.sms

import com.example.data.PnrStatusCategory
import java.util.Locale

data class PnrParseResult(
    val pnr: String?,
    val matchedPnrLast4: String?,
    val rawReply: String,
    val extractedStatus: String?,
    val category: PnrStatusCategory,
    val isChartPrepared: Boolean,
    val isCancelled: Boolean,
    val isSuccess: Boolean
)

object PnrReplyParser {

    private val FULL_PNR_REGEX = Regex("""\b(\d{10})\b""")

    // Strict status-token regex required by specification:
    // (CNF|RAC|WL|RLWL|PQWL|RSWL|TQWL|GNWL)\/?[A-Z0-9/]*
    val STATUS_TOKEN_REGEX = Regex(
        """(CNF|RAC|WL|RLWL|PQWL|RSWL|TQWL|GNWL)/?[A-Z0-9/]*""",
        RegexOption.IGNORE_CASE
    )

    // Checks sender title or address
    fun isSender139(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.trim().replace(" ", "").uppercase(Locale.US)
        if (clean == "139" || clean == "+91139") return true
        if (clean.endsWith("139") || clean.contains("-139")) return true
        return false
    }

    /**
     * Parses the 139 SMS / Notification reply body.
     * Matches the PNR by 10 digits or by [pendingPnrLast4] of an active pending check.
     */
    fun parse(smsBody: String, pendingPnrLast4: String? = null): PnrParseResult {
        val trimmed = smsBody.trim()
        val fullPnrMatch = FULL_PNR_REGEX.find(trimmed)?.value

        // Check if pending PNR last 4 digits match
        val matchedLast4 = when {
            fullPnrMatch != null -> fullPnrMatch.takeLast(4)
            !pendingPnrLast4.isNullOrBlank() && trimmed.contains(pendingPnrLast4) -> pendingPnrLast4
            else -> null
        }

        val lower = trimmed.lowercase(Locale.US)
        val isCancelled = lower.contains("cancel") || lower.contains("cancelled") || lower.contains("canc")
        val isChartPrepared = lower.contains("chart prepared") && !lower.contains("chart not prepared")

        if (isCancelled) {
            return PnrParseResult(
                pnr = fullPnrMatch,
                matchedPnrLast4 = matchedLast4,
                rawReply = trimmed,
                extractedStatus = "CANCELLED",
                category = PnrStatusCategory.CANCELLED,
                isChartPrepared = isChartPrepared,
                isCancelled = true,
                isSuccess = true
            )
        }

        // Find status tokens in the reply text
        // If multiple tokens appear (e.g. Booking: RLWL/29 Cur: RLWL/28), the last one is Current Status
        val allMatches = STATUS_TOKEN_REGEX.findAll(trimmed).toList()
        var candidateToken: String? = null
        if (allMatches.isNotEmpty()) {
            candidateToken = allMatches.last().value.trim().uppercase(Locale.US)
        }

        if (candidateToken == null && isChartPrepared) {
            return PnrParseResult(
                pnr = fullPnrMatch,
                matchedPnrLast4 = matchedLast4,
                rawReply = trimmed,
                extractedStatus = "CHART PREPARED",
                category = PnrStatusCategory.CHART_PREPARED,
                isChartPrepared = true,
                isCancelled = false,
                isSuccess = true
            )
        }

        if (candidateToken.isNullOrBlank()) {
            return PnrParseResult(
                pnr = fullPnrMatch,
                matchedPnrLast4 = matchedLast4,
                rawReply = trimmed,
                extractedStatus = null,
                category = PnrStatusCategory.UNKNOWN,
                isChartPrepared = isChartPrepared,
                isCancelled = false,
                isSuccess = false
            )
        }

        val category = categorizeToken(candidateToken)
        val isSuccess = category != PnrStatusCategory.UNKNOWN

        return PnrParseResult(
            pnr = fullPnrMatch,
            matchedPnrLast4 = matchedLast4,
            rawReply = trimmed,
            extractedStatus = candidateToken,
            category = category,
            isChartPrepared = isChartPrepared,
            isCancelled = false,
            isSuccess = isSuccess
        )
    }

    fun categorizeToken(token: String): PnrStatusCategory {
        val upper = token.trim().uppercase(Locale.US)
        return when {
            upper.startsWith("CAN") || upper.contains("CANCEL") -> PnrStatusCategory.CANCELLED
            upper.contains("CHART PREPARED") -> PnrStatusCategory.CHART_PREPARED
            upper.startsWith("CNF") || upper.startsWith("CONFIRM") -> PnrStatusCategory.CONFIRMED
            upper.startsWith("RAC") -> PnrStatusCategory.RAC
            upper.startsWith("RLWL") || upper.startsWith("PQWL") ||
                upper.startsWith("RSWL") || upper.startsWith("TQWL") ||
                upper.startsWith("GNWL") || upper.startsWith("WL") ||
                upper.contains("/WL") || upper.contains("WAITLIST") -> PnrStatusCategory.WAITLISTED
            else -> PnrStatusCategory.UNKNOWN
        }
    }
}
