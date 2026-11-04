# Air Bridge — handover

Written 3 November 2026, updated 4 November, on branch `native`. A commit hash
is deliberately not named here: it went stale within the day, twice.

Read this before touching the app. It is the state of play, the decisions that
are settled, and the traps that have already cost time.

## What the thing is

An Android app that relays phone notifications to a **Fitbit Air** band as
vibrations. The band has no notification support of its own; buzzing it is a
side effect of writing to its `/settings` endpoint over Golden Gate → DTLS →
CoAP. One secure session at a time, which is the single most important fact
about this codebase — see **Traps**.

`master` holds the original Flutter proof of concept. `native` is a ground-up
Kotlin + Compose rewrite, decided on 3 November 2026, and is where all work
happens. The two have **separate application ids** (`com.srineere.airbridge`
and `com.srineere.airbridge.dev`) and must not run at once.

## Shape of the app

Three tabs: `Health`, `Alerts`, `Device`. Health is first in the bar and the
app opens on it: the relay runs whether or not anyone looks at it, and the
readings are the half of this app you open it to see.

The Health column below is the **default** layout, not a fixed one: the tab
draws whatever the stored list says, and anybody can rearrange it.

```
Health
  a grid of tiles, laid out from a stored list  → Edit tiles
                                                → Goals
  Heart rate XL ─ today's trace, Measure    → Heart (today, zones, pills)
  Steps L       ─ ring, goal bars           → window, by week, when you walk
  Energy M · Nutrition M                    → Energy  → Burned against eaten
                                            → Nutrition → Food log → Log food
                                                                   → Saved foods
  Sleep XL      ─ stage bars, score         → score, hypnogram, stages, nights
  Vitals W · Activity S                     → Vitals  → HRV, breathing, skin,
                                                        blood oxygen
                                            → Activity → all of them → one
  Resting heart rate L ─ scatter, usual band → trend (histogram, 6 metrics)

Alerts
  [Relaying]  ──────────── the relay's on/off, as one switch
  Schedule              → pause (15 min / 1 hour / indefinitely) + quiet hours
  WHAT GETS THROUGH
    Calls               → the phone's own calls, its ring, its people
    Apps                → every app, its defaults, its alert kinds
    People              → everyone with a rule, across every app
  FROM YOUR ACTIVITY
    Buzz me at N steps  → its own pulse
    Nudge me each hour  → its own pulse
  RECENT (4)  Show all  → Activity

Device
  the band, battery, signal, Buzz, sync, permissions, widgets, about, accent
```

### The rule hierarchy

Resolution lives in `RelayRuleConfig.match()` (`RelayRulePolicy.kt`) and is the
heart of the app. Most specific wins:

```
a person's own rule  →  the app's own rule  →  the matching default
```

There are **four defaults**: `defaultPreset` (how apps buzz), `defaultCallPreset`
(how the phone rings), `defaultAppCallPreset` (how an app rings), and the ring
cadence. A call's default is chosen by whether the package *is* the dialer, which
is a runtime fact — `match()` takes a `dialer` argument rather than assuming
Google's.

Quiet hours and pause are checked **before** any of it, in
`RelaySilence.reason()`. A person's own vibration does not escape the quiet;
the Schedule screen says so outright, because the opposite is the reasonable
guess.

### Identity

A caller is a one-way hash (`RelayCallerIdentity.identityOf`). A phone number
hashes **without** the package, so the same person is one identity whichever app
they ring from; anything else hashes **with** it, because a name only means
something inside one app. This is why one contact can hold two rules — the
phone's and WhatsApp's — and why People merges them into one row while Calls
shows only the phone's.

Apps that name their caller are listed in `RelayContacts.NAMED_CALLERS`
(WhatsApp, Telegram). Any other app earns the same treatment automatically the
first time it names someone, via `RelayAppSelection.rememberNamesCallers`.

### The Health tab

Reads Health Connect, and writes one thing to it: food you log here. The
decisions that are settled:

- **Health Connect has no goals, and this cost an hour to establish.** It
  carries measurements only: no step target, no sleep target, no calorie
  target. The one `Goal` type in the API is `ExerciseCompletionGoal`, which
  belongs to `PlannedExerciseSessionRecord` — a structured training plan, not
  a daily figure — and whatever you set inside Google Health stays inside
  Google Health. So `health/HealthGoals.kt` holds our own, the Goals screen
  names the published source of each default, and every goal line on the tab
  is drawn from there. Do not go looking for a way to read Google's; there
  isn't one. The step goal shares its stored key with the buzz settings on
  purpose, because the band buzzes at that number and two keys would be two
  goals waiting to disagree.
- **Dense charts are drawn as ranges, not lines.** Eight hours of beats is
  about eighteen thousand readings against eight hundred pixels: as a
  polyline the strokes overlap into a solid block and the shape disappears
  into its own ink. `bandsOf` slices the span into about a bar a minute
  (capped at 120) and draws each slice from its lowest beat to its highest,
  which is the honest summary of what a pixel column holds. A slice with no
  readings is left out, so a band off the wrist is a gap rather than a line
  drawn through it. Under 400 samples the line comes back, because with
  fourteen beats the line *is* the data. The window control on the Heart
  screen is what makes a shorter span a finer chart rather than the same one
  stretched.
- **A session screen does not depend on the snapshot.** It looks the
  workout up by its start time through `SessionReader.session`, because the
  snapshot holds seven days and the history list reaches back a year: every
  workout older than a week used to open on "no longer in the window the tab
  holds". The snapshot is still the fast path when the session is in it.
- **A tile's colour is stored with the route it opened.** Without the route
  it leaked: opening a grey Activity tile left every screen visited
  afterwards grey, because the composition local was still holding the last
  tile tapped.
- **Activity history is a separate read.** The snapshot holds seven days
  because that is what the tile is about; `SessionReader.sessions` reads
  further back when someone opens All activities, and that list is
  deliberately plainer -- name, when, duration -- because a year of workouts
  is for finding one, not studying each.
- **Charts move and charts answer back.** `ui/ChartWindow.kt` holds both.
  A `ChartWindow` is a length *and* an offset, because resizing the view and
  moving it are different questions — "show me a fortnight" and "show me
  last September" — and a chart that always ends today can only answer the
  first. Stepping back calls `HealthState.widen`, which reads further and
  never narrows again. `Modifier.scrubbable` maps a touch to a point index
  and the charts draw a marker at it; the value lands in a `ChartReadout`
  **under** the chart, not in a tooltip under the thumb that is holding it.
  Two traps live here. An empty window and an unread window look identical,
  so every one of those screens checks `HealthState.loading` before saying
  "nothing recorded" — otherwise stepping back reads as "you did not walk
  that week" for as long as the read takes. And the window bar must stay on
  screen when its window is empty, or stepping back once strands you with
  no way forward.
- **A selection means the whole card follows it.** On Sleep, picking a night
  off the chart moves the score, efficiency, sleeping rate, hypnogram and
  stage breakdown onto that night, headers included — a score sitting beside
  a chart of a different night is two nights pretending to be one. Sleeping
  rate for an older night is not in the snapshot (the tab reads two days of
  beats), so it is read on demand through `SessionReader.heartSamples`.
  Charts indexed by time rather than by day take `scrubbableAcross`, which
  reports a fraction and lets the screen find the nearest reading; the gap
  between two beats is not the gap between two days.
- **Every chart has a scale.** `ui/ChartAxes.kt` is the axis toolkit —
  gutter, gridlines, baseline, goal line, zone bands — and the charts draw
  through it rather than inventing their own margins. Labels are drawn into
  the same Canvas as the series, not laid out as composables, so a
  thirty-point chart still costs one node. `axes` is optional throughout:
  passing it gives the chart its scale, leaving it out gives the bare shape,
  which is all a two-column tile has room for.
- **The tab is a grid, not a screen.** `health/Tiles.kt` holds the model —
  thirteen kinds, five sizes over six columns, a colour each — and the tab draws
  whatever `packTiles` makes of the stored list. Nothing in `HealthScreen.kt`
  is laid out by hand, which is the only reason Edit tiles can be a real
  screen rather than a settings page that rearranges nothing. A tile offers
  only the sizes it can honestly fill, and a tile at S is not the same tile
  shrunk: each size is written out, because the alternative is a hero with a
  9pt label in it.
- **A layout that cannot be read falls back to the default whole**, never to
  three tiles in the wrong order. A kind removed in a later version costs
  that one tile. There are tests for both.

- **`connect-client:1.1.0`, not the 1.2 alphas.** They demand AGP 9 and
  `compileSdk 37`, a toolchain jump this app has no other reason to make. 1.1.0
  already carries the background-read permission and the feature check.
- **All the arithmetic is in `health/HealthTrend.kt`**, with tests, and the
  screens hold none of their own. "Usual" is the interquartile range rather
  than mean ± SD, so one freak night cannot widen what counts as normal — there
  is a test that says exactly that. Change over a window compares thirds, not
  endpoints.
- **A week is the last seven days, everywhere, rolling.** Never Monday to
  Sunday, except in a month view. A calendar week is two days long on a
  Tuesday, so "this week" would mean something different every morning.
- **Resting is in no heart-rate zone.** Counting 58 bpm as Light turns a desk
  day into eight hours of exercise. Time between readings is credited to the
  earlier one, and a gap over five minutes is credited to nothing.
- **No composite scores beyond sleep.** `health/SleepScore.kt` scores last
  night on Duration, Stages, Consistency and Interruptions, and the screen
  never shows the total without the four parts. Every part is measured
  against **your own** nights, not a recommended figure, so 70 means "like
  your usual" and not "adequate sleep"; a part with nothing behind it is null
  and the score says it stood on three. Two things in there are worth knowing
  before changing it: the consistency component averages bedtimes **on the
  clock face**, because a plain mean of 23:30 and 00:30 is midday; and the
  usual band has a floor of five percent, because against a band of zero
  width a very consistent sleeper is marked down for five minutes. Both have
  tests. Readiness is drawn as a band around your own baseline, never as a
  number — the method is a rolling average against a smallest worthwhile
  change, which is a band, and inventing a 0–100 figure on top would be a
  clinical claim.

  **Reversed on 5 November 2026**, for readiness and for a biological age,
  on the owner's instruction after this reasoning was put to them twice. See
  `docs/ROADMAP.md`, which records what the reversal keeps: a total is never
  shown without its parts, every figure is measured against your own history,
  and a figure with nothing behind it stays null. The danger this rule names
  is real; the judgement is that the feature is worth it with those guards.
  The rule still stands for everything it does not name.
- **Distance and pace were empty because `READ_DISTANCE` was never granted**
  — granting it filled every session in, walks and racquet sessions alike.
  Not because nothing writes them. That took a log line to establish and it
  is worth keeping: a permission nobody granted and a metric nobody wrote
  look identical on screen. `HealthReader` logs the ungranted types on every
  read (`adb logcat -s AirBridge`), and the Activity rows say "distance not
  allowed yet" rather than printing a dash and leaving you to guess. If the
  Activity screen offers the permission sheet rather than making anyone
  hunt through Health Connect for it.
- **Blood oxygen is empty because Fitbit is not allowed to share it.**
  Established from the device, not guessed: every read permission is granted
  to us and `dumpsys package com.fitbit.FitbitMobile` shows
  `WRITE_OXYGEN_SATURATION: granted=false` while skin temperature and
  respiratory rate are `granted=true` — which matches the reader's own log,
  `oxygen=0` against 25 and 30 readings for those two. Seeing SpO2 inside
  the Fitbit app says nothing: that app reads it off the band directly. The
  empty-metric copy on every vital screen now points at exactly this.
- **Live heart rate needs Share heart rate on in Google Health.** With it off
  the CCCD write still succeeds and nothing arrives, and the transport waits
  `OPERATION_TIMEOUT_MS = 45_000` before saying so. The screen gives up at ten
  seconds and names the toggle. See `[[air-bridge-air-protocol]]`.
- **A measurement takes the relay's one slot**, through the same
  `begin()`/`onAir` path a notification uses, so alerts queue behind it rather
  than fighting for the session. `holdsBand` exists because the service object
  outlives its link — alive while starting, revived by ColorOS after a force
  stop — so "service alive" and "band taken" are different questions.

**The one write** is `health/HealthWriter.kt`: a `NutritionRecord` from the
Log food screen, marked as a manual entry, saved exactly as typed. When the
macros imply a different calorie figure from the one typed, the screen says
so and keeps both — the person read them off a packet, and silently replacing
one with the other would be the app deciding which of their numbers was the
lie.

**Reads are widened, never narrowed.** The tab loads thirty days; a detail
screen asking for ninety calls `HealthState.widen`, which re-reads once. A
ninety-day read is a superset, so there is no way back down and no need for
one. Per-session heart rate is not in the snapshot at all — `SessionReader`
reads one workout when you open it, because per-minute heart rate for every
session in a week is thousands of samples nothing on the tab draws.

The step goal polls every fifteen minutes from the relay service, and also
immediately when switched on. It records the fired day **before** sending the
pulse: a buzz can fail halfway, and recording only on success would retry every
quarter hour for the rest of the day. Settings live in `air_health.xml`, apart
from `relay_rules.xml`, because nothing there changes how a notification is
matched and so should not be able to break it.

## Where a reading comes from

Added 4 November 2026, and it fixed a bug that had been running the whole time.

**Nothing in the app filtered by `DataOrigin`.** Every `ReadRecordsRequest` and
every aggregate carried a time range and nothing else, and Health Connect reads
an absent `dataOriginFilter` as *every* origin. That is mostly what this app
wants — it is why the Health tab already works with any writer, Pixel Watch,
Garmin, Samsung or Polar, without a line of code, and why another app's food
appears in the log. For steps it was wrong.

**This phone writes its own steps.** Confirmed on the device, not assumed: the
enumeration logs two writers, `com.fitbit.FitbitMobile` and
`com.android.healthconnect.phone.je61914f407b237fb9a2a17063132118b`. The second
is the platform's **synthetic package name** for the built-in counter. Both
were being added together. Measured on 4 November: a seven-day average of
**6,402 unfiltered against 5,319 from the band alone**, about twenty per cent,
and **two of the seven days "met" the 7,500 goal on steps the band never
counted**. That is the early goal buzz the handover had listed as unwitnessed.

So steps — and nothing else — are read from chosen sources. `HealthSources`
enumerates who writes; `SourceChoice` is the stored answer, defaulting to
`Wearables`, every writer but the phone; the Goals screen names what it found
and lets anybody disagree. Three things in there are load-bearing:

- **The phone is recognised by `PackageManager` never having heard of it**, plus
  the documented `"android"` case. The synthetic name is device-specific and
  matches no installed app, so it cannot be recognised by name.
  `getCurrentDeviceDataSource()` would name it properly but is a framework API
  outside the pinned Jetpack library.
- **An empty filter is not an empty result.** Health Connect reads an empty
  `dataOriginFilter` as every origin, so "exclude the only writer there is" can
  never be handed to it as an empty set — it would return exactly what it was
  meant to exclude. `resolveSources` returns null for "do not filter" and an
  empty set for "nothing", and every read short-circuits on the second. There
  is a test that says so, and it is the test to keep.
- **Do not enumerate by reading records.** `readRecords(StepsRecord::class)`
  throws `IllegalArgumentException: startTime must be before endTime` **from
  inside the library's own converter** on this phone: Health Connect stores
  zero-duration step records and `StepsRecord`'s constructor rejects them, so
  there is nothing a caller can do. `AggregationResult.dataOrigins` answers the
  same question in one call and never builds a record. It cost a build to find
  and it will cost another if this is ever rewritten.

Everything else stays unfiltered, deliberately. Nutrition in particular: a
meal logged in MyFitnessPal is a meal eaten, and the food log should show it.

## Invariants — do not break these quietly

1. **The history file holds no names.** `RelayActivity.toMap()` is what leaves
   the app and is asserted by `RelayActivityTest`; `toStored()` is the on-device
   file and holds one field more, the caller hash. Names are looked up when the
   screen is drawn. Do not add a label to either.
2. **`startForeground` within five seconds.** `CalendarRelayService.onStartCommand`
   calls `enterForeground()` first, *before any bail-out*. `stopSelf()` does not
   excuse you; Android kills the process.
3. **Fail closed.** A corrupt or newer config decodes to defaults, never to
   "everything enabled".
4. **Quiet hours cannot start and end at the same minute** — `RelaySilence.init`
   requires it, because such a window never ends. The picker catches the clash
   before the requirement throws.

### What has been seen on the phone

Every Health screen has been walked on the device and screenshotted, twice
over: once when the tab became a tile grid, and again after the charts got
their scales. That second pass caught four things a compiler could not —
zone labels printed on top of the y axis, two goal labels in the same gutter
slot, "5.0k" where "5k" belongs, and the burned/eaten bars collapsing into
one track when they were put side by side. Look for that class of fault by
eye; none of it fails a build.

The food write is now proven too, and the two bugs it took to get there are
worth reading before touching it — see **The one write**. What is still
unwitnessed is deleting an entry: the path is the same permission and the same
`runCatching` as the save, and the identical code has been exercised against
the saved-foods store, but no real `NutritionRecord` has been removed from the
phone. Firing it costs somebody a genuine record, which is theirs to spend.

## Working on it

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

adb lives at `C:\Users\SRINEER\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
From Git Bash, device paths need doubling: `//sdcard/x.png`, not `/sdcard/x.png`.

Inspecting state on the phone, which is how most bugs here have actually been
found:

```bash
adb shell "run-as com.srineere.airbridge.dev cat shared_prefs/relay_rules.xml"
adb shell "run-as com.srineere.airbridge.dev cat shared_prefs/calendar_relay.xml"
adb logcat -s AirBridge
```

`calendar_relay.xml` holds the activity history and the learned naming apps;
`relay_rules.xml` holds the config (currently **version 6**).

## Traps that have already cost time

- **Two builds fight over the band.** The POC and the dev build each hold the
  band's single DTLS session; running both turns a 2-second read into 15 and
  logs `resetting DTLS` / `GattlinkSessionResent`. Uninstall one.
- **ColorOS blocks `adb shell pm grant`.** Permissions must be granted by hand
  on this phone.
- **Driving the UI by `input tap` is dangerous now.** App rows toggle on tap, so
  a stray tap changes a setting; two have slipped through in one session, and a
  mistimed tap opened the notification shade. Always screenshot, derive
  coordinates from *that* screenshot, and verify the result against prefs.
- **`io.open(path, "w", newline="\\n")` truncates before validating.** An invalid
  newline argument empties the file and *then* raises. It cost a file.
- **Formatting time:** use `DateFormat.getTimeFormat(context)`, never a format
  string. A hardcoded `h:mm a` printed "10:24 pm" onto a 24-hour phone.
- **The installed-app list is held for the process, in `InstalledApps`.** It is
  expensive — every launcher entry, a `getPackageInfo` and a `TelecomManager`
  call per app, and an icon to rasterise — so it is read once, off the main
  thread, and invalidated when something is *installed*, not when a setting
  changes. It used to hang off `RelayState`, which is replaced on every write,
  and that is what made the Apps screen stutter on every tap. Anything that
  moves it back, or reads it synchronously on a first frame, brings the
  stutter back silently: the screen still works, it is just slow again.
  `RelayState.apps` deliberately never waits — before the list lands it is
  empty, and `appsLoaded` is how a screen tells that apart from "nothing
  matched".
- **Icons are `ImageBitmap`, not Base64.** The old path PNG-compressed each
  icon and decoded it straight back, which was the Flutter channel showing
  through. Several of the `List<Map<String, String>>` returns left in
  `RelayContacts` and `RelayRecentCallers` are the same leftover.

## Design

The canvas is an Artifact: <https://claude.ai/artifact/EcNAsWKo5w3ciHwkswEDFc>
(private). Thirty-five boards plus design notes explaining *why* each screen is
the shape it is — the notes matter more than the pixels.

The working habit this project runs on: **design first, then build.** For
anything structural, update the board, show it, wait for a yes. Small fixes go
straight in. A board that has drifted from what shipped is worse than no board,
so the canvas is republished in the same sitting as the change.

Boards for screens that exist but were never drawn: the per-app caller list, and
a person's rules across apps. Of the Health boards, only `Widgets` is still
unbuilt; `Setup` is the tab's own not-connected state, and `Live` is the
measuring state of the heart rate tile.

Two places where the build deliberately departs from the board. Edit tiles
says arrows rather than drag: reordering a packed grid of five sizes by drag
is a frame-rate problem and a target-size problem, and two arrows say the
same thing in a way that also works for anyone who cannot drag. And the Log
food screen has no macro targets, because nothing in the app stores one —
the rows show grams and share of calories instead of inventing a goal.

The canvas also carries an `Option B` row — a Whoop-style take on Health, kept
for comparison rather than built. Option A won the tab; Option B's rings
survive as widget designs.

## What is not built

- **Widgets.** Designed, with a gallery screen in the app, but no Glance
  implementation behind it.
- **"Pair a different band."** On the Device board, never built.
- **Confirming the goal pulse on the wrist.** The hourly nudge is now proven end
  to end: the device prefs hold `nudge-*` entries and Recent names them. The
  goal pulse is still unwitnessed — the poll reads the steps and writes
  `goal_fired_day`, but no `goal-*` entry has been seen, because it takes
  crossing 7,500 on a day the relay is up.
- **Knowing a food log is complete.** The screen exists; the certainty does
  not. The reader counts entries a day and the Energy screen says when today
  has fewer than usual, which is as far as the data can go: nothing can tell a
  genuine fast from an unlogged dinner.
- **Per-day quiet windows.** The config holds one window for every day; per-day
  would be a config change, not a screen.
- **Telegram end to end.** The identity path is verified from Telegram's source
  and the contacts picker works, but no real Telegram call has matched a rule on
  a device yet.

- **The seven features chosen on 5 November** — readiness, biological age, a
  morning moment, a journal, a sleep bank, export, and finishing day-wise
  exploration. They have their own document, `docs/ROADMAP.md`, because two of
  them overturn the no-composite-scores rule above, and that reversal needed
  somewhere to be written down rather than discovered in a diff.
- **Four metrics Google Health already writes and this app does not read.**
  Built once on 4 November 2026 and backed out the same day, deliberately, to
  keep the source-filter change alone. The groundwork is established and worth
  not re-establishing: every type exists in `connect-client:1.1.0`, so no
  version bump is needed.
  - `Vo2MaxRecord.vo2MillilitersPerMinuteKilogram` — no aggregate, so read the
    records. An estimate something recomputes every week or two, **not one a
    day**: a thirty-day window holds two or three readings, and anything
    drawing it has to survive that. Belongs on Vitals.
  - `FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL` — a daily aggregate. Must take
    the same origin filter steps do, or the phone's stairs land on the band's.
    Belongs with steps.
  - `BodyTemperatureRecord.temperature.inCelsius` — no aggregate, read the
    records. **Not skin temperature**: skin temp is a delta from your own
    baseline and body temp is an absolute somebody measured. Showing either as
    the other is wrong by about thirty degrees. Belongs on Vitals, below skin.
  - `SpeedRecord.SPEED_AVG`/`SPEED_MAX` (a `Velocity`, `.inKilometersPerHour`)
    and `StepsCadenceRecord.RATE_AVG`/`RATE_MAX` (plain doubles) — both are
    series records with aggregates, so one call each on `SessionReader.read`
    rather than averaging samples by hand. They are per-session and belong on
    the session screen, in a third row drawn only when one of them exists: a
    band with no GPS writes neither, and a row of three dashes says less than
    no row.

  Each needs its `READ_*` in both `HealthConnect.readPermissions` and the
  manifest. Granting them is by hand on this phone; `pm grant` is blocked.

## Widgets

Eighteen of them are built and on the home screen: all thirteen Health kinds
and five band faces, in `widget/`. Rings, dials, paper, type-alone, the three
with buttons, and stacks are designed and not built.

**The shape of it.** Five `AppWidgetProvider`s, one per cell size
(`WidgetSize`), all backed by one `AirWidget : GlanceAppWidget`. Each has
`android:configure` pointing at `WidgetConfigActivity`, which Android
launches on drop and which *is* the catalogue; nothing is placed until it
returns `RESULT_OK`. `WidgetStore` keeps one row per `appWidgetId` in
`air_health`, beside the tile layout, because a widget's colour is its
tile's colour and two stores could disagree about that.

**Three things that cost real time, and will again if they are undone:**

- **A widget provider has to be `exported="true"`.** Declared `false`, all
  five appeared in `dumpsys appwidget` and never once in the launcher's
  picker: the system resolves `APPWIDGET_UPDATE` against installed receivers
  and skips the ones it cannot see. Symptom and cause look unrelated.
- **Every provider needs a `previewImage`.** Several launchers leave one out
  of the picker entirely without it, and the fallback -- inflating
  `initialLayout` -- is an empty loading view for a Glance widget.
- **Redraw by id, never `updateAll`.** `updateAll` resolves through Glance's
  own receiver index, which is built once a receiver has run in this process.
  Right after the configuration activity places the *first* widget it finds
  nothing, and the widget sits there still showing the "tap to choose" face
  it composed before the choice was stored. `liveIds()` asks the launcher,
  which always knows.

**`loadHealthData`** is a plain function in `HealthState.kt` rather than a
method, so the widgets and the tab share one read. Two readers would be two
sets of arithmetic drifting apart, and a tile whose trend differs from its
widget's is the kind of bug nobody reports because nobody believes it. The
widgets ask for 14 days, not the tab's 30: the longest window any face draws
is a fortnight.

The dependencies were already in `app/build.gradle.kts` (`glance-appwidget`
1.1.1, `glance-material3`, `work-runtime-ktx`).

**What Glance can and cannot draw.** Read from the 1.1.1 jars, not from
memory, because three of these contradict what anyone would assume:

- `CircularProgressIndicator(GlanceModifier, ColorProvider)` takes **no
  progress parameter**. It is the indeterminate spinner and nothing else, so
  there is no determinate ring at any size. A single ring costs a Bitmap just
  as much as three concentric ones. `LinearProgressIndicator` has a `Float`
  overload and is the only determinate component.
- `TextStyle` carries colour, size, weight, style, alignment, decoration and
  family. There is **no letter spacing**, and `FontWeight` is exactly Normal,
  Medium and Bold. Every tile head in the app is bold, uppercased and tracked
  at 1 sp, so a widget kicker sits tighter than the tab's and cannot be made
  to match without baking it into a bitmap.
- Weight is an **equal share only** -- no fractional weights and no
  percentage heights, so every bar height is computed in Kotlin in dp from
  `LocalSize`.
- `GlanceModifier.cornerRadius` is API 31+ and `minSdk` is 28, so every
  rounded surface needs a shape drawable on 28-30.

A chart made of rectangles in a Row composes natively; anything curved -- a
ring, a smooth sparkline -- is an Android Canvas rendered to a Bitmap and
shown as an `Image`. Both are legitimate; the designs say which they need.

**The band widget is the one only this app can do**, and it forced the
decision the rest inherit: the battery is always the last reading *with its
age*, never a live number. Asking the Air costs its one secure session and
holding that session blocks the Google Health sync, so a widget that
refreshed every half hour would quietly cost a day of readings.

**Widgets must read `LightExtras` on a light ground.** The dark health
colours are not merely dim there, they are unusable: `steps #5BD6A8` is
1.81:1 on white. The light values already exist and pass -- steps 4.51:1,
activity 4.82:1, heart 4.99:1. Do not mint a third set; a proposal to do so
measured *worse* than what is already shipping.

Two things a widget cannot carry, decided rather than overlooked: the live
measuring tile (no animation, no per-second push, and the band cannot be held
open from a home screen) and the hypnogram (sixty offset boxes would compose
and would be a grey smear under 150 dp).

**Three pick sheets** sit on the canvas after those four families, and they are the ones to
read first: `WidgetPickTiles` (all thirteen Health kinds at Square and Wide),
`WidgetPickBand` (twelve band and relay widgets, including the three with
buttons) and `WidgetPickStyles` (seventeen faces across four borrowed
grammars -- rings, dials, paper and type alone). Each face carries an id so a
decision can name one.

**A widget's colour is the tile's colour**, read from the `air_health` tile
store, not a setting of its own. The widget config therefore has no colour
row; changing a tile under Edit tiles changes its widget too. Sleep stages
and energy in-against-out keep their own palettes, as on the tab.

### The catalogue, and stacks: what Android actually allows

Both ideas are buildable. Checked against `android-36/android.jar` and the
Glance 1.1.1 jars, not assumed:

- **A few standard sizes, with the choice made in-app.** Declare one provider
  per cell size with `android:configure` pointing at an activity. Android
  launches it on drop and the widget is not placed until it returns OK, so
  the catalogue *is* the add flow. `WIDGET_FEATURE_RECONFIGURABLE` lets a
  long-press re-open it, `WIDGET_FEATURE_CONFIGURATION_OPTIONAL` allows a
  sensible default without one, and `WIDGET_FEATURE_HIDE_FROM_PICKER` can
  keep any per-family providers out of the drawer. **All three are API 31+**,
  and `minSdk` is 28: on 28-30 there is no re-open from the launcher, so the
  app needs its own "widgets you have placed" list to edit them.
- **Drawer previews.** `previewLayout` is API 31+, `previewImage` is the
  fallback. `AppWidgetManager.setWidgetPreview` (API 34+) can push a live,
  themed `RemoteViews` into the picker, so on a recent phone the drawer entry
  can be drawn in the user's own accent.
- **A stack has three honest shapes, and Glance only supplies one of them.**
  There is `LazyColumn` and `LazyVerticalGrid` in
  `androidx.glance.appwidget.lazy` and **no LazyRow and no pager**, so a
  horizontal swipe is not available in Glance's own composables. The options
  are: (1) `LazyColumn` with each item the full widget height -- vertical
  scroll, no snapping, pure Glance, cheapest; (2) dots plus an
  `actionRunCallback` that bumps a stored index -- tap to advance, works on
  28, but the tap target fights tap-to-open; (3) a real swipeable
  `StackView` or `AdapterViewFlipper` -- both are annotated
  `@RemoteViews.RemoteView`, so both are legal inside a widget -- fed by a
  `RemoteViewsService` whose factory composes each page with
  `GlanceRemoteViews.compose()` (`@ExperimentalGlanceRemoteViewsApi`). Option
  three is the one that matches what a launcher stack feels like, and it is
  the only one that leaves Glance's normal path. **It is also the one to
  build**: it is the only option that leaves the tap free to open a screen,
  which every other widget on the canvas does.
- A stack refreshes as one widget, so **the dearest member sets the update
  cost for all of them** -- which is another reason the band card in a stack
  shows a stored battery and never asks the Air.

## Theming, and why the neutrals are no longer constants

A ground is a choice now -- four in each mode -- and that broke a premise the
colour file was built on. Twenty neutrals were declared as hex values tuned
against the single near-black that existed when they were written, and a
constant tuned against one ground is a wrong answer on every other. Two were
measurably broken the moment Dusk was offered: `sleepAwake #2E2E42` is 1.45:1
on Ink and 1.25:1 on Dusk, an invisible stage of the sleep chart, and
`DarkDisabled #6E7E87` falls from 4.57:1 to 3.95:1, under AA.

So `Scheme.kt` derives the lot from `ground.base` and `ground.ink`. The ones
whose job is to be *just* visible are mixed to a contrast ratio rather than a
fixed amount -- `mixToContrast` binary-searches the mix until it hits the
target. The six health colours do not move at all: a steps green that drifts
with the ground is a steps green nobody can learn.

Three traps, all of them found by measuring rather than looking:

- **The accents collided with the data.** Three of the six were the same hex
  as a health colour: the green accent *was* `extras.steps`, the amber *was*
  `extras.hrv`, the rose a shade off `extras.heart`. On the one tab where
  colour carries meaning, the chrome and the readings were the same colour.
  The five replacements sit in hues the charts do not use, and a test asserts
  no accent ever equals a health colour again.
- **True black cannot lift.** Material conveys depth in dark mode by lifting
  the surface, and a lift off `#000000` is either invisible or relights the
  pixels the ground exists to turn off. `Ground.liftsTiles` answers no for
  Pitch, tiles there are a border and nothing else, and `Edges.on` raises the
  softest border setting so a card is never left with neither fill nor line.
- **Material You would have overwritten the ground.** `dynamicDarkColorScheme`
  returns surfaces as well as an accent, so taking it wholesale meant "true
  black" plus "match my wallpaper" silently stopped being true black. The
  wallpaper now contributes one colour -- its primary, used as a hue -- and
  everything else is ours.

`lift` mixes in sRGB component space on purpose, NOT with Compose's `lerp`,
which interpolates perceptually. A 0.14 perceptual lift off true black lands
at 1.05:1. The test suite caught that one.

A custom accent is clamped, never rejected: `clampToGround` keeps the hue and
moves lightness until it clears 4.5:1, because the accent draws 1px chart
strokes and 11px labels. Refusing the pick would be rude and a "use anyway"
escape would be dishonest.

Anything asking `isSystemInDarkTheme()` to choose a colour is a bug; use
`MaterialTheme.isDark`, which reads the ground actually in force. The activity
badges asked the system and drew dark inks on a light card whenever someone
forced Light on a dark phone.

## The food log

Tapping Log on Nutrition opens the log, not a form. What anyone wants first
is to see what is already down for today; adding is one tap from that rather
than instead of it. Entries group by meal, each group folds, and every entry
can be opened for its macros, kept as a saved food, or deleted.

Deleting goes through Health Connect and takes the record with it, whichever
app wrote it. It needs no permission the save did not already need: Health
Connect counts delete as writing. `FoodEntry` carries `id` for exactly this.

Logging is two steps, not one screen. "Start from" offers something recent,
a saved food, or typing it; whichever you pick lands on the same form, the
first two with the figures already in. It was one screen with meal pills,
two tabbed lists and a five-field form all at once, and nothing on it said
which part you were meant to begin with. Nothing is written from a list: the
figures pass through the form, where they can still be corrected.

Saved foods are Air Bridge's own, in `SavedFoods`. They can be edited and
deleted on their own screen; renaming one is a remove and a save, because
saving under the new name alone would leave the old one behind. They have to be: Health
Connect has one food record, `NutritionRecord`, which is a thing eaten at a
time. There is no library, no favourites, nothing to pick from -- checked in
the 1.1.0 jar, not assumed. Three screens say so in as many words, because
the obvious thought on not finding them in Google Health is that something
broke.

The store is separated by unit and record characters rather than commas or
pipes: a food is named by a person, and "Rice, dal and curd" is an ordinary
name. Any printable delimiter is eventually inside one.

## The one write

`HealthWriter.logFood` is the only thing this app writes, and it took the app
down the first time anybody pressed Save. `NutritionRecord` validates its own
times in its constructor and requires start strictly before end; the record
was built as an instant, start and end the same, on the reasoning that a thing
eaten has no duration. It threw before the insert was attempted, on the main
thread, outside the `runCatching` that only ever wrapped the insert.

So: a meal lasts one second, never crossing local midnight (a 23:59:59 entry
is logged as the last second of its own day rather than counted in two days'
totals), and the record is now built inside the catch. A library constructor
that rejects its own arguments is a refusal like any other and belongs in the
same message as "Health Connect refused the entry".

All of that is tested on the JVM, the constructor included -- the thing that
broke was argument validation, which is plain Kotlin and does not need a
phone to exercise. The write is now proven end to end as well: an entry saved
from the app on 2026-11-04 is in Health Connect and listed on the Nutrition
screen.

The second bug was worse than the first, because it looked like the write
failing when the write had worked. `refresh` ran on whichever screen asked
for it, and saving refreshed and popped the screen in the same breath: the
scope died mid-read and a half-cancelled snapshot -- `eaten=0 burned=0` --
was stored over the real one. `HealthState` now reads on a scope of its own
and clears `loading` in a `finally`, since a flag left set refuses every
later refresh and the tab never loads again.

The lesson worth keeping: never hand a long read the coroutine scope of a
screen that is about to leave.

## What a trend is

`trendOf` in `health/HealthTrend.kt`. `change` is the newest third of the
window against the oldest third -- not this week against last, and not first
reading against last: one odd night at either end would otherwise set the
whole direction of travel. It is in the metric's own units. Steps are the one
conversion, shown as a share of the window average, because "318 fewer steps"
means nothing without knowing what a usual day is.

Three of them are derived rather than read, in `HealthState`, because Health
Connect has no such record to ask for: energy (eaten minus burned, days with
both halves), nutrition (kilocalories eaten) and activity (minutes a day,
rest days left out rather than zeroed).

Two tiles have no trend and should not get one. Heart rate is today's trace,
which means nothing a week later; Vitals is a count of how many readings sit
in range, which is not a quantity that moves.

`sampleHealthData` builds its trends from the same two helpers the real path
uses (`balanceReadings`, `toDailyMinutes`), which is why they are `internal`
rather than private. They were not, once, and the edit screen's previews
showed no chips on energy, nutrition or activity while the tab behind them
showed all three -- a preview that computes differently is a preview that can
disagree with the thing it previews.

`ChangeChip` narrows as the tile does: S shows the arrow alone, M adds the
figure, W and wider add the unit. The heading's name gives way before the
chip does -- it carries the weight in that Row -- because a chip squeezed
until its figure drops out is an arrow pointing at nothing.

One switch on the edit screen turns the lot off, and `TileHead` gates every
trailing slot on it, so a tile cannot keep its chip by accident.

## The tile grid

Six columns, because six is the smallest number that lets one, two and three
tiles share a row on the same gutters. The sizes are S (2 columns), M (3), W
(4), L (6) and XL (6 across and two rows high).

W exists for one reason: S beside M is five of six, and that last column was
dead air no arrangement could use. S beside W is a whole row. Anything added
later that is meant to pair with another size has to fall out of the same
arithmetic, and any size that can share a row must be one row high, or the
grid goes ragged. Both are asserted in `TilesTest`.

Each kind names itself twice. `title` is the full name; `short` is what S
shows, because two columns cannot hold "Resting heart rate". Chopping at the
first space used to do this and gave "Blood" for blood oxygen and "Skin" for
skin temperature, so the ones that break are written out.

Heart rate and resting heart rate are two kinds, not one tile with two faces.
One is a live trace that means nothing a week later; the other is a single
figure a day inside a thirty-day trend. They were one tile for a while, and
the giveaway was that tapping it opened a screen about the other.

## Measuring takes the tile over

A live reading is the one thing on the Health tab that Health Connect cannot
answer, and the only thing on it that costs something while you look: holding
the subscription holds the band's single session, so the wrist cannot be
buzzed and alerts queue behind it.

It belongs to the heart rate tile, not the resting one. Resting heart rate is
a figure read while you slept; a live trace is a different question with the
same organ behind it, and the button sat on the wrong tile until 2026-11-04.

While a reading runs, the XL heart tile replaces today's trace with the live
one -- same card, same place, different question. It wears a tint, stops
being a link (it carries a Stop button, and a card that both stops the thing
and navigates is one you cannot tap confidently), and says what it costs. A
failure takes the tile over too: the answer to the tap belongs where the
number would have been.

`rememberMeasure()` is called ONCE for the grid and passed down. Each holder
frees the band when it leaves composition, so a second holder in a tile would
stop the first one's reading. The edit screen passes null, which is also how
its preview knows to draw a dead button.

## The heart screen's window

Four pills — 1h, 4h, 8h, Today — inside the card, above the chart they govern,
which is where every other window control on this tab sits. They arrived as a
new kind of control floating loose on the screen and were moved for that
reason alone: a tab with two idioms for the same job has one idiom too many.
Eight hours rather than twelve is the default because that is what the owner
asked for after living with both.

The clock range the window actually covers — "10:22 – 17:57" — is the chart's
**caption**, under the hour ticks. Above the chart it sat between the pills
that set it and the figures that summarise it, belonging to neither, and read
as a third heading. A reading taken by scrubbing lands below that again, so
the column runs: what you asked for, the summary, the chart, what it spans,
what your finger is on.

## The default layout

`defaultTiles` is the layout this app's owner actually settled on, adopted as
the default on 2026-11-04: heart rate XL, steps L, energy M beside nutrition
M, sleep XL, vitals W beside activity S, resting heart rate L. Today first,
then the day as it is being spent, then the night that preceded it.

Two properties are asserted rather than left to luck, because they are the
reason it is a good first screen: every row fills exactly, and every size is
used at least once. A new size that does not divide into six will fail that
test, which is the point of it.

## Editing a tile

The tile itself is the way in. Tapping its body opens a panel holding size,
colour and remove; the tile keeps only the two arrows that reorder it.

It was not always so, and the two things that were wrong are worth not
repeating. Removing lived behind the colour swatch, which looks like a colour
and nothing else, so the way off the tab was effectively hidden. And size was
a chip that cycled, which meant five taps to undo one and no way to see what
a tile even offered. Four controls never did fit across two columns.

## Edit tiles shows a made-up person

`sampleHealthData()` in `ui/SampleHealth.kt`, never your own readings. The
question that screen asks is what a size can hold, and real data answers it
badly: an empty tile might mean the size is too small or might mean you did
not wear the band, and nothing on screen says which. The sample slept, walked
and ate, so every tile is full and the shape is the only thing varying. It is
fixed rather than random, because two tiles being compared have to be drawn
from the same numbers twice.

The previews also swallow every touch before the tile sees it. The heart tile
carries a Measure button, and on that screen a stray tap would have started a
real reading.

## The two buzzes the app raises itself

The step goal and the hourly nudge are the only pulses with no notification
behind them. They are written once, as `StepBuzzRows` in `AlertsScreen.kt`, and
drawn by both the Alerts tab and the Steps screen: two copies of a switch are
two chances for the screens to disagree about what the band is about to do.

Each is recorded like any other buzz, with `commandId = "goal-<at>"` or
`"nudge-<at>"` and no package. That null package is why they read as "Test
vibration" for a while — `RelayState.label` only knew how to name packages.
`label(entry)` reads the prefix instead, and `badgeFor` gives each its own
badge, so Recent tells the three apart.

Both go through the relay's link, so neither fires while relaying is off. The
rows say so, but only when it is off: a caveat that is always on screen stops
being read.

## If you change the stored config

`RelayRuleStore` is versioned and currently at 6. Add a field by: widening the
`require(version in 2..N)`, defaulting the field from the old value in `decode`,
bumping the number in `encode`, and writing a test that an old config decodes to
the same behaviour. Version 5's migration copies the single old call default into
both call defaults; version 6's copies the notification default into the step
goal and the hourly nudge, which until then had no pulse of their own. Either
way nobody's band feels different after an update — that is the shape to copy,
and the reason each has a test is that the way such a migration fails is
silently. Note that the "newer schema is rejected" test has to move up with the
version, or it starts asserting against a number the decoder now accepts.
