package com.example.parser

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object DateTimeNormalizer {
    private val isoDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val isoTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    private val dateFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MM/yyyy").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd MMM yyyy").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM yyyy").toFormatter(Locale.US)
    )

    private val timeFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("HH:mm").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("H:mm").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("hh:mm a").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("hh:mma").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mma").toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("HH:mm:ss").toFormatter(Locale.US)
    )

    private val dateTokenRegex = Regex(
        """\b(\d{1,2}-[A-Za-z]{3}-\d{4}|\d{1,2}/\d{1,2}/\d{4}|\d{4}-\d{2}-\d{2}|\d{1,2}-\d{2}-\d{4}|\d{1,2}\s+[A-Za-z]{3}\s+\d{4})\b"""
    )

    private val timeTokenRegex = Regex(
        """\b(\d{1,2}:\d{2}(?:\s*[AaPp][Mm])?)(?:\s*[Hh][Rr][Ss]\.?)?\b"""
    )

    /**
     * Normalizes a date string (such as "19-Nov-2026" or "15/01/2026") to ISO "yyyy-MM-dd".
     */
    fun normalizeDate(raw: String): String? {
        if (raw.isBlank()) return null
        val candidate = dateTokenRegex.find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
        for (formatter in dateFormatters) {
            try {
                val parsed = LocalDate.parse(candidate, formatter)
                return parsed.format(isoDateFormatter)
            } catch (_: Exception) {
                // Continue trying next pattern
            }
        }
        return null
    }

    /**
     * Normalizes a time string (such as "11:55 PM", "06:35", "22:40 Hrs") to 24-hour "HH:mm".
     */
    fun normalizeTime(raw: String): String? {
        if (raw.isBlank()) return null
        val stripped = raw
            .replace(Regex("""(?i)\bhrs\.?\b"""), "")
            .replace(Regex("""(?i)\bhours\b"""), "")
            .trim()
        val candidate = timeTokenRegex.find(stripped)?.groupValues?.get(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: stripped

        for (formatter in timeFormatters) {
            try {
                val parsed = LocalTime.parse(candidate.uppercase(Locale.US), formatter)
                return parsed.format(isoTimeFormatter)
            } catch (_: Exception) {
                // Continue trying next pattern
            }
        }
        return null
    }

    /**
     * Extracts both normalized ISO date ("yyyy-MM-dd") and 24-hour time ("HH:mm")
     * from a combined string such as "06:35 19-Nov-2026" or "15/01/2026 22:40 Hrs".
     */
    fun extractDateAndTime(raw: String): Pair<String?, String?> {
        if (raw.isBlank()) return null to null
        val dateMatch = dateTokenRegex.find(raw)?.value
        val remainingForTime = if (dateMatch != null) raw.replaceFirst(dateMatch, " ") else raw
        val normDate = normalizeDate(dateMatch ?: raw)
        val normTime = normalizeTime(remainingForTime)
        return normDate to normTime
    }

    /**
     * Parses a monetary amount string (e.g., "Rs. 201.80", "₹ 1,250", "201.80", "1250")
     * into paise as a Long (e.g., 20180L, 125000L).
     */
    fun parseAmountToPaise(raw: String): Long? {
        if (raw.isBlank()) return null
        val cleaned = raw
            .replace(",", "")
            .let { Regex("""(\d+(?:\.\d{1,2})?)""").find(it)?.groupValues?.get(1) }
            ?: return null
        return try {
            BigDecimal(cleaned)
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
        } catch (_: Exception) {
            null
        }
    }
}
