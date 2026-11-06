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
            "Train No.",
            "Train Name",
            "Quota",
            "Class",
            "Booking Date",
            "Passenger Details",
            "Passenger Name",
            "Passengers",
            "Booking Status",
            "Current Status",
            "Total Fare (all inclusive)",
            "Total Fare",
            "Transaction ID",
            "Acronyms",
            "Important Instructions"
        )

        val STATUS_TOKEN_REGEX = Regex(
            """(CNF|RAC|WL|RLWL|PQWL|RSWL|TQWL|GNWL)/?[A-Z0-9/]*""",
            RegexOption.IGNORE_CASE
        )

        private val STATION_WITH_CODE_REGEX = Regex("""^(.*?)\s*\(([A-Za-z0-9]{1,6})\)\s*$""")

        // Common IRCTC Travel Classes
        private val CLASS_PATTERN = Regex(
            """(SLEEPER\s+CLASS(?:\s*\([A-Z0-9]+\))?|AC\s+[123]\s+TIER(?:\s*\([A-Z0-9]+\))?|AC\s+CHAIR\s+CAR(?:\s*\([A-Z0-9]+\))?|SECOND\s+SITTING(?:\s*\([A-Z0-9]+\))?|THIRD\s+AC\s+ECONOMY(?:\s*\([A-Z0-9]+\))?|FIRST\s+CLASS(?:\s*\([A-Z0-9]+\))?|EXECUTIVE(?:\s+CHAIR\s+CAR)?(?:\s*\([A-Z0-9]+\))?|\b(?:SL|3A|2A|1A|CC|EC|2S|3E|FC)\b(?:\s*\([A-Z0-9]+\))?)""",
            RegexOption.IGNORE_CASE
        )

        private val PASSENGER_ROW_REGEX = Regex(
            """^\s*(?:\d+[\.\s]+)?([A-Za-z\s\.]+?)\s+(\d{1,3})\s+([MF]|MALE|FEMALE|T|TRANSGENDER)\s+([A-Za-z0-9/]+)(?:\s+([A-Za-z0-9/]+))?\s*$""",
            RegexOption.IGNORE_CASE
        )
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

        // 1. PNR (Only digits/alphanumeric code, stopped at whitespace or next label)
        val pnrRaw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("PNR"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()
        val pnr = pnrRaw.split(Regex("\\s+")).firstOrNull()?.trimStart(':')?.trim().orEmpty()

        if (pnr.isNotBlank()) {
            confidences["reference"] = ConfidenceLevel.HIGH
            hints["reference"] = "Matched label 'PNR'"
        } else {
            confidences["reference"] = ConfidenceLevel.MANUAL
            hints["reference"] = "PNR label not found"
        }

        // 2. Train Number and Name
        val trainNoAndName = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Train No./Name", "Train No/Name", "Train No."),
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

        // 3. Class (Extract ONLY the class value, e.g. "SLEEPER CLASS (SL)", avoiding PNR/train contamination)
        val travelClass = extractClassValue(text)
        if (travelClass.isNotBlank()) {
            confidences["travelClass"] = ConfidenceLevel.HIGH
            hints["travelClass"] = "Matched class '$travelClass'"
        }

        // 4. Origin & Destination
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

        // 5. Departure & Arrival Times
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

        // 6. Passenger Details table parsing (bounded by table start and stopping at Acronyms/Transaction ID)
        val parsedPassengers = parsePassengerTable(text)

        // 7. Booking & Current Status (Cleaned with status-token regex)
        var bookingStatus = ""
        var currentStatus = ""

        if (parsedPassengers.isNotEmpty()) {
            val firstPassenger = parsedPassengers[0]
            bookingStatus = firstPassenger.bookingStatus
            currentStatus = firstPassenger.currentStatus
        }

        // Fallback to label extraction if passenger table row didn't include status
        if (bookingStatus.isBlank()) {
            val rawBooking = LabelValueExtractor.extractValueByLabel(
                text = text,
                targetLabels = listOf("Booking Status"),
                allKnownLabels = KNOWN_LABELS
            ).orEmpty()
            bookingStatus = extractStatusToken(rawBooking)
        }

        if (currentStatus.isBlank()) {
            val rawCurrent = LabelValueExtractor.extractValueByLabel(
                text = text,
                targetLabels = listOf("Current Status"),
                allKnownLabels = KNOWN_LABELS
            ).orEmpty()
            currentStatus = extractStatusToken(rawCurrent)
        }

        val effectiveStatus = currentStatus.ifBlank { bookingStatus }
        if (effectiveStatus.isNotBlank()) {
            confidences["status"] = ConfidenceLevel.HIGH
            hints["status"] = "Booking: ${bookingStatus.ifBlank { "N/A" }}, Current: ${currentStatus.ifBlank { "N/A" }}"
        } else {
            confidences["status"] = ConfidenceLevel.LOW
            hints["status"] = "Status label not found"
        }

        // 8. Total Fare
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

        // 9. Transaction ID
        val transactionId = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Transaction ID"),
            allKnownLabels = KNOWN_LABELS
        )?.takeWhile { !it.isWhitespace() }.orEmpty()

        // 10. Passenger Display Names (Never contains "on the ticket")
        val passengerNamesDisplay = if (parsedPassengers.isNotEmpty()) {
            parsedPassengers.joinToString(", ") { it.displayNameWithDetails() }
        } else {
            val passengerRaw = LabelValueExtractor.extractValueByLabel(
                text = text,
                targetLabels = listOf("Passenger Name", "Passengers"),
                allKnownLabels = KNOWN_LABELS
            ).orEmpty()
            if (passengerRaw.contains("on the ticket", ignoreCase = true)) "" else passengerRaw
        }

        val operatorDisplay = when {
            trainNo.isNotBlank() && trainName.isNotBlank() -> "$trainNo/$trainName"
            trainNoAndName.isNotBlank() -> trainNoAndName
            else -> "IRCTC Indian Railways"
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
            passengers = parsedPassengers,
            passengerNames = passengerNamesDisplay,
            transactionId = transactionId,
            rawText = text,
            fieldConfidences = confidences,
            fieldHints = hints
        )
    }

    internal fun extractClassValue(text: String): String {
        // First try finding an explicit Class pattern directly on lines with "Class"
        val lines = text.lines()
        for (line in lines) {
            val classIdx = line.indexOf("Class", ignoreCase = true)
            if (classIdx >= 0) {
                val after = line.substring(classIdx + 5).trimStart(':', '-', '*', ' ').trim()
                val match = CLASS_PATTERN.find(after)
                if (match != null) {
                    return match.value.trim()
                }
            }
        }

        // Try general label extraction
        val raw = LabelValueExtractor.extractValueByLabel(
            text = text,
            targetLabels = listOf("Class"),
            allKnownLabels = KNOWN_LABELS
        ).orEmpty()

        val match = CLASS_PATTERN.find(raw)
        if (match != null) {
            return match.value.trim()
        }

        // If the raw extraction contains known class tokens, strip out PNR or train numbers
        val trimmed = raw.trim()
        return if (!trimmed.contains("PNR", ignoreCase = true) && !trimmed.contains("Train", ignoreCase = true)) {
            trimmed
        } else {
            ""
        }
    }

    internal fun parsePassengerTable(text: String): List<Passenger> {
        val passengers = mutableListOf<Passenger>()
        val lines = text.lines()
        var inPassengerSection = false

        val stopKeywords = listOf(
            "Acronyms",
            "Transaction ID",
            "Total Fare",
            "Important Instructions",
            "Important",
            "Payment Details",
            "Agent Details",
            "Service Charge",
            "Please Note"
        )

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            if (!inPassengerSection) {
                if (trimmed.contains("Passenger Details", ignoreCase = true) ||
                    (trimmed.contains("Name", ignoreCase = true) &&
                        trimmed.contains("Age", ignoreCase = true) &&
                        trimmed.contains("Status", ignoreCase = true))
                ) {
                    inPassengerSection = true
                }
                continue
            }

            // Stop immediately when table section terminates
            if (stopKeywords.any { trimmed.contains(it, ignoreCase = true) }) {
                break
            }

            // Skip table headers and divider rules
            if (trimmed.all { it == '-' || it == '=' || it == '_' || it == '*' || it.isWhitespace() } ||
                trimmed.contains("Sl. No", ignoreCase = true) ||
                trimmed.contains("Passenger Name", ignoreCase = true) ||
                (trimmed.contains("Booking Status", ignoreCase = true) && trimmed.contains("Current Status", ignoreCase = true))
            ) {
                continue
            }

            // Reject any footer text that leaked in
            if (trimmed.contains("on the ticket", ignoreCase = true) ||
                trimmed.contains("authority to travel", ignoreCase = true) ||
                trimmed.contains("identity card", ignoreCase = true)
            ) {
                continue
            }

            val passenger = parsePassengerRow(trimmed)
            if (passenger != null) {
                passengers.add(passenger)
            }
        }
        return passengers
    }

    internal fun parsePassengerRow(line: String): Passenger? {
        val match = PASSENGER_ROW_REGEX.find(line)
        if (match != null) {
            val rawName = match.groupValues[1].trim().removePrefix(".").trim()
            if (rawName.contains("on the ticket", ignoreCase = true) || rawName.contains("Passenger", ignoreCase = true)) {
                return null
            }
            val age = match.groupValues[2].toIntOrNull()
            val gender = match.groupValues[3].uppercase()
            val bookingToken = extractStatusToken(match.groupValues[4])
            val currentToken = if (match.groupValues.size > 5 && match.groupValues[5].isNotBlank()) {
                extractStatusToken(match.groupValues[5])
            } else {
                bookingToken
            }

            return Passenger(
                name = rawName,
                age = age,
                gender = gender,
                bookingStatus = bookingToken,
                currentStatus = currentToken
            )
        }

        // Token-based fallback for varying spacing
        val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size >= 4) {
            // Find age (a 1-3 digit integer)
            val ageIdx = tokens.indexOfFirst { it.matches(Regex("""\d{1,3}""")) && it != tokens[0] }
            if (ageIdx > 0 && ageIdx < tokens.size - 1) {
                val nameTokens = tokens.subList(if (tokens[0].matches(Regex("""\d+\.?"""))) 1 else 0, ageIdx)
                val rawName = nameTokens.joinToString(" ").removePrefix(".").trim()
                if (rawName.isNotBlank() && !rawName.contains("on the ticket", ignoreCase = true)) {
                    val age = tokens[ageIdx].toIntOrNull()
                    val gender = tokens.getOrNull(ageIdx + 1)?.uppercase().orEmpty()
                    val status1 = tokens.getOrNull(ageIdx + 2)?.let { extractStatusToken(it) }.orEmpty()
                    val status2 = tokens.getOrNull(ageIdx + 3)?.let { extractStatusToken(it) }.orEmpty()

                    return Passenger(
                        name = rawName,
                        age = age,
                        gender = gender,
                        bookingStatus = status1,
                        currentStatus = if (status2.isNotBlank()) status2 else status1
                    )
                }
            }
        }
        return null
    }

    internal fun extractStatusToken(raw: String): String {
        val match = STATUS_TOKEN_REGEX.find(raw.trim())
        return match?.value?.trim()?.uppercase().orEmpty()
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
            // Check if leading token is digits
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2 && parts[0].all { it.isDigit() }) {
                parts[0] to parts[1]
            } else {
                "" to trimmed
            }
        }
    }
}
