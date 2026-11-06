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
