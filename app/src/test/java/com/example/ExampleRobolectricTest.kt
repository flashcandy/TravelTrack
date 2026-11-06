package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TripRepository
import com.example.parser.SampleTickets
import com.example.parser.TicketParserRouter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TripRepository
    private val router = TicketParserRouter()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TripRepository(database.tripDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app_name and waitlist warning string resources match specification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("TravelTrack", context.getString(R.string.app_name))
        assertEquals(
            "Waitlisted tickets may not be allowed to board. Check with the operator.",
            context.getString(R.string.waitlist_warning_banner)
        )
        assertEquals(
            "No trips in the next 5 days",
            context.getString(R.string.widget_empty_state)
        )
    }

    @Test
    fun `saving duplicate PNR updates existing record in Room database`() = runTest {
        val draft1 = router.parse(SampleTickets.SAMPLE_IRCTC_ERS_TEXT)
        val firstId = repository.saveTrip(draft1.toTrip())
        assertTrue(firstId > 0L)

        // Duplicate detection by reference number (PNR 6952208777)
        val duplicateFound = repository.findDuplicateByReference("6952208777")
        assertNotNull(duplicateFound)
        assertEquals(firstId, duplicateFound?.id)

        // Saving updated status for same PNR merges cleanly without creating a second row
        val updatedDraft = draft1.copy(status = "CNF/S4/32")
        repository.saveTrip(updatedDraft.toTrip(), overwriteDuplicate = true)

        val allTrips = repository.getAllTripsSnapshot()
        assertEquals(1, allTrips.size)
        assertEquals("CNF/S4/32", allTrips.first().status)
    }
}
