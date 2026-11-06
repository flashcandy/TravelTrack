package com.example

import com.example.data.PnrStatusCategory
import com.example.sms.PnrReplyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PnrReplyParserTest {

    @Test
    fun `parse confirmed status with coach and seat number`() {
        val reply = "PNR:6952208777,TRAIN:11020,DOJ:19-11-2026,SC TO KMT,STATUS:CNF/B2/31,CHART NOT PREPARED"
        val result = PnrReplyParser.parse(reply)

        assertEquals("6952208777", result.pnr)
        assertEquals("CNF/B2/31", result.extractedStatus)
        assertEquals(PnrStatusCategory.CONFIRMED, result.category)
        assertFalse(result.isChartPrepared)
        assertFalse(result.isCancelled)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse waitlisted reply with booking and current status tokens`() {
        val reply = "PNR: 6952208777 Train: 11020 Date: 19-Nov-2026 Booking: RLWL/29 Current: RLWL/28 Chart not prepared."
        val result = PnrReplyParser.parse(reply)

        assertEquals("6952208777", result.pnr)
        assertEquals("RLWL/28", result.extractedStatus)
        assertEquals(PnrStatusCategory.WAITLISTED, result.category)
        assertFalse(result.isChartPrepared)
        assertFalse(result.isCancelled)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse RAC status reply`() {
        val reply = "PNR 6952208777 Trn:12723 DOJ:10-12-26 Status:RAC/12 Chart Not Prepared"
        val result = PnrReplyParser.parse(reply)

        assertEquals("6952208777", result.pnr)
        assertEquals("RAC/12", result.extractedStatus)
        assertEquals(PnrStatusCategory.RAC, result.category)
        assertFalse(result.isChartPrepared)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse simple waitlisted status reply`() {
        val reply = "PNR 6952208777 Status: WL/3"
        val result = PnrReplyParser.parse(reply)

        assertEquals("6952208777", result.pnr)
        assertEquals("WL/3", result.extractedStatus)
        assertEquals(PnrStatusCategory.WAITLISTED, result.category)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse pooled quota waitlisted reply`() {
        val reply = "PNR: 6952208777 Current Status: PQWL/5"
        val result = PnrReplyParser.parse(reply)

        assertEquals("6952208777", result.pnr)
        assertEquals("PQWL/5", result.extractedStatus)
        assertEquals(PnrStatusCategory.WAITLISTED, result.category)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse cancelled ticket phrase`() {
        val reply = "PNR 6952208777 Ticket Cancelled."
        val result = PnrReplyParser.parse(reply)

        assertEquals("6952208777", result.pnr)
        assertEquals("CANCELLED", result.extractedStatus)
        assertEquals(PnrStatusCategory.CANCELLED, result.category)
        assertTrue(result.isCancelled)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse chart prepared with confirmed status`() {
        val reply = "PNR: 6952208777, Current Status: CNF, Chart Prepared"
        val result = PnrReplyParser.parse(reply)

        assertEquals("6952208777", result.pnr)
        assertEquals("CNF", result.extractedStatus)
        assertEquals(PnrStatusCategory.CONFIRMED, result.category)
        assertTrue(result.isChartPrepared)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `unrecognized reply must return UNKNOWN and fail gracefully`() {
        val reply = "Dear Customer, the requested service is temporarily unavailable. Please try again."
        val result = PnrReplyParser.parse(reply)

        assertNull(result.pnr)
        assertNull(result.extractedStatus)
        assertEquals(PnrStatusCategory.UNKNOWN, result.category)
        assertFalse(result.isSuccess)
    }
}
