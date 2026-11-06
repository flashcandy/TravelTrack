package com.example.parser

object LabelValueExtractor {

    private val FOOTER_STOP_KEYWORDS = listOf(
        "Important Instructions",
        "Important",
        "Instructions",
        "printed on the ticket",
        "authority to travel",
        "Acronyms",
        "General Information",
        "Agent Details"
    )

    /**
     * Finds a value by its label without relying on fixed text coordinates or positions.
     * - Searches for any of [targetLabels] (case-insensitive).
     * - Takes the text immediately following the label on the same line, stopping if any label
     *   from [allKnownLabels] begins later on the same line.
     * - If the text after the label on the same line is empty, takes the text from the next
     *   non-blank line (stopping before any label from [allKnownLabels]).
     * - Never lets a value run into footer text.
     */
    fun extractValueByLabel(
        text: String,
        targetLabels: List<String>,
        allKnownLabels: List<String>
    ): String? {
        val lines = text.lines()
        val otherLabels = allKnownLabels.filter { known ->
            targetLabels.none { it.equals(known, ignoreCase = true) }
        }

        for (i in lines.indices) {
            val line = lines[i]
            // Skip footer lines from label matching
            if (FOOTER_STOP_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
                // If this is a header like "Important Instructions:", ignore
                if (targetLabels.none { line.contains(it, ignoreCase = true) }) {
                    continue
                }
            }

            for (label in targetLabels) {
                val labelRegex = buildLabelStartRegex(label)
                val match = labelRegex.find(line) ?: continue

                val afterLabel = line.substring(match.range.last + 1)
                val sameLineValue = truncateBeforeNextLabel(afterLabel, otherLabels)
                    .trim()
                    .trimStart(':', '-', '*')
                    .trim()

                if (sameLineValue.isNotEmpty() && !isFooterText(sameLineValue)) {
                    return cleanExtractedValue(sameLineValue)
                }

                // Look at the next non-blank line
                var nextIdx = i + 1
                while (nextIdx < lines.size && lines[nextIdx].isBlank()) {
                    nextIdx++
                }
                if (nextIdx < lines.size) {
                    val nextLine = lines[nextIdx].trim()
                    if (!isFooterText(nextLine) && !startsWithAnyLabel(nextLine, otherLabels)) {
                        val nextLineValue = truncateBeforeNextLabel(nextLine, otherLabels)
                            .trim()
                            .trimStart(':', '-', '*')
                            .trim()
                        if (nextLineValue.isNotEmpty() && !isFooterText(nextLineValue)) {
                            return cleanExtractedValue(nextLineValue)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun cleanExtractedValue(raw: String): String {
        var cleaned = raw.trim()
        for (footer in FOOTER_STOP_KEYWORDS) {
            val idx = cleaned.indexOf(footer, ignoreCase = true)
            if (idx >= 0) {
                cleaned = cleaned.substring(0, idx).trim()
            }
        }
        return cleaned
    }

    private fun isFooterText(text: String): Boolean {
        return FOOTER_STOP_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }

    private fun buildLabelStartRegex(label: String): Regex {
        val escaped = Regex.escape(label.trim())
            .replace("\\ ", "\\s+")
        // Require word boundary or whitespace/start-of-line before label, and optional '*' or ':' after label
        return Regex("""(?:^|(?<=\s))($escaped)\s*\*?\s*:?(?=\s|$)""", RegexOption.IGNORE_CASE)
    }

    private fun truncateBeforeNextLabel(segment: String, knownLabels: List<String>): String {
        var earliestIndex = segment.length
        for (known in knownLabels) {
            val escaped = Regex.escape(known.trim()).replace("\\ ", "\\s+")
            val regex = Regex("""(?<=\s|[:|,|/|-]|^)($escaped)\s*\*?\s*:?(?=\s|:|$)""", RegexOption.IGNORE_CASE)
            val match = regex.find(segment)
            if (match != null && match.range.first < earliestIndex) {
                earliestIndex = match.range.first
            }
        }
        // Also truncate if footer keyword appears on the same line
        for (footer in FOOTER_STOP_KEYWORDS) {
            val idx = segment.indexOf(footer, ignoreCase = true)
            if (idx in 0 until earliestIndex) {
                earliestIndex = idx
            }
        }
        return segment.substring(0, earliestIndex)
    }

    private fun startsWithAnyLabel(line: String, knownLabels: List<String>): Boolean {
        for (known in knownLabels) {
            val escaped = Regex.escape(known.trim()).replace("\\ ", "\\s+")
            val regex = Regex("""^($escaped)\s*\*?\s*:?(?=\s|$)""", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(line)) return true
        }
        return false
    }
}
