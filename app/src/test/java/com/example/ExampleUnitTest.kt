package com.example

import com.example.data.TripFilter
import com.example.data.TripType
import com.example.parser.DateTimeNormalizer
import com.example.parser.IrctcTicketParser
import com.example.parser.RedBusTicketParser
import com.example.parser.SampleTickets
import com.example.parser.TicketFormat
import com.example.parser.TicketParserRouter
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val irctcParser = IrctcTicketParser()
    private val redBusParser = RedBusTicketParser()
    private val router = TicketParserRouter(irctcParser, redBusParser)

    @Test
    fun `parse sample IRCTC ERS train ticket extracts all fields and normalizes ISO date and 24h time`() {
        val draft = router.parse(SampleTickets.SAMPLE_IRCTC_ERS_TEXT)

        assertEquals(TicketFormat.IRCTC_TRAIN, draft.format)
        assertEquals(TripType.TRAIN, draft.type)
        assertEquals("6952208777", draft.reference)
        assertEquals("11020", draft.trainNumber)
        assertEquals("KONARK EXPRESS", draft.trainName)
        assertEquals("11020/KONARK EXPRESS", draft.operator)
        assertEquals("SLEEPER CLASS (SL)", draft.travelClass)
        assertEquals("KHAMMAM", draft.originName)
        assertEquals("KMT", draft.originCode)
        assertEquals("SECUNDERABAD JN.", draft.destinationName)
        assertEquals("SC", draft.destinationCode)
        assertEquals("Khammam → Secunderabad", draft.buildRouteTitle())
        assertEquals("2026-11-19", draft.departureDate)
        assertEquals("06:35", draft.departureTime)
        assertEquals("2026-11-19T06:35", draft.departureDateTimeIso)
        assertEquals("2026-11-19", draft.arrivalDate)
        assertEquals("10:50", draft.arrivalTime)
        assertEquals("2026-11-19T10:50", draft.arrivalDateTimeIso)
        assertEquals("RLWL/29", draft.bookingStatus)
        assertEquals("RLWL/28", draft.currentStatus)
        assertEquals("RLWL/28", draft.status)
        assertEquals(20180L, draft.amountPaise)

        val trip = draft.toTrip()
        assertEquals(201.80, trip.amountInRupees, 0.001)
        assertTrue(trip.isWaitlisted())
        assertEquals("8777", trip.maskedReferenceLast4())
    }

    @Test
    fun `parse sample redBus TSRTC bus ticket extracts all fields and normalizes 12h time to 24h`() {
        val draft = router.parse(SampleTickets.SAMPLE_REDBUS_TSRTC_TEXT)

        assertEquals(TicketFormat.REDBUS_TSRTC, draft.format)
        assertEquals(TripType.BUS, draft.type)
        assertEquals("TS260115130655852823RERD#TG26011518365612WD2H##", draft.reference)
        assertEquals("TELANGANA STATE ROAD TRANSPORT CORPORATION", draft.operator)
        assertEquals("KHAMMAM BS", draft.originName)
        assertEquals("KPHB", draft.destinationName)
        assertEquals("Khammam → KPHB", draft.buildRouteTitle())
        assertEquals("2026-01-15", draft.departureDate)
        assertEquals("23:55", draft.departureTime)
        assertEquals("2026-01-15T23:55", draft.departureDateTimeIso)
        assertEquals("2026-01-15T22:40", draft.originStartTime)
        assertEquals("Confirmed", draft.status)
        assertEquals(125000L, draft.amountPaise)
        assertEquals("15, 16", draft.seatNumbers)
        assertEquals(
            "VIJAYA NOMULA (45, FEMALE), SAI CHAND NOMULA (28, MALE)",
            draft.passengerNames
        )

        val trip = draft.toTrip()
        assertEquals(1250.0, trip.amountInRupees, 0.001)
        assertFalse(trip.isWaitlisted())
        assertEquals("WD2H", trip.maskedReferenceLast4())
    }

    @Test
    fun `parsers support values on the next line after the label`() {
        val multiLineIrctc = """
            Electronic Reservation Slip (ERS)
            Booked from
            KHAMMAM (KMT)
            To
            SECUNDERABAD JN. (SC)
            Departure*
            06:35 19-Nov-2026
            Arrival*
            10:50 19-Nov-2026
            PNR
            6952208777
            Train No./Name
            11020/KONARK EXPRESS
            Class
            SLEEPER CLASS (SL)
            Booking Status
            RLWL/29
            Current Status
            RLWL/28
            Total Fare (all inclusive)
            Rs. 201.80
            Transaction ID
            100004928172635
        """.trimIndent()

        val draft = irctcParser.parse(multiLineIrctc)
        assertEquals("6952208777", draft.reference)
        assertEquals("KHAMMAM", draft.originName)
        assertEquals("KMT", draft.originCode)
        assertEquals("SECUNDERABAD JN.", draft.destinationName)
        assertEquals("SC", draft.destinationCode)
        assertEquals("2026-11-19", draft.departureDate)
        assertEquals("06:35", draft.departureTime)
        assertEquals("100004928172635", draft.transactionId)
        assertEquals(20180L, draft.amountPaise)
    }

    @Test
    fun `past bus journey must not appear on the widget while upcoming train within 5 days appears`() {
        val trainTrip = router.parse(SampleTickets.SAMPLE_IRCTC_ERS_TEXT).toTrip().copy(id = 1L)
        val pastBusTrip = router.parse(SampleTickets.SAMPLE_REDBUS_TSRTC_TEXT).toTrip().copy(id = 2L)

        // Current reference time: 16-Nov-2026 08:00 (3 days before the 19-Nov-2026 train,
        // and 10 months after the 15-Jan-2026 bus journey)
        val now = LocalDateTime.of(2026, 11, 16, 8, 0)

        val widgetTrips = TripFilter.filterUpcomingForWidget(
            trips = listOf(trainTrip, pastBusTrip),
            now = now
        )

        assertEquals(1, widgetTrips.size)
        assertEquals(trainTrip.id, widgetTrips.first().id)
        assertEquals("Khammam → Secunderabad", widgetTrips.first().title)
        assertFalse(
            "Past bus journey (2026-01-15) must NOT appear on the widget",
            widgetTrips.any { it.id == pastBusTrip.id }
        )

        // Also verify Upcoming vs Past list grouping on Home screen
        val (upcoming, past) = TripFilter.partitionUpcomingAndPast(
            trips = listOf(trainTrip, pastBusTrip),
            now = now
        )
        assertEquals(listOf(trainTrip), upcoming)
        assertEquals(listOf(pastBusTrip), past)
    }

    @Test
    fun `date and time normalizer handles all required formats`() {
        assertEquals("2026-11-19", DateTimeNormalizer.normalizeDate("19-Nov-2026"))
        assertEquals("2026-01-15", DateTimeNormalizer.normalizeDate("15-Jan-2026"))
        assertEquals("2026-01-15", DateTimeNormalizer.normalizeDate("15/01/2026"))
        assertEquals("23:55", DateTimeNormalizer.normalizeTime("11:55 PM"))
        assertEquals("06:35", DateTimeNormalizer.normalizeTime("06:35"))
        assertEquals("22:40", DateTimeNormalizer.normalizeTime("22:40 Hrs"))
        assertNull(DateTimeNormalizer.normalizeDate("not-a-date"))
    }
}
