package com.example.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TripFilter {
    private val isoDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun parseIsoDateTime(isoString: String?): LocalDateTime? {
        if (isoString.isNullOrBlank()) return null
        val trimmed = isoString.trim()
        return runCatching {
            LocalDateTime.parse(trimmed, isoDateTimeFormatter)
        }.recoverCatching {
            LocalDateTime.parse(trimmed)
        }.getOrNull()
    }

    /**
     * Returns trips whose departure is strictly not in the past (>= now)
     * and within the next 5 days (<= now + 5 days), sorted by departure time ascending,
     * limited to at most 3 trips for the Glance home screen widget.
     */
    fun filterUpcomingForWidget(
        trips: List<Trip>,
        now: LocalDateTime = LocalDateTime.now(),
        maxDays: Long = 5L,
        maxCount: Int = 3
    ): List<Trip> {
        val windowEnd = now.plusDays(maxDays)
        return trips.asSequence()
            .mapNotNull { trip ->
                val departure = parseIsoDateTime(trip.departureDateTime) ?: return@mapNotNull null
                trip to departure
            }
            .filter { (_, departure) ->
                !departure.isBefore(now) && !departure.isAfter(windowEnd)
            }
            .sortedBy { (_, departure) -> departure }
            .map { (trip, _) -> trip }
            .take(maxCount)
            .toList()
    }

    /**
     * Splits all trips into (Upcoming, Past) relative to [now].
     * Upcoming trips are sorted soonest-first; Past trips are sorted most-recent-first.
     */
    fun partitionUpcomingAndPast(
        trips: List<Trip>,
        now: LocalDateTime = LocalDateTime.now()
    ): Pair<List<Trip>, List<Trip>> {
        val upcoming = mutableListOf<Pair<Trip, LocalDateTime>>()
        val past = mutableListOf<Pair<Trip, LocalDateTime>>()

        for (trip in trips) {
            val dt = parseIsoDateTime(trip.departureDateTime)
            if (dt == null || !dt.isBefore(now)) {
                upcoming.add(trip to (dt ?: LocalDateTime.MAX))
            } else {
                past.add(trip to dt)
            }
        }

        val sortedUpcoming = upcoming.sortedBy { it.second }.map { it.first }
        val sortedPast = past.sortedByDescending { it.second }.map { it.first }
        return sortedUpcoming to sortedPast
    }
}
