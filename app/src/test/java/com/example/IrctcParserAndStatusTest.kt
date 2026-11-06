package com.example

import com.example.data.PnrStatusCategory
import com.example.data.Trip
import com.example.parser.IrctcTicketParser
import com.example.parser.Passenger
import com.example.ui.theme.StatusCancelledRed
import com.example.ui.theme.StatusConfirmedGreen
import com.example.ui.theme.StatusUnknownGrey
import com.example.ui.theme.StatusWaitlistAmber
import com.example.ui.theme.mapStatusCategoryToColor
import com.example.ui.theme.mapStatusToColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrctcParserAndStatusTest {

    private val parser = IrctcTicketParser()

    private fun loadSampleErsText(): String {
        // Try ClassLoader resource stream first
        val stream = javaClass.classLoader?.getResourceAsStream("sample_irctc_waitlisted_ers.txt")
            ?: javaClass.getResourceAsStream("/sample_irctc_waitlisted_ers.txt")
        if (stream != null) {
            return stream.bufferedReader().use { it.readText() }
        }
        // Fallback to direct file path in project
        val candidatePaths = listOf(
            "src/test/resources/sample_irctc_waitlisted_ers.txt",
            "app/src/test/resources/sample_irctc_waitlisted_ers.txt"
        )
        for (path in candidatePaths) {
            val file = File(path)
            if (file.exists()) {
                return file.readText()
            }
        }
        error("Could not find sample_irctc_waitlisted_ers.txt in test resources or paths")
    }

    @Test
    fun `parse IRCTC waitlisted ERS text from test resources extracts correct class, train, structured passenger, and status`() {
        val text = loadSampleErsText()
        val draft = parser.parse(text)

        // 1. Class: extract only the class value without PNR or train contamination
        assertEquals("SLEEPER CLASS (SL)", draft.travelClass)

        // 2. Train: extract formatted train number and name
        assertEquals("11020 KONARK EXPRESS", draft.train)

        // 3. PNR: assert last 4 digits only (do not hardcode full PNR in test)
        assertEquals("8777", draft.reference.takeLast(4))

        // 4. Passenger: structured object with name, age, gender, bookingStatus, currentStatus
        assertEquals(1, draft.passengers.size)
        val passenger = draft.passengers[0]
        assertEquals("NOMULA SAI CHAND", passenger.name)
        assertEquals(30, passenger.age)
        assertEquals("M", passenger.gender)
        assertEquals("RLWL/29", passenger.bookingStatus)
        assertEquals("RLWL/28", passenger.currentStatus)

        // 5. Ensure footer text did not leak into passenger names
        assertFalse(
            "Passenger name must never contain footer text",
            draft.passengerNames.contains("on the ticket", ignoreCase = true)
        )
        assertFalse(
            "Passenger name must never contain footer text",
            passenger.name.contains("on the ticket", ignoreCase = true)
        )

        // 6. Status fields: separate bookingStatus and currentStatus tokens
        assertEquals("RLWL/29", draft.bookingStatus)
        assertEquals("RLWL/28", draft.currentStatus)

        // 7. Status colour for RLWL = amber
        assertEquals(StatusWaitlistAmber, mapStatusToColor("RLWL/29"))
        assertEquals(StatusWaitlistAmber, mapStatusToColor("RLWL/28"))
        assertEquals(StatusWaitlistAmber, mapStatusToColor("RLWL"))

        val trip = draft.toTrip()
        assertEquals(PnrStatusCategory.WAITLISTED, trip.statusCategory)
        assertEquals(StatusWaitlistAmber, mapStatusCategoryToColor(trip.statusCategory))
    }

    @Test
    fun `confirmed ticket CNF maps to green`() {
        assertEquals(PnrStatusCategory.CONFIRMED, Trip.resolveStatusCategory("CNF"))
        assertEquals(PnrStatusCategory.CONFIRMED, Trip.resolveStatusCategory("CNF/B2/31"))
        assertEquals(PnrStatusCategory.CONFIRMED, Trip.resolveStatusCategory("Confirmed"))
        assertEquals(StatusConfirmedGreen, mapStatusToColor("CNF"))
        assertEquals(StatusConfirmedGreen, mapStatusToColor("CNF/B2/31"))
        assertEquals(StatusConfirmedGreen, mapStatusToColor("Confirmed"))
    }

    @Test
    fun `RAC ticket maps to amber`() {
        assertEquals(PnrStatusCategory.RAC, Trip.resolveStatusCategory("RAC"))
        assertEquals(PnrStatusCategory.RAC, Trip.resolveStatusCategory("RAC/12"))
        assertEquals(StatusWaitlistAmber, mapStatusToColor("RAC"))
        assertEquals(StatusWaitlistAmber, mapStatusToColor("RAC/12"))
    }

    @Test
    fun `all waitlist variants map to amber`() {
        val variants = listOf(
            "WL", "WL/3",
            "RLWL", "RLWL/29", "RLWL/28",
            "PQWL", "PQWL/5",
            "RSWL", "RSWL/2",
            "TQWL", "TQWL/1",
            "GNWL", "GNWL/10"
        )
        for (variant in variants) {
            assertEquals(
                "Variant $variant must resolve to WAITLISTED",
                PnrStatusCategory.WAITLISTED,
                Trip.resolveStatusCategory(variant)
            )
            assertEquals(
                "Variant $variant must map to amber",
                StatusWaitlistAmber,
                mapStatusToColor(variant)
            )
        }
    }

    @Test
    fun `cancelled ticket maps to red`() {
        assertEquals(PnrStatusCategory.CANCELLED, Trip.resolveStatusCategory("CANCELLED"))
        assertEquals(PnrStatusCategory.CANCELLED, Trip.resolveStatusCategory("CAN"))
        assertEquals(StatusCancelledRed, mapStatusToColor("CANCELLED"))
        assertEquals(StatusCancelledRed, mapStatusToColor("CAN"))
    }

    @Test
    fun `unknown or not checked status maps to grey`() {
        assertEquals(PnrStatusCategory.UNKNOWN, Trip.resolveStatusCategory("Not checked yet"))
        assertEquals(PnrStatusCategory.UNKNOWN, Trip.resolveStatusCategory("UNKNOWN"))
        assertEquals(PnrStatusCategory.UNKNOWN, Trip.resolveStatusCategory(null))
        assertEquals(PnrStatusCategory.UNKNOWN, Trip.resolveStatusCategory(""))
        assertEquals(StatusUnknownGrey, mapStatusToColor("Not checked yet"))
        assertEquals(StatusUnknownGrey, mapStatusToColor("UNKNOWN"))
        assertEquals(StatusUnknownGrey, mapStatusToColor(null))
        assertEquals(StatusUnknownGrey, mapStatusToColor(""))
    }
}
