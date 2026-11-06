# TravelTrack — Nothing OS Offline Ticket Tracker & Glance Widget

**TravelTrack** is an offline-first Android application built in Kotlin with **Jetpack Compose**, **Room**, **WorkManager**, **PdfBox-Android**, **ML Kit Text Recognition**, and **Jetpack Glance** (`androidx.glance:glance-appwidget`). It extracts train and bus trip details from uploaded or shared tickets (`PDF`, `JPG`, `PNG`), normalizes dates and times to ISO 8601 (`yyyy-MM-dd` / `HH:mm`), stores them locally on the device, and surfaces upcoming trips in the next 5 days on a Nothing OS styled home screen widget.

---

## Project Structure

```text
app/src/main/
├── AndroidManifest.xml                     # Offline manifest (no INTERNET), ACTION_SEND filters, Glance & Alarm receivers
├── java/com/example/
│   ├── MainActivity.kt                     # Edge-to-edge activity, ACTION_SEND handler, widget deep-link router
│   ├── data/
│   │   ├── Trip.kt                         # Room Entity (paise amount, ISO date/time, waitlist & last-4 privacy helpers)
│   │   ├── TripDao.kt                      # Reactive Flow queries & duplicate reference lookup
│   │   ├── AppDatabase.kt                  # Room database singleton
│   │   ├── TripFilter.kt                   # Pure 5-day widget window filter & Upcoming/Past partitioning
│   │   ├── TripRepository.kt               # Repository with duplicate reference detection & merge
│   │   └── SettingsRepository.kt           # DataStore Preferences for notification toggles, theme, & widget sync time
│   ├── parser/
│   │   ├── TicketExtractionModels.kt       # ParsedTicketDraft, ConfidenceLevel, TicketFormat
│   │   ├── DateTimeNormalizer.kt           # Normalizes dates to yyyy-MM-dd, times to 24h HH:mm, amounts to paise
│   │   ├── LabelValueExtractor.kt          # Label-based value extractor (same line or next line, position-independent)
│   │   ├── IrctcTicketParser.kt            # IRCTC Electronic Reservation Slip parser
│   │   ├── RedBusTicketParser.kt           # redBus / TSRTC bus ticket parser
│   │   ├── UnknownTicketParser.kt          # Unknown ticket fallback + TicketParserRouter
│   │   ├── TicketDocumentExtractor.kt      # PdfBox-Android text layer + PdfRenderer & ML Kit OCR fallback
│   │   └── SampleTickets.kt                # Sample IRCTC & redBus ticket texts
│   ├── widget/
│   │   ├── TravelTrackWidget.kt            # GlanceAppWidget with SizeMode.Responsive (2x2 Compact & 4x2 List)
│   │   ├── TravelTrackWidgetReceiver.kt    # GlanceAppWidgetReceiver
│   │   ├── WidgetRefreshWorker.kt          # WorkManager 6-hour periodic & midnight rollover refresh
│   │   └── TravelTrackWidgetPreviews.kt    # @Preview composables for 2x2 Compact, 4x2 List, and Empty widget states
│   ├── notifications/
│   │   ├── TripNotificationScheduler.kt    # Schedules 24h & 2h alerts via setExactAndAllowWhileIdle with fallback
│   │   ├── TripAlarmReceiver.kt            # Posts local departure notifications
│   │   └── BootCompletedReceiver.kt        # Reschedules alerts & widget jobs on BOOT_COMPLETED
│   └── ui/
│       ├── TravelTrackViewModel.kt         # StateFlow MVVM state holder
│       ├── components/
│       │   └── NothingComponents.kt        # Flat 1dp bordered cards, perforated dividers, status pills, confidence badges
│       ├── screens/
│       │   ├── HomeScreen.kt               # Upcoming & Past list, live widget preview toggle, red '+' FAB
│       │   ├── AddTicketScreen.kt          # PDF/JPG/PNG picker, share info, & sample parser bench
│       │   ├── ReviewTicketScreen.kt       # Editable form with per-field confidence hints & raw text viewer
│       │   ├── TripDetailScreen.kt         # Boarding pass detail view with RLWL/PQWL/RSWL/WL warning banner
│       │   └── SettingsScreen.kt           # Notification toggles, widget sync info, theme toggle, Delete All Data
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
2. **Run Unit & Robolectric Tests (Parsers, Date/Time Normalizer, 5-Day Widget Filter, UI)**:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## How to Add a New Ticket Format

TravelTrack uses a modular, label-driven parser architecture in `com.example.parser`:

1. **Create a New Parser Class** in `app/src/main/java/com/example/parser/` (for example, `VandeBharatPassParser.kt` or `AbhiBusTicketParser.kt`):
   - Define a list of `KNOWN_LABELS` that appear on the ticket.
   - Implement `fun canParse(text: String): Boolean` to detect the ticket type by header keywords or distinctive labels.
   - Implement `fun parse(text: String): ParsedTicketDraft`:
     - Use `LabelValueExtractor.extractValueByLabel(text, targetLabels, KNOWN_LABELS)` to find each field by its label on the same line or the next line without relying on text coordinates.
     - Normalize dates with `DateTimeNormalizer.normalizeDate(raw)` (`yyyy-MM-dd`), times with `DateTimeNormalizer.normalizeTime(raw)` (`HH:mm`), and fares with `DateTimeNormalizer.parseAmountToPaise(raw)`.
     - Populate `fieldConfidences` (`ConfidenceLevel.HIGH`, `MEDIUM`, `LOW`, `MANUAL`) and `fieldHints` so the Review screen displays clear provenance hints.
2. **Register the Parser in `TicketParserRouter.kt`** (inside `UnknownTicketParser.kt`):
   ```kotlin
   fun parse(rawText: String): ParsedTicketDraft {
       return when {
           irctcParser.canParse(rawText) -> irctcParser.parse(rawText)
           redBusParser.canParse(rawText) -> redBusParser.parse(rawText)
           myNewParser.canParse(rawText) -> myNewParser.parse(rawText)
           else -> unknownParser.parse(rawText)
       }
   }
   ```
3. **Add Unit Tests** in `app/src/test/java/com/example/TicketParsersTest.kt` verifying field extraction, ISO date/time normalization, and 5-day widget filtering.
