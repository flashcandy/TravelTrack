package com.example.parser

import com.example.data.TripType

/**
 * Fallback parser when a ticket is neither IRCTC nor redBus/TSRTC.
 * Preserves all extracted text so the user can review and fill in fields manually.
 */
class UnknownTicketParser {

    fun parse(text: String): ParsedTicketDraft {
        val (inferredDate, inferredTime) = DateTimeNormalizer.extractDateAndTime(text)
        val inferredType = if (text.contains("bus", ignoreCase = true) ||
            text.contains("seat", ignoreCase = true)
        ) {
            TripType.BUS
        } else {
            TripType.TRAIN
        }

        val confidences = mapOf(
            "reference" to ConfidenceLevel.MANUAL,
            "operator" to ConfidenceLevel.MANUAL,
            "origin" to ConfidenceLevel.MANUAL,
            "destination" to ConfidenceLevel.MANUAL,
            "departure" to if (inferredDate != null && inferredTime != null) {
                ConfidenceLevel.LOW
            } else {
                ConfidenceLevel.MANUAL
            },
            "amount" to ConfidenceLevel.MANUAL,
            "status" to ConfidenceLevel.MANUAL
        )

        val hints = mapOf(
            "reference" to "Manual entry required (unknown ticket format)",
            "operator" to "Manual entry required",
            "origin" to "Manual entry required",
            "destination" to "Manual entry required",
            "departure" to if (inferredDate != null) {
                "Opportunistically detected date/time — please verify"
            } else {
                "Enter ISO date (yyyy-MM-dd) and 24h time (HH:mm)"
            },
            "amount" to "Enter ticket fare in INR",
            "status" to "Enter ticket booking status"
        )

        return ParsedTicketDraft(
            format = TicketFormat.UNKNOWN,
            type = inferredType,
            departureDate = inferredDate.orEmpty(),
            departureTime = inferredTime.orEmpty(),
            status = "Confirmed",
            rawText = text.trim(),
            fieldConfidences = confidences,
            fieldHints = hints
        )
    }
}

class TicketParserRouter(
    private val irctcParser: IrctcTicketParser = IrctcTicketParser(),
    private val redBusParser: RedBusTicketParser = RedBusTicketParser(),
    private val unknownParser: UnknownTicketParser = UnknownTicketParser()
) {
    fun parse(rawText: String): ParsedTicketDraft {
        return when {
            irctcParser.canParse(rawText) -> irctcParser.parse(rawText)
            redBusParser.canParse(rawText) -> redBusParser.parse(rawText)
            else -> unknownParser.parse(rawText)
        }
    }
}
