package com.example

import com.example.data.PnrStatusCategory
import com.example.sms.PnrReplyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PnrReplyParserTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$name")
            ?: javaClass.getResourceAsStream("/fixtures/$name")
            ?: error("Fixture not found: fixtures/$name")
        return stream.bufferedReader().use { it.readText() }.trim()
    }

    @Test
    fun `parse confirmed reply fixture asserting last 4 digits 8777`() {
        val reply = loadFixture("reply_confirmed.txt")
        val result = PnrReplyParser.parse(reply, pendingPnrLast4 = "8777")

        assertEquals("8777", result.matchedPnrLast4)
        assertEquals("CNF", result.extractedStatus)
        assertEquals(PnrStatusCategory.CONFIRMED, result.category)
        assertTrue(result.isSuccess)
        assertFalse(result.isCancelled)
    }

    @Test
    fun `parse rac reply fixture asserting last 4 digits 8777`() {
        val reply = loadFixture("reply_rac.txt")
        val result = PnrReplyParser.parse(reply, pendingPnrLast4 = "8777")

        assertEquals("8777", result.matchedPnrLast4)
        assertEquals("RAC/12", result.extractedStatus)
        assertEquals(PnrStatusCategory.RAC, result.category)
        assertTrue(result.isSuccess)
        assertFalse(result.isCancelled)
    }

    @Test
    fun `parse waitlisted rlwl28 reply fixture asserting last 4 digits 8777`() {
        val reply = loadFixture("reply_waitlisted_rlwl28.txt")
        val result = PnrReplyParser.parse(reply, pendingPnrLast4 = "8777")

        assertEquals("8777", result.matchedPnrLast4)
        assertEquals("RLWL/28", result.extractedStatus)
        assertEquals(PnrStatusCategory.WAITLISTED, result.category)
        assertTrue(result.isSuccess)
        assertFalse(result.isCancelled)
    }

    @Test
    fun `parse chart prepared fixture asserting last 4 digits 8777`() {
        val reply = loadFixture("reply_chart_prepared.txt")
        val result = PnrReplyParser.parse(reply, pendingPnrLast4 = "8777")

        assertEquals("8777", result.matchedPnrLast4)
        assertEquals("CNF/B2/31", result.extractedStatus)
        assertEquals(PnrStatusCategory.CONFIRMED, result.category)
        assertTrue(result.isChartPrepared)
        assertTrue(result.isSuccess)
        assertFalse(result.isCancelled)
    }

    @Test
    fun `parse cancelled ticket fixture asserting last 4 digits 8777`() {
        val reply = loadFixture("reply_cancelled.txt")
        val result = PnrReplyParser.parse(reply, pendingPnrLast4 = "8777")

        assertEquals("8777", result.matchedPnrLast4)
        assertEquals("CANCELLED", result.extractedStatus)
        assertEquals(PnrStatusCategory.CANCELLED, result.category)
        assertTrue(result.isCancelled)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `other sender fixture is rejected by isSender139`() {
        assertFalse(PnrReplyParser.isSender139("VK-BANKOTP"))
        assertFalse(PnrReplyParser.isSender139("Google"))
        assertFalse(PnrReplyParser.isSender139("9876543210"))
        assertFalse(PnrReplyParser.isSender139(null))
        assertFalse(PnrReplyParser.isSender139(""))
    }

    @Test
    fun `isSender139 accurately identifies 139 senders`() {
        assertTrue(PnrReplyParser.isSender139("139"))
        assertTrue(PnrReplyParser.isSender139("+91139"))
        assertTrue(PnrReplyParser.isSender139("VK-139"))
        assertTrue(PnrReplyParser.isSender139("AD-139"))
        assertTrue(PnrReplyParser.isSender139("VM-139"))
        assertTrue(PnrReplyParser.isSender139("91139"))
    }

    @Test
    fun `unrecognised reply returns UNKNOWN and does not mutate status`() {
        val reply = loadFixture("reply_unrecognised.txt")
        val result = PnrReplyParser.parse(reply, pendingPnrLast4 = "8777")

        assertEquals("8777", result.matchedPnrLast4)
        assertNull(result.extractedStatus)
        assertEquals(PnrStatusCategory.UNKNOWN, result.category)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `match pending PNR by last 4 digits when full PNR is masked in reply`() {
        val reply = "Dear passenger, status for PNR ending in 8777 is CNF/S4/32"
        val result = PnrReplyParser.parse(reply, pendingPnrLast4 = "8777")

        assertEquals("8777", result.matchedPnrLast4)
        assertEquals("CNF/S4/32", result.extractedStatus)
        assertEquals(PnrStatusCategory.CONFIRMED, result.category)
        assertTrue(result.isSuccess)
    }
}
