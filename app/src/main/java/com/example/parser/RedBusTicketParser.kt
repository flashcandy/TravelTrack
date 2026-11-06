package com.example.parser

import com.example.data.TripType

/**
 * Parser for redBus / TSRTC bus tickets.
 * Detects by "redBus", "Onward Journey Ticket Details", "TELANGANA STATE ROAD TRANSPORT CORPORATION",
 * or bus-specific labels ("From City", "To City", "Ticket No").
 */
class RedBusTicketParser {

    companion object {
        private val KNOWN_LABELS = listOf(
            "From City",
            "To City",
            "Date of Journey",
            "Depart On",
            "Start Time at Origin",
            "Ticket No",
            "Ticket Number",
            "Status",
            "Total Price",
            "Total Fare",
            "Seats",
            "Seat No",
            "Passengers",
            "Passenger Name",
            "Pickup Point",
            "Boarding Point",
            "Drop Point",
            "Dropping Point",
            "Operator",
            "Bus Operator"
        )
    }

    fun canParse(text: String): Boolean {
        if (text.isBlank()) return false
        val hasBusHeader = text.contains("redBus", ignoreCase = true) ||
            text.contains("Onward Journey Ticket Details", ignoreCase = true) ||
            text.contains("TELANGANA STATE ROAD TRANSPORT CORPORATION", ignoreCase = true) ||
            text.contains("TSRTC", ignoreCase = true)
        val hasBusLabels = text.contains("From City", ignoreCase = true) &&
            text.contains("To City", ignoreCase = true) &&
            text.contains("Ticket No", ignoreCase = true)
        return hasBusHeader || hasBusLabels
    }

    fun parse(text: String): ParsedTicketDraft {
        val confidences = mutableMapOf<String, ConfidenceLevel>()
        val hints = mutableMapOf<String, String>()

        val ticketNo = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Ticket No", "Ticket Number"),
            allKnownLabels = KNOWN_LABELS
        )?.takeWhile { !it.isWhitespace() }.orEmpty()

        if (ticketNo.isNotBlank()) {
            confidences["reference"] = ConfidenceLevel.HIGH
            hints["reference"] = "Matched label 'Ticket No'"
        } else {
            confidences["reference"] = ConfidenceLevel.MANUAL
            hints["reference"] = "Ticket No label not found"
        }

        val labeledOperator = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Operator", "Bus Operator"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val operator = when {
            labeledOperator.isNotBlank() -> labeledOperator
            text.contains("TELANGANA STATE ROAD TRANSPORT CORPORATION", ignoreCase = true) ->
                "TELANGANA STATE ROAD TRANSPORT CORPORATION"
            ticketNo.startsWith("TS", ignoreCase = true) || ticketNo.contains("#TG", ignoreCase = true) ->
                "TELANGANA STATE ROAD TRANSPORT CORPORATION"
            text.contains("redBus", ignoreCase = true) -> "redBus"
            else -> "TSRTC / redBus"
        }
        confidences["operator"] = if (labeledOperator.isNotBlank() ||
            text.contains("TELANGANA STATE ROAD TRANSPORT CORPORATION", ignoreCase = true)
        ) {
            ConfidenceLevel.HIGH
        } else {
            ConfidenceLevel.MEDIUM
        }
        hints["operator"] = "Detected bus operator"

        val fromCity = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("From City"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        if (fromCity.isNotBlank()) {
            confidences["origin"] = ConfidenceLevel.HIGH
            hints["origin"] = "Matched label 'From City'"
        } else {
            confidences["origin"] = ConfidenceLevel.MANUAL
            hints["origin"] = "From City not found"
        }

        val toCity = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("To City"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        if (toCity.isNotBlank()) {
            confidences["destination"] = ConfidenceLevel.HIGH
            hints["destination"] = "Matched label 'To City'"
        } else {
            confidences["destination"] = ConfidenceLevel.MANUAL
            hints["destination"] = "To City not found"
        }

        val pickupPoint = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Pickup Point", "Boarding Point"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val dropPoint = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Drop Point", "Dropping Point"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val dateOfJourneyRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Date of Journey"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val normalizedDoj = DateTimeNormalizer.normalizeDate(dateOfJourneyRaw)

        val departOnRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Depart On"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val normalizedDepartTime = DateTimeNormalizer.normalizeTime(departOnRaw)

        val originStartRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Start Time at Origin"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val (originStartDate, originStartTime) = DateTimeNormalizer.extractDateAndTime(originStartRaw)
        val normalizedOriginStart = if (originStartDate != null && originStartTime != null) {
            "${originStartDate}T${originStartTime}"
        } else {
            originStartRaw
        }

        val finalDepDate = normalizedDoj ?: originStartDate.orEmpty()
        val finalDepTime = normalizedDepartTime ?: originStartTime.orEmpty()

        if (finalDepDate.isNotBlank() && finalDepTime.isNotBlank()) {
            confidences["departure"] = ConfidenceLevel.HIGH
            hints["departure"] = "Matched 'Date of Journey' & 'Depart On'"
        } else {
            confidences["departure"] = ConfidenceLevel.LOW
            hints["departure"] = "Verify journey date and departure time"
        }

        val status = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Status"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        if (status.isNotBlank()) {
            confidences["status"] = ConfidenceLevel.HIGH
            hints["status"] = "Matched label 'Status'"
        } else {
            confidences["status"] = ConfidenceLevel.LOW
            hints["status"] = "Status label not found"
        }

        val totalPriceRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Total Price", "Total Fare"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val amountPaise = DateTimeNormalizer.parseAmountToPaise(totalPriceRaw) ?: 0L
        if (amountPaise > 0L) {
            confidences["amount"] = ConfidenceLevel.HIGH
            hints["amount"] = "Matched label 'Total Price'"
        } else {
            confidences["amount"] = ConfidenceLevel.LOW
            hints["amount"] = "Verify total price"
        }

        val seats = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Seats", "Seat No"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        if (seats.isNotBlank()) {
            confidences["seats"] = ConfidenceLevel.HIGH
            hints["seats"] = "Matched label 'Seats'"
        }

        val passengers = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Passengers", "Passenger Name"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        if (passengers.isNotBlank()) {
            confidences["passengers"] = ConfidenceLevel.HIGH
            hints["passengers"] = "Matched label 'Passengers'"
        }

        val originCode = deriveCityCode(fromCity)
        val destinationCode = deriveCityCode(toCity)

        return ParsedTicketDraft(
            format = TicketFormat.REDBUS_TSRTC,
            type = TripType.BUS,
            operator = operator,
            originName = fromCity,
            originCode = originCode,
            destinationName = toCity,
            destinationCode = destinationCode,
            pickupPoint = pickupPoint,
            dropPoint = dropPoint,
            departureDate = finalDepDate,
            departureTime = finalDepTime,
            originStartTime = normalizedOriginStart,
            amountPaise = amountPaise,
            currency = "INR",
            reference = ticketNo,
            status = status,
            passengerNames = passengers,
            seatNumbers = seats,
            rawText = text,
            fieldConfidences = confidences,
            fieldHints = hints
        )
    }

    private fun deriveCityCode(city: String): String {
        val cleaned = city.trim()
            .replace(Regex("\\s+BS$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleaned.isEmpty()) return ""
        return if (cleaned.length <= 4) {
            cleaned.uppercase()
        } else {
            cleaned.take(3).uppercase()
        }
    }
}
