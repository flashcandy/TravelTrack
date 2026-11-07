# TravelTrack — Nothing OS Offline Ticket Tracker & Glance Widget

**TravelTrack** is an offline-first Android application built in Kotlin with **Jetpack Compose**, **Room**, **WorkManager**, **PdfBox-Android**, **ML Kit Text Recognition**, and **Jetpack Glance** (`androidx.glance:glance-appwidget`). It extracts train and bus trip details from uploaded or shared tickets (`PDF`, `JPG`, `PNG`), normalizes dates and times to ISO 8601 (`yyyy-MM-dd` / `HH:mm`), stores them locally on the device, and surfaces upcoming trips in the next 5 days on a Nothing OS styled home screen widget.

It also supports **live PNR status checking via 139** for waitlisted and RAC Indian Railways train tickets with automatic notification reply reading and widget updates.

---

## Permissions & Offline Architecture

> **Privacy & Permissions Boundary**:
> - **Zero SMS Permissions by Default**: The primary method opens your device's default SMS app with a prefilled message via `smsto:139` with `sms_body = "PNR <10-digit PNR>"`. The user taps Send in their trusted messaging app. No SMS permissions are required.
> - **No `RECEIVE_SMS` or `READ_SMS`**: TravelTrack does **not** declare or request `RECEIVE_SMS` or `READ_SMS`.
> - **Notification Listener Service (`PnrReplyListenerService`)**: Reads incoming status replies posted by your device's Messages app. Only notifications identifying sender `139` matching an active pending check from the last 3 minutes are processed. All other notifications are immediately ignored without being stored or inspected.
> - **Optional `SEND_SMS` (Settings)**: For users wanting background auto-checks, an optional "Send automatically via SMS" switch can be toggled in Settings to dispatch via `SmsManager`. If denied, it falls back to the prefilled intent.
> - **100% Offline (No Internet)**: The `INTERNET` permission is completely stripped. All ticket data, passenger records, and check logs remain strictly local to your device.

---

## Project Structure

```text
app/src/main/
├── AndroidManifest.xml                     # Offline manifest, NotificationListenerService, Glance & Alarm receivers
├── java/com/example/
│   ├── MainActivity.kt                     # Edge-to-edge activity, ACTION_SEND handler, notification access check on resume
│   ├── data/
│   │   ├── Trip.kt                         # Room Entity (paise amount, ISO date/time, currentStatus, lastCheckedAt, PnrStatusCategory)
│   │   ├── TripDao.kt                      # Reactive Flow queries, status updates & clearAllRawReplies
│   │   ├── PnrStatusCheck.kt               # Entity logging PNR check attempts, method ("PREFILLED_SMS" / "AUTO_SMS"), timestamps
│   │   ├── PnrStatusCheckDao.kt            # DAO for pending check lookups, history, and cleanups
│   │   ├── AppDatabase.kt                  # Room database with MIGRATION_1_2, MIGRATION_2_3, and MIGRATION_3_4
│   │   ├── TripFilter.kt                   # Pure 5-day widget window filter & Upcoming/Past partitioning
│   │   ├── TripRepository.kt               # Repository with duplicate reference detection & PNR check logging
│   │   └── SettingsRepository.kt           # DataStore Preferences for notification toggles, theme, auto-check, & auto-send
│   ├── parser/
│   │   ├── TicketExtractionModels.kt       # ParsedTicketDraft, ConfidenceLevel, TicketFormat
│   │   ├── DateTimeNormalizer.kt           # Normalizes dates to yyyy-MM-dd, times to 24h HH:mm, amounts to paise
│   │   ├── LabelValueExtractor.kt          # Label-based value extractor (same line or next line, bounded by known labels)
│   │   ├── IrctcTicketParser.kt            # IRCTC Electronic Reservation Slip parser with structured Passenger table parsing
│   │   ├── RedBusTicketParser.kt           # redBus / TSRTC bus ticket parser
│   │   ├── UnknownTicketParser.kt          # Unknown ticket fallback + TicketParserRouter
│   │   ├── TicketDocumentExtractor.kt      # PdfBox-Android text layer + PdfRenderer & ML Kit OCR fallback
│   │   └── SampleTickets.kt                # Sample IRCTC & redBus ticket texts
│   ├── sms/
│   │   ├── PnrReplyParser.kt               # 139 reply parser (strict status tokens, last 4 digits matching, isSender139 check)
│   │   ├── PnrStatusSender.kt              # Prefilled SMS intent ("smsto:139") and optional SmsManager dispatch
│   │   ├── PnrReplyListenerService.kt      # NotificationListenerService reading 139 replies with 3-min timeout
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
│       │   ├── TripDetailScreen.kt         # Boarding pass, "Check PNR status" (139 SMS), manual update dialog, masked PNR
│       │   └── SettingsScreen.kt           # Notification Access badge & button, auto-check, auto-send, clear history
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
2. **Run Unit & Robolectric Tests (Parsers, 139 Reply Fixtures, Date/Time Normalizer, 5-Day Widget Filter, UI)**:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## Testing with Fixtures and Real 139 Replies

Test fixtures are stored under `app/src/test/resources/fixtures/`:
- `reply_confirmed.txt`
- `reply_rac.txt`
- `reply_waitlisted_rlwl28.txt`
- `reply_chart_prepared.txt`
- `reply_cancelled.txt`
- `reply_unrecognised.txt`
- `reply_other_sender.txt`

When you receive a real reply from 139:
1. On the **Trip Detail** screen, view the raw text under **LAST 139 SMS REPLY (DEBUG)**.
2. Add a new fixture file in `src/test/resources/fixtures/`.
3. Assert on the last 4 digits of the PNR and verify the status token extraction in `PnrReplyParserTest.kt`.
