package com.example.parser

object LabelValueExtractor {
    /**
     * Finds a value by its label without relying on fixed text coordinates or positions.
     * - Searches for any of [targetLabels] (case-insensitive).
     * - Takes the text immediately following the label on the same line, stopping if any label
     *   from [allKnownLabels] begins later on the same line.
     * - If the text after the label on the same line is empty, takes the text from the next
     *   non-blank line (stopping before any label from [allKnownLabels]).
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
            for (label in targetLabels) {
                val labelRegex = buildLabelStartRegex(label)
                val match = labelRegex.find(line) ?: continue

                val afterLabel = line.substring(match.range.last + 1)
                val sameLineValue = truncateBeforeNextLabel(afterLabel, otherLabels)
                    .trim()
                    .trimStart(':', '-', '*')
                    .trim()

                if (sameLineValue.isNotEmpty()) {
                    return sameLineValue
                }

                // Look at the next non-blank line
                var nextIdx = i + 1
                while (nextIdx < lines.size && lines[nextIdx].isBlank()) {
                    nextIdx++
                }
                if (nextIdx < lines.size) {
                    val nextLine = lines[nextIdx].trim()
                    if (!startsWithAnyLabel(nextLine, otherLabels)) {
                        val nextLineValue = truncateBeforeNextLabel(nextLine, otherLabels)
                            .trim()
                            .trimStart(':', '-', '*')
                            .trim()
                        if (nextLineValue.isNotEmpty()) {
                            return nextLineValue
                        }
                    }
                }
            }
        }
        return null
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
            val regex = Regex("""(?<=\s)($escaped)\s*\*?\s*:?(?=\s|$)""", RegexOption.IGNORE_CASE)
            val match = regex.find(segment)
            if (match != null && match.range.first < earliestIndex) {
                earliestIndex = match.range.first
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
