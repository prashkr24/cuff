# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project aims at [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
once it reaches 1.0.

**No version has been released yet.** There are no tags in this repository and
no published APK, so everything below sits under *Unreleased*. The app
currently reports `versionName 0.3.0` / `versionCode 24`, which is what the
first tag will carry.

## [Unreleased]

Reconstructed from the commit history on `native`, which began on 2 November
2026. Dates in brackets are when the work landed, not release dates.

### Added

- **The Health half is opt-in**, behind a switch at the top of the Device tab
  and off on a fresh install. The relay is what the app is for, and wanting
  only that should not cost thirteen health permissions. Turning it on is
  what asks for them; turning it off takes the tab, its widgets and its two
  activity buzzes away, cancels the background work and clears the caches —
  and deletes nothing, including food already written to Health Connect. An
  install already using the tab keeps it. [13 Nov]
- **The app and its widgets open on their last reading.** Both persist the
  last `HealthSnapshot` — about 40 KB for the tab, 12 KB for the widgets —
  so a screen draws real figures immediately and refreshes underneath
  instead of showing a spinner. Restored data keeps its real age, and
  anything meaning "so far today" is dropped once the day has turned. [13 Nov]
- **Fewer Health Connect round trips.** `widen` rounds up to whole months, so
  stepping a seven-day chart back four times costs one read of 60 days rather
  than four reads over nearly the same ground. [13 Nov]
- **The sleep score on the sleep stages widget**, at 4x4, in the trailing
  slot the Sleep card already uses and from the same reading, so the two
  sleep widgets cannot disagree. [10 Nov]
- **Spoken descriptions on every chart worth hearing.** A screen reader landed
  on a Canvas, found nothing, and said nothing; the readout under it fills
  from a scrub, which is not available to someone using a reader. Each chart
  now carries one sentence built from the data it draws, with units, signs and
  durations written as words because a reader speaks none of the symbols.
  Charts inside tiles stay silent on purpose: the tile's own text already says
  it. [6 Nov]
- **Tile heights that follow the font scale**, clamped at 1.5×. At the largest
  system font size a small tile's chart was squeezed to zero and vanished
  without any error. [6 Nov]
- **State that survives process death.** The half-filled Log food form, every
  chart window, the picked night and the food log's day are `rememberSaveable`
  now; `ChartWindow` round-trips through its own `Saver`. Rotation was already
  safe through `configChanges`. [6 Nov]
- **A confirmation before forgetting a saved food**, matching the one the
  logged-entry delete already had, and a haptic on both destructive confirms.
  [6 Nov]

- **The relay.** A notification listener and a foreground service that buzz a
  Fitbit Air for calls and notifications, over Golden Gate → DTLS → CoAP. The
  band has no vibrate command; every pulse is a write to its `/settings`
  resource. [2 Nov]
- **A ring inside one held session**, so pulses land 97–175 ms apart rather
  than a session apart, and a ring that stops when the phone stops ringing
  rather than when a notification is posted. [2 Nov]
- **Per-app rules**, with a separate row for each kind of alert an app can
  raise, and a separate rule for calls where the app can ring. [3 Nov]
- **Per-person rules.** A caller is a one-way SHA-256 hash; a phone number
  hashes without the package so one person is one identity across apps, and a
  display name hashes with it. Contacts can be given a rule before they have
  ever rung. [3 Nov]
- **Patterns and strength chosen separately** — one pulse, two, a ring or
  silence, at either of the band's two strengths — with ring cadence settable
  per app and per person. [3 Nov]
- **Quiet hours, a pause and a Stop button**, with the relay's own
  notification carrying the reason it is silent. [3 Nov]
- **An activity history** that records every attempt with its stage, latency
  and error code, and deliberately holds no names and no message text. [3 Nov]
- **Boot and update restart**, a sticky service, and a battery-optimisation
  exemption request, so a relay somebody started comes back. [3 Nov]
- **The Health tab**, reading Health Connect: steps, sleep with stages, heart
  rate and zones, resting heart rate, HRV, breathing rate, skin temperature,
  weight, workouts, energy in against energy out, and nutrition. [4 Nov]
- **The one write** — a food log that saves `NutritionRecord`s, with saved
  foods kept in the app because Health Connect has no food library. [4 Nov]
- **A tile grid** the Health tab is drawn from: five sizes across six columns,
  reordered, resized, recoloured and removed on an editor screen that previews
  against a made-up week rather than your own readings. [4 Nov]
- **Choosable theming.** Eight grounds, six accents, three edge weights, a
  custom accent clamped rather than refused, and every neutral derived from
  the ground rather than declared as a constant. [4 Nov]
- **A crash log**, recorded automatically into private storage and sent only
  when somebody presses the button. [5 Nov]
- **Home screen widgets.** Five cell sizes backed by one Glance widget, plus a
  stack that pages several cards through one cell, with a catalogue under
  Device › Widgets that draws every design from the same code the widget uses.
  [5 Nov]
- **A baseline profile**, recorded against a cold start and a scroll of the
  Health tab, committed and shipped. [5 Nov]
- **Calls through the quiet window**, as a nested sub-option, since not being
  woken by messages and not wanting to know the phone is ringing are different
  wishes. [5 Nov]
- **Readiness.** A figure out of a hundred where fifty is your own usual,
  built from HRV, resting heart rate, last night and recent load, with all four
  parts always shown and the figure withheld until sixty mornings. [6 Nov]
- **Fitness age**, from VO₂ max against the Cooper Institute's middle values —
  and `Vo2MaxRecord`, which this app now reads. Called fitness age and never
  biological age. [6 Nov]
- **A morning report**, sent once the night has actually been written rather
  than at a fixed hour, and a **journal** that asks before bed. [6 Nov]
- **A sleep bank**, and **export** of four CSVs in a zip. [6 Nov]
- **A window control on every chart**, so any of them can be moved back
  through its own history rather than always ending today. [6 Nov]
- **Device provenance.** `Metadata.device` is read, so a record says what
  hardware it came off, and the Device tab asks the band its own name instead
  of printing a constant. [6 Nov]
- **A band icon that looks like the band**, after a watch, a pod and a capsule,
  and workout icons keyed on exercise type. [6 Nov]

### Changed

- **The whole app was rewritten from Flutter to native Kotlin and Compose**,
  and the transport moved in as the `:air` module rather than a package
  dependency. [3 Nov]
- **`applicationId` became `com.srineere.airbridge`**, with debug installing as
  `.dev` beside it. [3 Nov]
- **Minimum SDK raised to 28**, which is where the caller API the relay depends
  on actually lives. [4 Nov]
- **Steps are read from chosen sources**, not every writer. The phone counts
  its own steps under a synthetic package name, and added to the band's they
  counted one walk twice — about twenty per cent on the phone this was measured
  on. [4 Nov]
- **Every reading that can be double-counted is now counted from one place.**
  Steps, distance, calories, food, sleep and workouts are filtered by writer,
  each enumerated separately; point measurements are deliberately not, because
  filtering them could only ever delete data. [6 Nov]
- **"Last night" now means the night that ended this morning**, and nothing
  otherwise. Health Connect files a sleep session under the day it ended, so at
  two in the morning every screen headed "last night" was showing the night
  before. [6 Nov]
- **Rings and Dials became one widget family, "Drawn round."** [6 Nov]
- **The usual band is the quartiles pushed out by half an interquartile
  range**, rather than the quartiles alone, which put half of anybody's
  readings outside by construction. [6 Nov]
- **The installed-app list is held for the process** and read off the main
  thread, rather than hanging off state that is replaced on every write, which
  is what made the Apps screen stutter on every tap. [4 Nov]
- **Release builds are minified and shrunk**, with the band's side of the
  transport kept by ProGuard rules. [4 Nov]
- **CI points at the app that is actually here**, and runs assemble, both
  modules' unit tests, and lint. [4 Nov]

### Fixed

- **The hourly nudge fired outside its own window.** The end hour was
  inclusive, so a window shown as "09:00–21:00" could buzz at 21:50 — fifty
  minutes after the time chosen. Reported from a phone, and a test had been
  asserting the wrong behaviour rather than catching it. [13 Nov]
- **Widgets went blank after a reboot and said the stack was unconfigured.**
  A widget's `initialLayout` is inflated raw out of the APK after a restart —
  no adapter, no data — and the stack's empty TextView was visible with "Tap
  to choose what this stack holds" baked into it, over stacks that were
  configured perfectly well. The placeholder is blank now, a configured stack
  that has no data says so differently from one that was never set up, and
  the platform's own "Loading…" view is replaced by an empty card. [13 Nov]
- **Nothing refreshed widgets at boot.** The periodic work reschedules itself
  across a restart, but nothing covered the half hour before its first turn.
  A boot receiver now pokes a redraw from the kept snapshot, with no Health
  Connect read at boot at all. [13 Nov]
- **The 90-day window emptied the screen and said nothing.** `loading` meant
  "a read is in flight" and every screen read it as "there is nothing to
  show". It is `refreshing` now, the shorter window stays drawn, and a line
  under the window arrows says what is being read. [13 Nov]
- **A burst of notifications behind one slow band write disappeared.** The
  coalescer's cooldown is measured from the end of the previous command and
  the alert lifetime from when the notification was posted, so a held burst
  waited `write + 15s` against a budget of 30s and any write over about
  thirteen seconds killed every alert behind the first. A locked, idle phone
  is when Google Health takes the Air's one session and writes get slow,
  which is why it read as notifications going missing while the phone was
  away. The follow-up is now scheduled against the oldest waiting alert's
  expiry, and the lifetime is 90s so a full 45s band timeout plus the
  cooldown fits inside it. [10 Nov]
- **Expired alerts left no trace.** `expired` was not shown in the activity
  list, so the one failure with nowhere to be seen was the one holding the
  burst. It is shown, and every failure now reads "Failed" before it reads
  why, with twelve error codes traced to their raise sites and given a
  sentence each. [10 Nov]
- **Readiness was only ever computed by the screen showing it.** Opening it
  forced a 120-day read and said "Calibrating" until that landed; no
  background pass ever did the work, because the morning worker read 30 days
  and was cancelled outright when the morning report was off, which is the
  default. It now rides that worker and keeps the series, keyed by the
  morning it describes. [10 Nov]
- **The band widget called every app notification "Message"**, including the
  app's own step goal and hourly nudge, because the only name it would
  accept was a saved caller's. It resolves the app at draw time now, the way
  the Alerts list does. [10 Nov]
- **Opening the app from a widget could strand the Health tab.** The route
  extra was never consumed, so a configuration change replayed it; a widget
  asking for Health never navigated at all; and the bar treated a tap on an
  already-selected tab as a no-op. [10 Nov]
- **The workout list and the workout tile disagreed.** `SessionReader` read
  exercise sessions unfiltered while the tab's own count filtered them, so two
  apps writing the same run showed it once in the tile and twice in the
  history. Both filter now — with the empty filter folded back into no filter,
  because "Devices only" on a phone whose only workout writer is the phone
  resolves to nothing, and a history that filters to nothing is somebody's
  workouts gone rather than an honest zero. [6 Nov]
- **The step check drifted through the night.** It ran on
  `handler.postDelayed`, which counts in `SystemClock.uptimeMillis` and stops
  while the phone is suspended, so a night in Doze pushed the daily goal and
  the hourly nudge arbitrarily late. It is an `AlarmManager` alarm now,
  delivered to a receiver rather than through the service — routing it through
  `onStartCommand` would have dismissed each hourly nudge within a quarter
  hour of posting it, because `enterForeground` cancels that notification
  before it reads the action. Falls back to the old handler where no exact
  alarm can be scheduled, rather than throwing. [6 Nov]
- **Macro rows that did not add up to their own total.** Nutrition sums were
  filtered by writer in two reads out of seven, so with a source chosen and two
  food apps the Nutrition screen showed rows drawn from every writer under a
  heading drawn from one. All eight sums take the filter now; the food log and
  the meal count deliberately do not. [6 Nov]
- **`hourlyTotals` was wrong on both daylight-saving transitions.** It assumed
  a fixed 24-hour day and indexed buckets by elapsed hours rather than the
  local clock, so every bucket after a spring-forward sat one slot low and an
  autumn day's final hour was never read. [6 Nov]
- **`Written.Distance` was declared and never used** while the Goals screen
  told the user the source choice covered distance, and `SessionReader` read
  distance and energy unfiltered beside a day's burn that was filtered. [6 Nov]
- **Contacts were queried twice a second during a ring, on the UI thread.**
  The lookup is memoised for the length of one ring and dropped when it ends.
  [6 Nov]
- The food log's date used a hardcoded pattern rather than the locale's own,
  and the Health header's date froze when a tab was left open across midnight.
  [6 Nov]
- Calls on a locked phone and grouped WhatsApp calls were being silenced. [2 Nov]
- A ring stopped on a notification being removed rather than on the phone
  stopping ringing, and only the first call from a key would ring. [2 Nov]
- Saving a food entry refreshed and popped the screen in the same breath, so
  the read was cancelled mid-flight and an empty snapshot was stored over the
  real one. [4 Nov]
- `NutritionRecord` validates its own times in its constructor and requires
  start strictly before end, so building a meal as an instant threw on the main
  thread before the insert was attempted. [4 Nov]
- The step filter switched itself off: `stepWriters` asked for the granted
  permissions a second time, got an answer without steps in it, read that as
  "nothing to exclude" — and then cached it for six hours. [5 Nov]
- A live heart rate reading could eat the day's goal buzz: the goal was marked
  fired, nothing buzzed, and it never tried again that day. [5 Nov]
- Widgets reported the size they declared rather than the cell they were given,
  so content ended up crushed into the top third of a tall cell. [5 Nov]
- Readiness read "30 of 60" and always would have, because the window it was
  computed over was the tab's thirty days. [6 Nov]
- The CSV export wrote raw exercise-type integers and no heart rate at all,
  while the row offering the file promised heart rate. [6 Nov]
- Four screens wrapped `dayLabel` in a condition of their own and overrode it,
  printing "Last night" over a night that was not last night. [6 Nov]
- The food log mishandled entries written by other apps, including entries with
  no meal type, which vanished from the screen while the day's total went on
  counting them. [6 Nov]
- A baseline profile recorded against an app with no Health Connect grants
  looked like a tidier profile and was a wrong one; the generator now refuses
  to record and says why. [6 Nov]
- Buzz the band could be pressed into a guaranteed `BUSY` failure while a sync
  was in flight. [6 Nov]

### Removed

- **The journal, hidden rather than deleted.** No entry point, no reminder,
  and the already-booked WorkManager job is cancelled so it stops firing a
  notification for a screen that is no longer reachable. The code, its tests
  and anything already written all stay, so it can come back. [13 Nov]
- **The `INTERNET` permission from debug builds**, left over from Flutter's
  hot reload. This app makes no network call in any build type. [6 Nov]
- **The DataStore dependency**, declared and never used — all state is
  `SharedPreferences`. [6 Nov]
- **The Flutter lab and its state.** `master` still holds the original Flutter
  proof of concept; `native` is a ground-up rewrite and is where all work
  happens. [3 Nov]
- **Reading message text.** The listener takes metadata only. [3 Nov]
- **The blood oxygen tile.** Fitbit does not share SpO₂ through Health
  Connect — established from `dumpsys`, not guessed — so the tile could only
  ever have been empty. [6 Nov]
- **The Paper widget family**, built and then cut. Some of its colour
  machinery is still in `WidgetTheme.kt` and unused.
- **`requestPinAppWidget`.** Android deliberately skips the configuration
  activity for a pinned widget, so it arrives knowing nothing about what it is
  meant to show. [5 Nov]
- **`deviceGlyph`**, which matched substrings of model names against a
  hand-written list and whose only caller passed a constant. [6 Nov]

### Settled, and not coming

- **The status light cannot be driven.** No CoAP resource, and the device
  exposes exactly two settings within a one-byte selector. Probed across the
  whole selector byte, not a dozen of it. [3 Nov]
- **Wrist taps cannot be detected.** The Air handles them in firmware and sends
  nothing upstream. [3 Nov]
- **iOS is not possible**, outside the EU permanently. No iOS app can read
  other apps' notifications, except `AccessoryNotifications`, which Apple
  restricts to EU accounts on EU devices. [5 Nov]

[Unreleased]: https://github.com/srineere/air-bridge/commits/native
