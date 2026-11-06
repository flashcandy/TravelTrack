package com.example.parser

/**
 * Structured passenger information parsed from the ticket's passenger details table.
 */
data class Passenger(
    val name: String,
    val age: Int? = null,
    val gender: String = "",
    val bookingStatus: String = "",
    val currentStatus: String = ""
) {
    /**
     * Formats passenger for UI display, e.g. "NOMULA SAI CHAND (30, M)"
     */
    fun displayNameWithDetails(): String {
        val details = listOfNotNull(
            age?.toString(),
            gender.takeIf { it.isNotBlank() }
        ).joinToString(", ")
        return if (details.isNotBlank()) "$name ($details)" else name
    }
}
