package com.example.sms

import com.example.data.PnrStatusCategory
import java.util.Locale

data class PnrParseResult(
    val pnr: String?,
    val rawReply: String,
    val extractedStatus: String?,
    val category: PnrStatusCategory,
    val isChartPrepared: Boolean,
    val isCancelled: Boolean,
    val isSuccess: Boolean
)

object PnrReplyParser {

    private val PNR_REGEX = Regex("""\b(\d{10})\b""")

    // Matches e.g. "CNF/B2/31", "RAC/12", "WL/3", "RLWL/28", "PQWL/5", "TQWL/1", "CNF"
    private val STATUS_TOKEN_REGEX = Regex(
        """(?i)\b(CNF|RAC|RLWL|PQWL|RSWL|TQWL|WL)(?:\s*[/,-]\s*([A-Za-z0-9]+(?:/[A-Za-z0-9]+)*))?"""
    )

    // Matches labels like "Current Status: ...", "Current: ...", "Status: ..."
    private val LABELED_STATUS_REGEX = Regex(
        """(?i)(?:current\s*status|current|status)\s*[:=-]\s*([A-Za-z0-9/_-]+(?:\s+[A-Za-z0-9/_-]+)?)"""
    )

    fun parse(smsBody: String): PnrParseResult {
        val trimmed = smsBody.trim()
        val pnrMatch = PNR_REGEX.find(trimmed)?.value

        val lower = trimmed.lowercase(Locale.US)
        val isCancelled = lower.contains("cancel") || lower.contains("cancelled") || lower.contains("canc")
        val isChartPrepared = lower.contains("chart prepared") && !lower.contains("chart not prepared")

        if (isCancelled) {
            return PnrParseResult(
                pnr = pnrMatch,
                rawReply = trimmed,
                extractedStatus = "CANCELLED",
                category = PnrStatusCategory.CANCELLED,
                isChartPrepared = isChartPrepared,
                isCancelled = true,
                isSuccess = true
            )
        }

        // Try extracting using explicit "Current Status" / "Current" label first
        val labeledMatch = LABELED_STATUS_REGEX.find(trimmed)
        var candidateToken: String? = null

        if (labeledMatch != null) {
            val afterLabel = labeledMatch.groupValues[1].trim()
            val tokenInLabel = STATUS_TOKEN_REGEX.find(afterLabel)
            if (tokenInLabel != null) {
                candidateToken = cleanToken(tokenInLabel.value)
            } else if (afterLabel.isNotBlank() && !afterLabel.equals("N/A", ignoreCase = true)) {
                candidateToken = afterLabel
            }
        }

        // If no token from label, search the entire body for token patterns (take the last one, as Current status usually follows Booking status)
        if (candidateToken == null) {
            val allTokens = STATUS_TOKEN_REGEX.findAll(trimmed).toList()
            if (allTokens.isNotEmpty()) {
                candidateToken = cleanToken(allTokens.last().value)
            }
        }

        if (candidateToken == null && isChartPrepared) {
            return PnrParseResult(
                pnr = pnrMatch,
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
                pnr = pnrMatch,
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
            pnr = pnrMatch,
            rawReply = trimmed,
            extractedStatus = candidateToken,
            category = category,
            isChartPrepared = isChartPrepared,
            isCancelled = false,
            isSuccess = isSuccess
        )
    }

    private fun cleanToken(raw: String): String {
        return raw.trim()
            .replace(Regex("""\s*[/,-]\s*"""), "/")
            .uppercase(Locale.US)
    }

    fun categorizeToken(token: String): PnrStatusCategory {
        val upper = token.trim().uppercase(Locale.US)
        return when {
            upper.startsWith("CAN") || upper.contains("CANCEL") -> PnrStatusCategory.CANCELLED
            upper.startsWith("CNF") || upper.startsWith("CONFIRM") -> PnrStatusCategory.CONFIRMED
            upper.startsWith("RAC") -> PnrStatusCategory.RAC
            upper.startsWith("RLWL") || upper.startsWith("PQWL") ||
                upper.startsWith("RSWL") || upper.startsWith("TQWL") ||
                upper.startsWith("WL") || upper.contains("/WL") ||
                upper.contains("WAITLIST") -> PnrStatusCategory.WAITLISTED
            upper.contains("CHART PREPARED") -> PnrStatusCategory.CHART_PREPARED
            else -> PnrStatusCategory.UNKNOWN
        }
    }
}
