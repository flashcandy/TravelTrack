# TravelTrack — Nothing OS Offline Ticket Tracker & Glance Widget

**TravelTrack** is an offline-first Android application built in Kotlin with **Jetpack Compose**, **Room**, **WorkManager**, **PdfBox-Android**, **ML Kit Text Recognition**, and **Jetpack Glance** (`androidx.glance:glance-appwidget`). It extracts train and bus trip details from uploaded or shared tickets (`PDF`, `JPG`, `PNG`), normalizes dates and times to ISO 8601 (`yyyy-MM-dd` / `HH:mm`), stores them locally on the device, and surfaces upcoming trips in the next 5 days on a Nothing OS styled home screen widget.

It also supports **live PNR status checking via SMS to 139** for waitlisted and RAC Indian Railways train tickets with automatic reply parsing and widget updates.

---

## Permissions & Sideloading Notice

> **Important**: `SEND_SMS` and `RECEIVE_SMS` are sensitive permissions restricted under Google Play Developer Program policies. TravelTrack is designed for **personal, sideloaded APK use**. 
> - SMS is used exclusively to dispatch `PNR <10-digit PNR>` to `139` and read incoming status replies within a 3-minute timeout window.
> - Messages from all other phone numbers and senders are strictly ignored and never read.
> - The application has zero internet access (the `INTERNET` permission is completely stripped). All ticket data, passenger names, and check history remain strictly on your device.

---

## Project Structure

```text
app/src/main/
├── AndroidManifest.xml                     # Offline manifest, ACTION_SEND filters, SMS receivers, Glance & Alarm receivers
├── java/com/example/
│   ├── MainActivity.kt                     # Edge-to-edge activity, ACTION_SEND handler, widget deep-link router
│   ├── data/
│   │   ├── Trip.kt                         # Room Entity (paise amount, ISO date/time, currentStatus, lastCheckedAt, PnrStatusCategory)
│   │   ├── TripDao.kt                      # Reactive Flow queries, status updates & duplicate reference lookup
│   │   ├── PnrStatusCheck.kt               # Entity logging PNR check attempts, replies, and timestamps
│   │   ├── PnrStatusCheckDao.kt            # DAO for pending check lookups, history, and cleanups
│   │   ├── AppDatabase.kt                  # Room database singleton with MIGRATION_1_2
│   │   ├── TripFilter.kt                   # Pure 5-day widget window filter & Upcoming/Past partitioning
│   │   ├── TripRepository.kt               # Repository with duplicate reference detection & PNR check logging
│   │   └── SettingsRepository.kt           # DataStore Preferences for notification toggles, theme, & auto-check PNR
│   ├── parser/
│   │   ├── TicketExtractionModels.kt       # ParsedTicketDraft, ConfidenceLevel, TicketFormat
│   │   ├── DateTimeNormalizer.kt           # Normalizes dates to yyyy-MM-dd, times to 24h HH:mm, amounts to paise
│   │   ├── LabelValueExtractor.kt          # Label-based value extractor (same line or next line, position-independent)
│   │   ├── IrctcTicketParser.kt            # IRCTC Electronic Reservation Slip parser
│   │   ├── RedBusTicketParser.kt           # redBus / TSRTC bus ticket parser
│   │   ├── UnknownTicketParser.kt          # Unknown ticket fallback + TicketParserRouter
│   │   ├── TicketDocumentExtractor.kt      # PdfBox-Android text layer + PdfRenderer & ML Kit OCR fallback
│   │   └── SampleTickets.kt                # Sample IRCTC & redBus ticket texts
│   ├── sms/
│   │   ├── PnrReplyParser.kt               # 139 SMS reply parser (PNR, status tokens, chart prepared, cancelled)
│   │   ├── SmsStatusSender.kt              # Dispatches "PNR <PNR>" to 139 via SmsManager with 10-digit validation
│   │   ├── SmsStatusReceiver.kt            # SMS_RECEIVED BroadcastReceiver filtering sender 139 within 3-min timeout
│   │   └── PnrStatusWorker.kt              # WorkManager periodic check (every 6h; every 2h in final 24h)
│   ├── widget/
│   │   ├── TravelTrackWidget.kt            # GlanceAppWidget with 4-state dot colors (Green/Amber/Red/Grey) and last checked text
│   │   ├── TravelTrackWidgetReceiver.kt    # GlanceAppWidgetReceiver
│   │   ├── WidgetRefreshWorker.kt          # WorkManager 6-hour periodic & midnight rollover refresh
│   │   └── TravelTrackWidgetPreviews.kt    # @Preview composables for 2x2 Compact, 4x2 List, and Empty widget states
│   ├── notifications/
│   │   ├── TripNotificationScheduler.kt    # Schedules 24h & 2h alerts via setExactAndAllowWhileIdle with fallback
│   │   ├── TripAlarmReceiver.kt            # Posts local departure notifications
│   │   └── BootCompletedReceiver.kt        # Reschedules alerts & widget jobs on BOOT_COMPLETED
│   └── ui/
│       ├── TravelTrackViewModel.kt         # StateFlow MVVM state holder managing PNR checking & timeout countdown
│       ├── components/
│       │   └── NothingComponents.kt        # Flat 1dp bordered cards, perforated dividers, status pills, confidence badges
│       ├── screens/
│       │   ├── HomeScreen.kt               # Upcoming & Past list, live widget preview toggle, red '+' FAB
│       │   ├── AddTicketScreen.kt          # PDF/JPG/PNG picker, share info, & sample parser bench
│       │   ├── ReviewTicketScreen.kt       # Editable form with per-field confidence hints & raw text viewer
│       │   ├── TripDetailScreen.kt         # Boarding pass, "Check PNR status" (139 SMS), manual update dialog, debug raw reply
│       │   └── SettingsScreen.kt           # SMS rationale, auto-check toggles, clear history, widget sync info, theme toggle
│       └── theme/
│           ├── Color.kt                    # Monochrome #000000/#111111 + Nothing Red #D71921
│           ├── Type.kt                     # Bundled Doto (dot-matrix display) & Space Grotesk (body/labels)
│           └── Theme.kt                    # Full-dark default theme (Android 12+ dynamic color disabled)
└── res/
    ├── font/
    │   ├── doto.ttf                        # Bundled Google Font: Doto
    │   └── space_grotesk.ttf               # Bundled Google Font: Space Grotesk
    └── xml/
        └── travel_track_widget_info.xml    # AppWidget provider info (2x2 and 4x2 sizing)
```

---

## Build & Test Steps

1. **Assemble Debug APK**:
   ```bash
   gradle assembleDebug
   ```
2. **Run Unit & Robolectric Tests (Parsers, 139 SMS Reply Parser, Date/Time Normalizer, 5-Day Widget Filter, UI)**:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## Testing the Parser with a Real 139 SMS Reply

When Indian Railways 139 replies to your SMS query, you can verify and expand parser test cases:

1. On the **Trip Detail** screen, if an SMS reply was received, the full unmodified text appears under **LAST 139 SMS REPLY (DEBUG)**.
2. Long-press or copy the raw SMS reply text.
3. Open `app/src/test/java/com/example/PnrReplyParserTest.kt`.
4. Add a test case with your real reply:
   ```kotlin
   @Test
   fun `parse real 139 SMS reply`() {
       val realReply = "PASTE_YOUR_RAW_REPLY_HERE"
       val result = PnrReplyParser.parse(realReply)

       assertEquals("YOUR_10_DIGIT_PNR", result.pnr)
       assertEquals("EXPECTED_STATUS_TOKEN", result.extractedStatus) // e.g. "CNF/B2/31", "RAC/12", "WL/3"
       assertTrue(result.isSuccess)
   }
   ```
5. Run the test suite:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## How to Add a New Ticket Format

TravelTrack uses a modular, label-driven parser architecture in `com.example.parser`:

1. **Create a New Parser Class** in `app/src/main/java/com/example/parser/`:
   - Define a list of `KNOWN_LABELS` that appear on the ticket.
   - Implement `fun canParse(text: String): Boolean` to detect the ticket type by header keywords or distinctive labels.
   - Implement `fun parse(text: String): ParsedTicketDraft`:
     - Use `LabelValueExtractor.extractValueByLabel(text, targetLabels, KNOWN_LABELS)` to find each field by its label on the same line or the next line without relying on text coordinates.
     - Normalize dates with `DateTimeNormalizer.normalizeDate(raw)` (`yyyy-MM-dd`), times with `DateTimeNormalizer.normalizeTime(raw)` (`HH:mm`), and fares with `DateTimeNormalizer.parseAmountToPaise(raw)`.
2. **Register the Parser in `TicketParserRouter.kt`** (inside `UnknownTicketParser.kt`).
3. **Add Unit Tests** in `app/src/test/java/com/example/ExampleUnitTest.kt`.
