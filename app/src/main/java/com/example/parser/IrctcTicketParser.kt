package com.example.parser

import com.example.data.TripType

/**
 * Parser for IRCTC train tickets (Electronic Reservation Slip).
 * Detects by "Electronic Reservation Slip", "IRCTC", or IRCTC-specific label combinations.
 */
class IrctcTicketParser {

    companion object {
        private val KNOWN_LABELS = listOf(
            "Booked from",
            "Boarding At",
            "From",
            "To",
            "Departure*",
            "Departure",
            "Arrival*",
            "Arrival",
            "PNR",
            "Train No./Name",
            "Train No/Name",
            "Quota",
            "Class",
            "Booking Status",
            "Current Status",
            "Total Fare (all inclusive)",
            "Total Fare",
            "Transaction ID",
            "Passenger Name",
            "Passengers"
        )

        private val STATION_WITH_CODE_REGEX = Regex("""^(.*?)\s*\(([A-Za-z0-9]{1,6})\)\s*$""")
    }

    fun canParse(text: String): Boolean {
        if (text.isBlank()) return false
        val hasHeader = text.contains("Electronic Reservation Slip", ignoreCase = true) ||
            text.contains("IRCTC", ignoreCase = true)
        val hasIrctcLabels = text.contains("PNR", ignoreCase = true) &&
            (text.contains("Train No./Name", ignoreCase = true) ||
                text.contains("Booked from", ignoreCase = true))
        return hasHeader || hasIrctcLabels
    }

    fun parse(text: String): ParsedTicketDraft {
        val confidences = mutableMapOf<String, ConfidenceLevel>()
        val hints = mutableMapOf<String, String>()

        val pnr = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("PNR"),
            allKnownLabels = KNOWN_LABELS
        )?.takeWhile { !it.isWhitespace() }.orEmpty()

        if (pnr.isNotBlank()) {
            confidences["reference"] = ConfidenceLevel.HIGH
            hints["reference"] = "Matched label 'PNR'"
        } else {
            confidences["reference"] = ConfidenceLevel.MANUAL
            hints["reference"] = "PNR label not found"
        }

        val trainNoAndName = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Train No./Name", "Train No/Name"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val (trainNo, trainName) = parseTrainNumberAndName(trainNoAndName)
        if (trainNoAndName.isNotBlank()) {
            confidences["operator"] = ConfidenceLevel.HIGH
            hints["operator"] = "Matched label 'Train No./Name'"
        } else {
            confidences["operator"] = ConfidenceLevel.MANUAL
            hints["operator"] = "Train No./Name not found"
        }

        val travelClass = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Class"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        if (travelClass.isNotBlank()) {
            confidences["travelClass"] = ConfidenceLevel.HIGH
            hints["travelClass"] = "Matched label 'Class'"
        }

        val originRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Booked from", "Boarding At"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val (originName, originCode) = parseStationNameAndCode(originRaw)
        if (originName.isNotBlank()) {
            confidences["origin"] = ConfidenceLevel.HIGH
            hints["origin"] = "Matched label 'Booked from'"
        } else {
            confidences["origin"] = ConfidenceLevel.MANUAL
            hints["origin"] = "Origin station not found"
        }

        val destinationRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("To"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val (destinationName, destinationCode) = parseStationNameAndCode(destinationRaw)
        if (destinationName.isNotBlank()) {
            confidences["destination"] = ConfidenceLevel.HIGH
            hints["destination"] = "Matched label 'To'"
        } else {
            confidences["destination"] = ConfidenceLevel.MANUAL
            hints["destination"] = "Destination station not found"
        }

        val departureRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Departure*", "Departure"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val (depDate, depTime) = DateTimeNormalizer.extractDateAndTime(departureRaw)
        if (depDate != null && depTime != null) {
            confidences["departure"] = ConfidenceLevel.HIGH
            hints["departure"] = "Matched label 'Departure*' ($departureRaw)"
        } else {
            confidences["departure"] = ConfidenceLevel.LOW
            hints["departure"] = "Verify departure date & time"
        }

        val arrivalRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Arrival*", "Arrival"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val (arrDate, arrTime) = DateTimeNormalizer.extractDateAndTime(arrivalRaw)
        if (arrDate != null && arrTime != null) {
            confidences["arrival"] = ConfidenceLevel.HIGH
            hints["arrival"] = "Matched label 'Arrival*' ($arrivalRaw)"
        }

        val bookingStatus = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Booking Status"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val currentStatus = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Current Status"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val effectiveStatus = currentStatus.ifBlank { bookingStatus }
        if (effectiveStatus.isNotBlank()) {
            confidences["status"] = ConfidenceLevel.HIGH
            hints["status"] = if (currentStatus.isNotBlank()) {
                "Matched 'Current Status' (Booking: ${bookingStatus.ifBlank { "N/A" }})"
            } else {
                "Matched 'Booking Status'"
            }
        } else {
            confidences["status"] = ConfidenceLevel.LOW
            hints["status"] = "Status label not found"
        }

        val fareRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Total Fare (all inclusive)", "Total Fare"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val amountPaise = DateTimeNormalizer.parseAmountToPaise(fareRaw) ?: 0L
        if (amountPaise > 0L) {
            confidences["amount"] = ConfidenceLevel.HIGH
            hints["amount"] = "Matched label 'Total Fare (all inclusive)'"
        } else {
            confidences["amount"] = ConfidenceLevel.LOW
            hints["amount"] = "Verify total fare"
        }

        val transactionId = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Transaction ID"),
            allKnownLabels = KNOWN_LABELS
        )?.takeWhile { !it.isWhitespace() }.orEmpty()

        val passengerRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Passenger Name", "Passengers"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val operatorDisplay = when {
            trainNo.isNotBlank() && trainName.isNotBlank() -> "$trainNo/$trainName"
            trainNoAndName.isNotBlank() -> trainNoAndName
            else -> "IRCTC"
        }

        return ParsedTicketDraft(
            format = TicketFormat.IRCTC_TRAIN,
            type = TripType.TRAIN,
            operator = operatorDisplay,
            trainNumber = trainNo,
            trainName = trainName,
            travelClass = travelClass,
            originName = originName,
            originCode = originCode,
            destinationName = destinationName,
            destinationCode = destinationCode,
            departureDate = depDate.orEmpty(),
            departureTime = depTime.orEmpty(),
            arrivalDate = arrDate.orEmpty(),
            arrivalTime = arrTime.orEmpty(),
            amountPaise = amountPaise,
            currency = "INR",
            reference = pnr,
            bookingStatus = bookingStatus,
            currentStatus = currentStatus,
            status = effectiveStatus,
            passengerNames = passengerRaw,
            transactionId = transactionId,
            rawText = text,
            fieldConfidences = confidences,
            fieldHints = hints
        )
    }

    internal fun parseStationNameAndCode(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "" to ""
        val match = STATION_WITH_CODE_REGEX.find(trimmed)
        return if (match != null) {
            val station = match.groupValues[1].trim()
            val code = match.groupValues[2].trim()
            station to code
        } else {
            trimmed to ""
        }
    }

    internal fun parseTrainNumberAndName(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "" to ""
        return if (trimmed.contains("/")) {
            val number = trimmed.substringBefore("/").trim()
            val name = trimmed.substringAfter("/").trim()
            number to name
        } else {
            "" to trimmed
        }
    }
}
