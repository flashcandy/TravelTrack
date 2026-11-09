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

    // Explicit "Current Status:" label regex used by Telegram @check_pnr_status_bot and structured replies
    private val CURRENT_STATUS_LABEL_REGEX = Regex(
        """Current\s*Status\s*[:\-]\s*([A-Za-z0-9/\s]+?)(?:\r|\n|$|⏱|✅|❌|\uD83D|\u23F1)""",
        RegexOption.IGNORE_CASE
    )

    // Known Telegram package names
    private val TELEGRAM_PACKAGES = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "tw.nekomimi.nekogram",
        "ir.ilmili.telegraph",
        "com.radolyn.ayugram",
        "uz.unnarsx.cherrygram",
        "org.thunderdog.challegram"
    )

    // Checks sender title or address for 139 SMS
    fun isSender139(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.trim().replace(" ", "").uppercase(Locale.US)
        if (clean == "139" || clean == "+91139") return true
        if (clean.endsWith("139") || clean.contains("-139")) return true
        return false
    }

    /**
     * Checks if a notification is from Telegram's @check_pnr_status_bot or contains structured Telegram PNR bot markers.
     */
    fun isTelegramPnrBot(packageName: String?, senderTitle: String?, bodyText: String?): Boolean {
        val lowerTitle = senderTitle?.trim()?.lowercase(Locale.US).orEmpty()
        val lowerBody = bodyText?.trim()?.lowercase(Locale.US).orEmpty()

        val titleMatchesBot = lowerTitle.contains("check_pnr_status_bot") ||
            lowerTitle.contains("pnr status") ||
            lowerTitle.contains("check pnr") ||
            lowerTitle.contains("irctc pnr")

        val bodyMatchesBotFormat = (lowerBody.contains("pnr:") || lowerBody.contains("🎟")) &&
            (lowerBody.contains("booking status:") || lowerBody.contains("current status:") || lowerBody.contains("chartstatus:"))

        if (titleMatchesBot) return true
        if (packageName != null && TELEGRAM_PACKAGES.contains(packageName) && bodyMatchesBotFormat) {
            return true
        }
        return bodyMatchesBotFormat
    }

    /**
     * Parses a 139 SMS or @check_pnr_status_bot Telegram notification reply body.
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
        val isCancelled = lower.contains("ticket cancelled") ||
            lower.contains("has been cancelled") ||
            lower.contains("train cancelled") ||
            Regex("""\bcancelled\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        val isChartPrepared = (lower.contains("chart prepared") || lower.contains("chartstatus: chart prepared")) &&
            !lower.contains("chart not prepared")

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

        // 1. Check if there are explicit "Current Status: <token>" lines (e.g. from @check_pnr_status_bot)
        val explicitCurrentMatches = CURRENT_STATUS_LABEL_REGEX.findAll(trimmed).mapNotNull { match ->
            val rawVal = match.groupValues.getOrNull(1)?.trim().orEmpty()
            STATUS_TOKEN_REGEX.find(rawVal)?.value?.trim()?.uppercase(Locale.US)
        }.toList()

        // 2. Fallback to all status tokens in the reply text
        val allMatches = STATUS_TOKEN_REGEX.findAll(trimmed).toList()
        val candidateToken: String? = when {
            explicitCurrentMatches.isNotEmpty() -> explicitCurrentMatches.first()
            allMatches.isNotEmpty() -> allMatches.last().value.trim().uppercase(Locale.US)
            else -> null
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
