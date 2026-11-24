# Cuff — handover

Written 3 November 2026, updated 4, 5 and 6 November, on branch `native`. A
commit hash is deliberately not named here: it went stale within the day,
twice.

Read this before touching the app. It is the state of play, the decisions that
are settled, and the traps that have already cost time. For how the thing is
built rather than why it is the way it is — the module layout, the read path,
the widget model, the threading — see `docs/DESIGN.md`, which is written for
somebody arriving at the repository cold.

## What the thing is

An Android app that relays phone notifications to a **Fitbit Air** band as
vibrations. The band has no notification support of its own; buzzing it is a
side effect of writing to its `/settings` endpoint over Golden Gate → DTLS →
CoAP. One secure session at a time, which is the single most important fact
about this codebase — see **Traps**.

`master` holds the original Flutter proof of concept. `native` is a ground-up
Kotlin + Compose rewrite, decided on 3 November 2026, and is where all work
happens. The two have **separate application ids** (`com.srineere.cuff`
and `com.srineere.cuff.dev`) and must not run at once.

## Shape of the app

Three tabs: `Health`, `Alerts`, `Device`. Health is first in the bar and the
app opens on it: the relay runs whether or not anyone looks at it, and the
readings are the half of this app you open it to see.

The Health column below is the **default** layout, not a fixed one: the tab
draws whatever the stored list says, and anybody can rearrange it.

```
Health
  a grid of tiles, laid out from a stored list  → Journal
                                                → Goals
                                                → Edit tiles
  Heart rate XL ─ today's trace, Measure    → Heart (today, zones, pills)
  Steps XL      ─ ring, goal bars           → window, by week, when you walk
  Energy M · Nutrition M                    → Energy  → Burned against eaten
                                            → Nutrition → Food log → Log food
                                                                   → Saved foods
  Sleep XL      ─ stage bars, score         → score, hypnogram, stages, nights
                                                       → Sleep bank
  Vitals W · Readiness S                    → Vitals  → HRV, breathing, skin,
                                                        resting, fitness age
                                            → Readiness → parts, band, chart
  Activity L    ─ the week's workouts       → all of them → one

Alerts
  [Relaying]  ──────────── the relay's on/off, as one switch
  Schedule              → pause (15 min / 1 hour / indefinitely) + quiet hours
  WHAT GETS THROUGH
    Calls               → the phone's own calls, its ring, its people
    Apps                → every app, its defaults, its alert kinds
    People              → everyone with a rule, across every app
  FROM YOUR ACTIVITY
    Morning report      → when it goes, and what it says
    Buzz me at N steps  → its own pulse
    Nudge me each hour  → its own pulse
  RECENT (4)  Show all  → Activity

Device
  the band, battery, signal, Buzz, sync, widgets, appearance, permissions,
  export, about
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

There is now one exception, and only one: `RelaySilence.callsStillRing`, a
nested sub-option under quiet hours. A window exists so a night of messages
cannot wake you, and that is a different wish from not wanting to know the
phone is ringing. A **pause** is untouched by it — a pause is an answer given
by hand a few minutes ago and outranks a window set months back, which is also
why `reason()` tests the pause first and returns before it looks at the clock.

`RelaySilence.nextChange()` is the other half of that file worth knowing. The
relay used to wake every minute for the rest of its life to re-word its own
notification; nothing there changes without a boundary to change at, so it now
sleeps until the pause ends or the window's next edge. Only the wording rides
on it — every decision about whether to buzz asks `reason()` against the clock
at the time, so a wake-up that arrives late leaves the band correct and the
text stale.

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
  seventeen kinds, of which fifteen are offered on the tab, five sizes over six
  columns, a colour each — and the tab draws
  whatever `packTiles` makes of the stored list. Nothing in `HealthScreen.kt`
  is laid out by hand, which is the only reason Edit tiles can be a real
  screen rather than a settings page that rearranges nothing. A tile offers
  only the sizes it can honestly fill, and a tile at S is not the same tile
  shrunk: each size is written out, because the alternative is a hero with a
  9pt label in it.
- **A layout that cannot be read falls back to the default whole**, never to
  three tiles in the wrong order. A kind removed in a later version costs
  that one tile. There are tests for both.
- **Two kinds are widget faces and nothing else.** `TileKind.onTab` is false
  for `Stages` and `SleepBank`, so `unusedKinds` never offers them and the
  catalogue still draws them. A home screen shows one card all day and a tab
  shows a dozen at once: the Sleep tile already carries the night there, and a
  tab holding all three says one thing three times.

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
  empty-metric copy on every vital screen now points at exactly this, and the
  blood oxygen **tile kind has been removed** — a tile that could only ever be
  empty is worse than a tile that is not offered. `READ_OXYGEN_SATURATION` is
  not in `HealthConnect.readPermissions` or the manifest either.
- **Live heart rate needs Share heart rate on in Google Health.** With it off
  the CCCD write still succeeds and nothing arrives, and the transport waits
  `OPERATION_TIMEOUT_MS = 45_000` before saying so. The screen gives up at ten
  seconds and names the toggle.
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
Widened on 6 November from steps alone to every reading that can be
double-counted — see **One place a reading is counted from**, below, which is
the part to read if you are changing any of this.

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

## One place a reading is counted from

6 November 2026. Only steps were filtered by who wrote them, and About had
just begun telling people that a Pixel Watch or a Garmin fills these screens
too — which makes a second writer likely rather than hypothetical. Sleep,
workouts, calories and food were still read from every writer.

**The obvious fix would have deleted data.** `stepWriters` already enumerated
who writes steps, and reusing that set for weight is wrong: a smart scale has
never written a step, so the filter would drop somebody's entire weight
history while trying to stop their steps being counted twice. So writers are
enumerated **per reading**, each cached on its own, in
`HealthSources.writersOf`.

`health/Written.kt` is the list and the reasoning. Six readings are filtered:

- **Additive** — steps, distance, calories burned, food. Two writers inflate
  the total.
- **Duplicating** — sleep, workouts. Two writers put the same night, or the
  same run, on the screen twice.

Everything else is deliberately absent and the absence is the point. Heart
rate, HRV, resting rate, breathing, skin temperature, VO2 max and weight are
point measurements: a second source there is more readings, not a wrong
number, and filtering could only ever remove data.

`SourceChoice.Only` needed the same protection in a different shape, in
`HealthReader.originsFor`. Somebody who picks their band to settle a step
argument has said nothing about food, and if the band has never written a meal
then honouring that literally would empty the food log. It applies only where
the chosen app actually writes that reading, and stands aside where it does
not — a preference, not an instruction to discard everything else.

On the phone this was built against it changes exactly one figure, which is
the right outcome. The enumeration logs what it finds:

```
steps     phone + com.fitbit.FitbitMobile
sleep     com.fitbit.FitbitMobile
workouts  com.fitbit.FitbitMobile
calories  com.fitbit.FitbitMobile
food      com.fitbit.FitbitMobile, com.srineere.cuff.dev
```

Food is the one worth looking at twice: this app is a writer there, and under
"Devices only" it survives, because the rule excludes **phones** rather than
everything that is not a band. Had it excluded by any other test, the food log
would have deleted itself.

Nutrition stays visible across writers for the reason it always did: a meal
logged in MyFitnessPal is a meal eaten, and the food log should show it. What
changed is only that a *second* nutrition writer can no longer double the
day's calorie total.

That line divides nutrition in two, and the division is the thing to hold on
to: **a list shows every writer, a sum comes from one.** The food log and the
meal count are lists. The day's calories, the three macro dailies and the
three figures for today are sums, and all of them take `foodOrigins`. For a
while only the first two did, which is the worst of both: the macro rows on
the Nutrition screen were drawn from every writer while the calorie total
above them came from one, so a day with two food apps showed rows that did
not add up to their own heading. There is a comment at the log's read saying
not to hand it the same set, because doing so would hide somebody's lunch.

Workouts are the one list that *is* filtered, and they needed a third rule
to be filtered safely. The seven-day tile and the full history used to
disagree whenever two apps wrote the same run, which is the bug; but
`resolveSources` under "Devices only" answers with the non-phone writers,
and on a phone whose only workout writer is the phone that is the empty set
— which `records` reads as "return nothing". Filtering the history naively
would therefore have deleted somebody's workouts from the screen to avoid
showing one of them twice. `SessionReader.workoutOrigins` folds the empty
answer back into null, so the filter removes duplicates and never empties
the page. **A sum that filters to nothing is honestly zero; a list that
filters to nothing is a lie.**

## The device a record came off

Also 6 November. Health Connect attaches a device to every record —
`Metadata.device`, carrying a type from a fixed enum (watch, fitness band,
ring, chest strap, scale, phone, headset, display), a manufacturer and a
model — and nothing in this app read it.

What was there instead was `deviceGlyph`, matching substrings of a model name
against a list of words somebody had made up — versa, forerunner, vivoactive —
which was brittle for anything on the list and useless for everything else. It
had one caller and that caller passed a constant, so the watch and phone
branches could never run. The comment above it described an intention rather
than the code, which is the part worth not repeating.

So `health/RecordedBy.kt`, carried on `Session` from all three places sessions
are built, with a glyph keyed on the type. The name comes from the model, with
its maker in front only where the model does not already carry it — a Pixel
Watch 2 does not want "Google" before it and a Forerunner does.

**All three fields are optional and most writers fill none of them**, so this
is null far more often than not, and a null draws nothing. "Unknown device"
would be the screen inventing a fact about hardware, which is the thing this
was added to stop doing.

And the Device tab now **asks the band its name** (`ui/BandName.kt`) rather
than reading "Fitbit Air" off a constant. The band has always known its own
name over GATT; the heading never asked. Asked once and kept: the answer costs
a session, the band grants one at a time, Google Health wants that session for
your steps, and a name is the one band fact that cannot go stale. It is last
in the chain, behind the two readings somebody actually opened the tab to see.

## What "last night" means, and the question `dayLabel` answers

Two fixes on 6 November, a day apart in reasoning and the same fault
underneath: a heading about one night printed over another.

**Last night is the night that ended this morning, and nothing otherwise.**
`HealthData.lastNight` was whatever night was newest, which is right for most
of the day and wrong in the hours where it matters. Health Connect files a
sleep session under the day it **ended**, so at two in the morning the newest
night is the one before — and every screen headed "last night" was showing it:
a sleep score, a sleeping heart rate, four stages and a set of vitals for a
night you had already got up from, while the night you were actually in had
not been written yet. It is quietly wrong rather than visibly wrong, which is
worse, because the figures look current.

Being null is not an error state here. It is the honest answer between going
to bed and the band syncing, and `HealthData.nightPending` tells the two
silences apart: before the morning is over, no night means one is coming;
after it, there was not one. The hour the morning gives up is
`MorningMoment.LATEST_HOUR`, shared rather than copied, so there is one
definition of when a morning stops being a morning. The vitals wait together,
because every figure on that screen is measured while you are asleep.

Readiness has carried the morning it describes since it was built — the field
is there and the comment above it warns about exactly this — and nothing ever
checked it. It checks now.

The widgets inherit all of it, because `lastNight` lives on `HealthData`
rather than on the tab: the sleep ring leaves the home screen overnight and
comes back when the night does.

**And four screens asked a different question than the one `dayLabel`
answers.** `dayLabel` has always been right — it compares the night's date to
today and says "Last night" only when they match. Four callers wrapped it in a
condition of their own and overrode it. Two asked whether the night was the
*newest* one; two asked whether the chart's *window reached today*. Neither is
the same question. Every one of them now calls `dayLabel` and stops.

HRV is the one exception, because its label carries a unit: it says "last
night, RMSSD" only when the newest reading was written today, "latest reading"
when the window reaches today but that night has not arrived, and names the
day otherwise.

`LastNight` is now `NightDetail`. It draws whichever night the finger is on,
has done since the chart became scrubbable, and took a `newestDay` parameter
to decide a label it no longer writes. A composable called `LastNight` is how
"last night" ended up printed over a night that was not last night; the name
was the bug's first draft.

## A day's sleep, not a session's

Health Connect has no concept of a night. It holds `SleepSessionRecord`s,
and an afternoon nap is a session exactly as last night is. For a long time
this app mapped one session to one `Night`, which was wrong in three
directions at once and quietly in all of them:

- a total that left out an hour of afternoon sleep;
- "the last 7 nights" silently becoming the last seven *sessions*, so a
  napper saw five days of history in a seven-day chart;
- every screen that keys nights by day — the sleep bank's `associateBy`,
  readiness' `sleepByDay` — keeping whichever session came last. On a
  napping day that is the nap, so a twenty-minute doze replaced a
  seven-hour night in the baseline and in the bank.

`oneNightPerDay` in `health/HealthReader.kt` adds them up before anything
sees them, called once, right where `toNight` is mapped over the records.
Everything downstream — charts, score, readiness, widgets, export, both
caches — gets one night per day for free, and nothing else needed to know.

**Everything is added and no sleep leads.** Six hours overnight and three
in the afternoon is a nine-hour day: the lengths are summed, `start` and
`end` reach from the first sleep to the last, and `segments` is all of
them end to end, so the hypnogram shows the afternoon with a gap where the
day was. An earlier draft of this let the longest sleep lead and keep the
timeline, which is tidier to draw and wrong: it presents a total as though
it belonged to one sleep.

**`Night.parts` holds the sleeps the day was added up from**, each a
`Night` in its own right with its own hours, stages and efficiency —
empty on the ordinary one-sleep day, and never nested more than one deep.
The sleep screen lists them under the hypnogram, and the headline says
"Added up from 2 sleeps", because nine hours is a startling figure until
you can see it was six and three. A total nobody can take apart is a
figure to be taken on trust.

**Three things must not stretch across the waking hours between two
sleeps**, and each is a field or a rule rather than a derivation:

- `inBed` adds the sessions instead of measuring `start` to `end`. On a
  two-sleep day the span is sixteen hours, against which nine hours of
  sleep reads as a wretched efficiency. The two agree on a one-sleep day,
  which is why the default is the span — and why confusing them is easy.
- `wakes` counts within each run of contiguous segments, splitting on a
  gap wider than a minute. Every sleep ends on an Awake segment — getting
  up — and a single pass over a merged list finds the morning's sitting in
  the middle and charges a wake for it.
- `midpoint` is stored, and is the mean of each sleep's middle weighted by
  its own length. Halfway between going to bed and waking from an
  afternoon sleep is breakfast time; `consistency` in `SleepScore` is the
  only thing that reads it, and it would mark down every napping day for a
  shift that never happened.

The fold also repairs a night a writer split in two around a long wake,
which until now read and scored as two short nights.

Both persisted caches gained fields, and both version constants went to
2 — `CACHE_VERSION` and `KEPT_VERSION`. That is what makes the migration a
non-event: a cache written by the old build is refused whole and rebuilt by
the next read, so no pre-fold night can survive into a screen that now
assumes one night per day. The widget store deliberately keeps no `parts`,
so `Night.sleeps` reads 1 there, alongside `wakes` reading 0 for want of
segments; both are documented at the encoder and neither is asked for by
anything in `widget/`.

## The export

`health/HealthExport.kt` writes a zip of four CSVs — `daily.csv`,
`nights.csv`, `workouts.csv`, `food.csv` — wherever the system file picker is
pointed. Two faults were fixed in it on 6 November, both in the part somebody
chooses precisely because they want the numbers out of this app.

**The kind column held the raw enum.** `exerciseType.toString()` writes 76 for
tennis and 79 for a walk, and a spreadsheet of those is unreadable without the
Health Connect source open beside it. `exerciseName` now names the type, and
it moved out of the activity screen and into the layer that owns `Session`,
because naming an exercise is a fact about the data rather than about one
screen. `type_code` is kept **beside** the name rather than replaced by it:
the name is for reading and the code is for grouping, and a spreadsheet wants
both.

**And heart rate was never written at all**, while the row offering the file
said "kind, length and heart rate". The description was the only place that
figure existed. The row now carries `distance_km`, `kcal`, `steps`,
`avg_bpm`, `peak_bpm` and the device that recorded it, from
`SessionSummary` — one aggregate call per session, which is what it was built
for. Reading per-second samples instead would be tens of thousands of records
to print two columns. A session whose summary will not read still gets its
row: a workout missing from the file would be worse than one with empty
columns.

That makes the workouts part **slow enough to need saying so**. Everything
else in the zip is already in the snapshot and writes at the speed of a string
builder; this one reads. The button counts workouts while it runs, and counts
only when there is more than one, so a wait is never mistaken for a hang.

## The band icon, and the icons that found a bug

Three wrong drawings before this one. A **watch** was wrong because it has a
face, which is the one thing this product is sold on not having — and it was
the first thing on the Device tab. A **pod on a strap** was the same mistake
drawn more carefully: a raised rectangle mid-band reads as a screen. A
**capsule** was wrong on measurement rather than concept, 12 units across a 24
grid with a corner radius of 4 being a pill with a line through it.

What it is now: a narrow rectangle with soft corners, a clasp wider than the
band it holds, and three weave lines. The clasp is filled, because an outlined
clasp on an outlined band reads as one outline with a kink in it. The weave is
thinner and dimmed to 45%, three units apart, which is where full-weight
strokes turn to mush at 24dp.

The tabs follow the same principle — say what the tab is about. Alerts wore a
Vibration glyph, which draws a phone, on the tab that is about what reaches
your wrist instead; it is `GraphicEq` now. Both tabs fill their glyph when
selected, which is Material 3's own pattern and which this bar did not have.

**Workout icons found a real bug.** The rows carried the first letter of the
workout's name: two entries on this phone read "R" and were racquet sports,
and a run would have read "R" too. They are keyed on `exerciseType` now, not
on the name, because `exerciseName` prefers the record's own title and a
session called "Morning loop" gives an M while the type underneath still says
walking. Health Connect has constants for tennis, squash, badminton,
racquetball and table tennis and **none** for "racquet sports", which is the
entry Google Health actually offers — so it arrives as `OTHER_WORKOUT` with
the name in the title, and so do circuit training, CrossFit and most of that
list. The type is consulted first and the name only when the type said
nothing. Order in the name list is load-bearing: "walk" sits above
"treadmill", or a treadmill walk would be a run.

**Buzz the band is off while a sync is in flight.** The band answers one
connection at a time, so a buzz asked for mid-sync does not queue — it comes
back `BUSY`. It was the one control in this app that could be pressed into a
guaranteed failure.

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
./gradlew :app:testDebugUnitTest :air:testDebugUnitTest
./gradlew :app:lintDebug
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

Those first three are exactly what CI runs — see `.github/workflows/ci.yml`.
Lint is not optional there: every API-level portability bug looks the same,
invisible on the one phone it was written on and fatal on somebody else's, and
the only one found so far was found by lint rather than by use.

The baseline profile is regenerated by hand, with a phone plugged in, and only
when the startup path changes shape:

```bash
./gradlew :app:generateReleaseBaselineProfile
```

adb lives at `C:\Users\SRINEER\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
From Git Bash, device paths need doubling: `//sdcard/x.png`, not `/sdcard/x.png`.

Inspecting state on the phone, which is how most bugs here have actually been
found:

```bash
adb shell "run-as com.srineere.cuff.dev cat shared_prefs/relay_rules.xml"
adb shell "run-as com.srineere.cuff.dev cat shared_prefs/calendar_relay.xml"
adb logcat -s AirBridge
```

`calendar_relay.xml` holds the activity history and the learned naming apps;
`relay_rules.xml` holds the config (currently **version 6**).

## Traps that have already cost time

- **`AudioManager.getMode()` never reaches `MODE_RINGTONE` on a silent
  phone.** Telecom takes ringing audio focus in order to play a ringtone; with
  nothing to play it never asks, and the mode sits at `MODE_NORMAL` for the
  whole of a ringing call. `RelayRingWatch` treated that mode as proof the
  phone had stopped ringing, so with the ringer off every call died on the
  first swap of its notification and the only buzz anyone got was the missed
  call afterwards. A reporter on a Pixel 9a found the shape of it before we
  did — setting any audible ringtone made it work. The mode is now consulted
  only when `ringerAudible()` says it is worth consulting, and a second
  witness, the missed-call channel, ends a ring that nothing else would.
  Anything else that reads the audio mode has the same hole.
- **Notification access is a *restricted setting* for a sideloaded app.**
  From Android 13 the switch is greyed out for any app installed from a file,
  which is every copy of this one, and tapping it says "Restricted setting"
  and no more. The way through is *Settings → Apps → Cuff → ⋮ → Allow
  restricted settings*. It is in the README and in the Permissions screen's
  own copy, because the first run of this app meets it every time and nothing
  on screen used to say so.
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
- **`handler.postDelayed` is not a clock.** It counts in
  `SystemClock.uptimeMillis`, which stops while the phone is suspended, so
  anything scheduled that way drifts by however long the device slept. The
  step check ran on it and a night in Doze pushed the daily goal and the
  hourly nudge arbitrarily late. Anything that has to happen at a *time*
  wants `AlarmManager`; `postDelayed` is only for "a while from now, while
  we are awake anyway".
- **An alarm delivered to the service dismisses the nudge.** `enterForeground`
  runs before `onStartCommand` reads the action and it cancels notification
  903 — the hourly nudge's own. So a periodic alarm routed through the
  service would quietly clear each nudge within a quarter hour of posting
  it. The step alarm goes to `StepCheckReceiver` and into the live instance
  instead, which is why it is a broadcast and not the simpler
  `PendingIntent.getService`.
- **Three timeouts in three files that have to be read together.** The
  coalescer's cooldown is 15 s *from the end of the previous command*;
  `sendFollowUp` drops a merged alert older than `ALERT_LIFETIME_MILLIS`
  (30 s) *from when it was posted*; `AirLink`'s operation deadline is 45 s.
  So a burst held behind a slow write waited `write + 15 s` against a budget
  of 30 s, and any write over about thirteen seconds killed the whole burst
  — one late buzz for the first message and silence for the rest. A locked,
  idle phone is exactly when Google Health takes the Air's single session
  and a write gets slow, which is why it read as "missed when locked".
  `followUpAt` now schedules at the earlier of the cooldown and the oldest
  waiting alert's expiry, and the lifetime has been raised to ninety so that
  sixty — a full `AirLink` timeout plus the cooldown — fits inside it with
  room. Change any one of the three numbers and do this arithmetic again.
- **There is a fourth thirty seconds, and it is not the same window.**
  `CalendarAlertFilter`'s `ARRIVAL_STALENESS_MILLIS` asks how old a
  notification already was *when it reached us*, which in the ordinary case
  is milliseconds and refuses nothing; it exists for the listener reconnect,
  where every notification on the phone is handed back with its original
  `postTime`. `ALERT_LIFETIME_MILLIS` asks how long an alert the relay has
  already accepted may wait and still be worth sending. They were both
  thirty for a while and that was a coincidence, not a rule. Raising the
  door to match the queue would replay a minute and a half of old
  notifications onto a wrist every time the listener reconnects.
- **A widget's `initialLayout` is inflated raw, and it is what you see after
  a reboot.** RemoteViews live only in the launcher's and the system's
  memory, so a restart throws them away and the host inflates
  `android:initialLayout` straight out of the APK — no `setRemoteAdapter`,
  no `setEmptyView`, no actions, no data. The stack's empty TextView was
  `visible` with its text baked in, so every reboot drew "Tap to choose what
  this stack holds" over a stack that was configured perfectly well, and no
  amount of debugging `onDataSetChanged` would have found it, because
  `onDataSetChanged` had not run. An initial layout is a placeholder for a
  widget that knows nothing; write it as one.
- **`getLoadingView()` returning null does not mean "no loading view".** It
  means `RemoteViewsAdapter` supplies its own, which is a TextView reading
  "Loading…" from the platform's resources. That string is not in this app
  and grepping for it finds nothing, which is a memorable hour. Return an
  empty item-shaped frame instead.
- **`setIntent` makes a widget's tap permanent.** The activity is singleTop,
  so `onNewIntent` calls `setIntent` and `getIntent()` answers with that
  widget's intent for as long as the instance lives. A configuration change
  outside the list `MainActivity` handles — `screenLayout` and `keyboard` are
  not in it — recreates the activity, runs `onCreate` against that retained
  intent, and navigates to the widget's screen a second time, pulling
  somebody back to a screen they had deliberately left. Read the route once
  and `removeExtra` it; an intent is a message, not a setting.
- **A day is not twenty-four hours twice a year.** `hourlyTotals` used
  `start.plus(Duration.ofDays(1))` and indexed by *elapsed* hours, so after a
  spring-forward every bucket sat one slot low and an autumn day's last hour
  was never read. Use `localDay` for the boundaries and `byLocalHour` for
  the index, and note the latter *sums* — autumn runs 01:00 twice and both
  belong in slot 1. `dailyTotals` was always safe because it uses
  `LocalDateTime` and `Period`; the hourly one was not.
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
- **A baseline profile recorded against an empty app looks like a tidier
  one.** Regenerating produced a *smaller* file — 2,828 rules removed, 112
  added, 15% down — which reads as tightening and was the opposite:
  `HealthChartsKt` went from 72 rules to **none** and `HealthScreenKt` from
  196 to 16. The profile records against `com.srineere.cuff`, a
  different install from the `.dev` build on the phone, and the tooling
  installs it clean for every run, so it starts with no Health Connect grants
  and the recording describes the "Allow Health Connect" card. Nothing warned;
  the only way to catch it was counting rules for a class that had to be in
  there.

  Two fixes were tried. `pm grant` from inside the run needs
  `GRANT_RUNTIME_PERMISSIONS`, which the shell user does not have on this
  phone — and the benchmark's own shell is that same user. It is kept anyway,
  wrapped in `runCatching`, because on a device that allows it that is the
  whole fix. Granting by hand beforehand does not survive either: the
  permission really is there right up until the run starts, and then AGP
  uninstalls and reinstalls rather than updating. So `requireDataOnScreen()`
  refuses to record while the ungranted card is up, and says why. A wrong
  profile is worse than a stale one — a stale profile is wrong about the new
  code, and that one was wrong about all of it. The committed profile lives at
  `app/src/release/generated/baselineProfiles/`, with its 72 chart rules
  intact.

## Design

The canvas is an Artifact: <https://claude.ai/artifact/EcNAsWKo5w3ciHwkswEDFc>
(private). A board for every screen, plus design notes explaining *why* each screen is
the shape it is — the notes matter more than the pixels.

The working habit this project runs on: **design first, then build.** For
anything structural, update the board, show it, wait for a yes. Small fixes go
straight in. A board that has drifted from what shipped is worse than no board,
so the canvas is republished in the same sitting as the change.

Boards for screens that exist but were never drawn: the per-app caller list, and
a person's rules across apps. Every Health board is now built; `Setup` is the
tab's own not-connected state, and `Live` is the measuring state of the heart
rate tile. The seven screens added on 5 and 6 November — readiness, fitness
age, the morning report, the journal, the sleep bank, export, and the window
control that reaches every chart — were built ahead of their boards, which is
the one place the habit below was not kept.

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

Shorter than it was. **The seven features chosen on 5 November are built** —
readiness (`health/Readiness.kt`, `ui/ReadinessScreen.kt`), fitness age
(`health/FitnessAge.kt`, `ui/FitnessAgeScreen.kt`), the morning report
(`health/MorningMoment.kt`, `health/MorningReport.kt`, `ui/MorningScreen.kt`),
the journal (`health/Journal.kt`, `health/JournalReminder.kt`,
`ui/JournalScreen.kt`), the sleep bank (`ui/HealthSleepBankScreen.kt`), export
(`health/HealthExport.kt`, `ui/HealthExportScreen.kt`) and day-wise
exploration in every chart (`ui/ChartWindow.kt`). `docs/ROADMAP.md` is the
record of what was chosen and why, including the reversal of the
no-composite-scores rule; it is no longer a list of outstanding work.

Note that fitness age is called **fitness age, never biological age**. The
published models estimate cardiorespiratory fitness and nothing else, and a
name claiming otherwise is the claim, not the number.

What genuinely remains:

- **Widget buttons.** The three band widgets with controls — Stop, Buzz, Find,
  and a pause — are on the canvas and deliberately absent from the code: each
  takes the band's one session, and a button is a different kind of widget
  from a reading. `WidgetCatalogue.notBuilt` derives what is missing rather
  than keeping a hand-written list, so this entry cannot outlive the gap.
  Everything else in `widget/` is built — see **Widgets** below.
- **"Pair a different band."** On the Device board, never built.
- **Confirming the goal pulse on the wrist.** The hourly nudge is now proven end
  to end: the device prefs hold `nudge-*` entries and Recent names them. The
  goal pulse is still unwitnessed — the poll reads the steps and writes
  `goal_fired_day`, but no `goal-*` entry has been seen, because it takes
  crossing the goal on a day the relay is up.
- **Knowing a food log is complete.** The screen exists; the certainty does
  not. The reader counts entries a day and the Energy screen says when today
  has fewer than usual, which is as far as the data can go: nothing can tell a
  genuine fast from an unlogged dinner.
- **Per-day quiet windows.** The config holds one window for every day; per-day
  would be a config change, not a screen.
- **Telegram end to end.** The identity path is verified from Telegram's source
  and the contacts picker works, but no real Telegram call has matched a rule on
  a device yet.

- **Three metrics Google Health already writes and this app does not read.**
  The groundwork is established and worth not re-establishing: every type
  exists in `connect-client:1.1.0`, so no version bump is needed. There were
  four; `Vo2MaxRecord` is now read, declared in the manifest and in
  `HealthConnect.readPermissions`, and is what fitness age is built on.
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

Thirty-one designs in four families, in `widget/`, every one of them built.
The catalogue under Device › Widgets lists them with a drawing of each.

| Family | Designs | Needs |
| --- | --- | --- |
| Your tiles | 17, one per `TileKind`, the two off-tab kinds included | nothing |
| The band | 4: the band, battery, relaying, buzzes today | nothing |
| Drawn round | 6: three arcs, summary, trends; sleep score, three dials, the overview | a bitmap for the arcs |
| Type alone | 4: steps, sleep, battery, relaying | the wallpaper's own hint |

Rings and Dials were two families and are now one. Three thin arcs that close
and one thick arc with a figure in it are two hands, not two families, and
splitting them put four widgets under two headings nobody needed to choose
between. Paper was built and then cut.

Still designed and not built: the band widgets with **buttons** — Stop, Buzz,
Find, Pause. Each takes the band's one session, and a button is a different
kind of widget from a reading, so they arrive with their own slice rather
than half-built inside this one. `WidgetCatalogue.notBuilt` derives the empty
families rather than keeping a "coming soon" list, because a hand-kept one
goes on promising something that shipped a month ago.

A design's `id` is a prefixed enum **name** — `tile:Steps`, `band:Battery` —
never an ordinal, so inserting a kind cannot silently turn every placed Sleep
widget into an Energy one.

**Stacks.** A sixth provider, `StackWidgetReceiver`, and the only one that
is not a Glance widget. One cell holds several cards; the frame is built by
hand and each card is composed through `GlanceRemoteViews` from the same
`WidgetFace` the five single-card providers draw, at the whole cell, so a
resize redraws them all.

**Nothing in a widget scrolls sideways.** `HorizontalScrollView` is not a
remote view, and the adapter-backed ones that are (`ListView`, `GridView`,
`StackView` and the two flippers) are vertical or do not scroll. Nor can a
vertical list be made to snap. So the stack is an `AdapterViewFlipper`,
which shows exactly one child and is therefore paged by construction, and
the two chevrons in the bottom corners are how a page turns.

Two earlier answers and why they went: a `StackView` genuinely stacks and
swipes, but the swipe never arrived on the test launcher and it measures
its children about a tenth smaller than the cell, so a card composed to the
cell overflowed. A `ListView` scrolled reliably but scrolled like a list,
resting between two cards.

**Five things here will each cost you an afternoon.**

* **Only certain views may appear in the layout.** `android.view.View` is
  not one of them, and neither is `Space`. One of either anywhere in
  `widget_stack.xml` and the launcher draws "an error occurred when loading
  widget" in place of the whole thing, with
  `getDefaultView couldn't find any view` the only clue, in the launcher's
  log rather than ours. The chevrons are placed with `layout_gravity`
  rather than by a row with a spacer for exactly this reason.
* **The flipper's animations are animators, and must be a single
  `objectAnimator`.** `AdapterViewAnimator` loads `android:inAnimation` with
  `AnimatorInflater` and casts to `ObjectAnimator`, so a tween from
  `res/anim` throws, and so does a `<set>` from `res/animator`, which
  inflates to an `AnimatorSet`. Two properties in one animator is what
  `propertyValuesHolder` is for.
* **The animation cannot have a direction.** A RemoteViews cannot set a
  flipper's animations, so the only way to animate backwards is to send a
  different layout, and a different layout makes the host inflate the
  widget again. The new flipper's adapter has not loaded yet, and
  `setDisplayedChild` against an empty adapter clamps to zero, so every tap
  on the back arrow jumped to the first card. It is a fade.
* **The page is ours, in `StackPage`, not the flipper's.** `showNext`
  through `partiallyUpdateAppWidget` arrives, returns cleanly, and does
  nothing visible: it means "one more than wherever you are", and the host
  rebinds and rebuilds that view whenever it likes. A stored index plus
  `setDisplayedChild` on *every* update says exactly which card and
  survives the rebuild.
* **Every card is wrapped in `widget_stack_item.xml`, and cannot carry its
  own click.** Glance hands back a RemoteViews whose layout id comes from
  its generated pool and differs card by card; an adapter returning those
  directly claims one view type while having several, and the recycler
  draws one card's chart into another's frame. And a child of a collection
  taps through a fill-in intent against the collection's template, so cards
  are composed with `onClick = null` and `routeFor` supplies the route as a
  value. That template is the one mutable `PendingIntent` in the app.

The cards are composed in `onDataSetChanged`, which is allowed to be slow,
never in `getViewAt`, which the launcher waits on.

**The shape of it.** Six `AppWidgetProvider`s — five, one per cell size
(`WidgetSize`), all backed by one `AirWidget : GlanceAppWidget`, plus the
stack. Each has
`android:configure` pointing at `WidgetConfigActivity`, which Android
launches on drop and which *is* the catalogue; nothing is placed until it
returns `RESULT_OK`. `WidgetStore` keeps one row per `appWidgetId` in
`air_health`, beside the tile layout, because a widget's colour is its
tile's colour and two stores could disagree about that.

**Five things that cost real time, and will again if they are undone:**

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
- **A Glance container holds ten children and drops the rest in silence.**
  Its generated layouts stop at `..._10children.xml`, so a Row asked for
  more keeps the first ten and discards the others with no error anywhere.
  A day binned into twenty-four hours came out as five fat columns and a
  night's eleven sleep stages came out as five, for weeks, looking merely
  ugly rather than broken. Anything with more than a handful of parts is a
  bitmap now: `ChartBitmap` for bars and stripes, `ScatterBitmap` for dots,
  `RingBitmap` for arcs.
- **`SizeMode.Exact`, not `Responsive`.** Responsive reports the size you
  *declared*, not the cell you were given. A launcher's 4x4 came back half
  again taller than the 300dp the face was measured for: the RemoteViews
  stretches, the composition does not, and the content ends up crushed into
  the top third with a field of empty card beneath it.

**Arcs are bitmaps, because Glance has no determinate ring.**
`CircularProgressIndicator` takes a colour and a modifier and nothing else,
so it can only spin, and a curve cannot be made out of a Row of Boxes.
`RingBitmap` draws one Canvas per composition, capped at 192px -- not a
guess: a RemoteViews crosses a Binder transaction with a hard size limit and
ARGB costs four bytes a pixel, so a 4x4 at three times density would be
about 3MB, which is a widget that silently fails to appear. A bitmap also
cannot carry both halves of the palette the way a `ColorProvider` does, so
`WidgetColours.isDark` resolves that question once, through Glance's own
`isNightMode` where the app follows the system.

**Only a reading with a real ceiling gets a ring or a dial.** Sleep has a
score out of a hundred this app computes; steps have a goal; burn has a
fortnight to sit against. Resting heart rate, HRV and skin temperature have
none, and a dial for them would be a denominator nobody picked. Note that
`Goals.activeMinutes` is *weekly* -- the WHO's lower bound -- so it cannot
join two daily rings, and dividing it by seven would invent a target nobody
chose. Today is excluded from its own burn average: at nine in the morning
it is a fifth of a day and would drag down the thing it is measured against.

**Paper did not follow the phone's light-or-dark, by design.** A pale card
was its premise. It used the *app's* light half, so a Cream ground stayed
Cream, and every colour went through `paperTile`: the mint that carries
steps on a dark tile is 1.81:1 on white, which is not dim, it is gone.

The family was cut, and this is written in the past tense for that reason —
but `paperTile`, `PaperInks` and `WidgetColours.paperInks` are all still in
`WidgetTheme.kt`, unused by any design. Either a Paper family comes back and
uses them, or they go; leaving them to rot in the middle of the colour layer
is how the next person spends an afternoon working out which half of the
file is live.

**Type alone has no card, so it asks the wallpaper.**
`WallpaperColors.HINT_SUPPORTS_DARK_TEXT` is the same signal the launcher
uses to colour its own icon labels, so the type matches the labels beside it
rather than fighting them. Null where the system will not say, and it then
falls back to the app's own light-or-dark. A wallpaper wanting dark text is
a pale one, so the ink comes from the light half even on a phone in dark
mode: what matters there is the photograph, not the theme.

**There is no letter spacing in Glance.** `TextStyle` carries size, weight,
colour, alignment, decoration and family, and `FontWeight` is exactly
Normal, Medium and Bold. Paper's look rested on figures tracked at -0.03 em
on the design sheets and could not have it without a bitmap per numeral,
which would also freeze the figure against the system text size. That is one
of the reasons the family went.

**The widgets draw what the tiles draw.** The head's window and full name
come from the kind, the change chip is the tab's filled pill at fourteen per
cent rather than coloured words, the tall face stacks as the tile's XL does,
and the kinds whose tiles plot dots over a usual band -- resting heart rate,
HRV, breathing, skin temperature, weight -- plot dots here too. `ScatterBitmap` copies `MiniScatter`'s arithmetic on purpose: a
widget plotting its points differently is a widget showing a different
chart.

**One `Face`, drawn twice.** `faceFor`, `bandFace`, `ringsFace` and the rest
produce a `Face`, and both the widget and the catalogue's thumbnail draw
from it. A catalogue whose pictures are written separately goes quietly out
of date, and the first to notice is someone who placed a widget expecting
what the picture showed. The thumbnails are Compose, because Glance composes
into RemoteViews which cannot be drawn inside this app's own UI at all.

**The previews are the tile editor's made-up week**, not the owner's: a
catalogue is about what a widget *is*, and it costs no Health Connect read
to open. `sampleGoals` deliberately sits a little beyond the sample day --
level with it, every bar was full and three rings drew as a bullseye.

**`loadHealthData`** is a plain function in `HealthState.kt` rather than a
method, so the widgets and the tab share one read. Two readers would be two
sets of arithmetic drifting apart, and a tile whose trend differs from its
widget's is the kind of bug nobody reports because nobody believes it. The
widgets ask for 30 days — `WIDGET_WINDOW_DAYS` — and `widgetWindowDays`
raises that to 120 when a readiness or fitness-age face is on the home
screen, because those two count nights rather than days and cannot answer
on a month.

**And they draw from disk before they read anything.** The snapshot was held
in memory alone, so a reboot, or Android reclaiming the process on a quiet
afternoon, left every widget with nothing and sent the next draw to Health
Connect on a thread the launcher was waiting on. `WidgetSnapshotStore` keeps
the Health Connect half of the snapshot — about 12 KB of JSON in its own
`air_widget_cache` file, deliberately not beside the tile layout, because
SharedPreferences rewrites a whole file per commit. A cold process draws the
kept figures at once and asks for a read behind them. Nothing derived is
stored: the restored snapshot goes through the same `healthDataOf` the tab
uses, so a widget and a tile cannot compute different trends from the same
readings.

Two things are deliberately thinned rather than kept whole. The hypnogram's
segments are dropped — no face draws one, and `hasStages` is answered by the
three stage durations, which are kept. Today's beats are thinned to the
highest sample in each five minutes, which is lossless for the only thing any
face draws from them, an hourly maximum, because five minutes divides an hour
exactly. Every kept sample is a real sample at its real time.

There are now **two** persisted caches of the same `HealthSnapshot` —
`widget/WidgetSnapshotStore.kt` and `health/HealthCache.kt` — written at the
same time by two people who could not see each other. They share the
reduction and nothing else. Collapsing them is deleting one and pointing its
reader at the other; it has not been done, and this paragraph is the reason
somebody will know it was a decision rather than an oversight.

**Adding one belongs to the launcher.** `requestPinAppWidget` was tried and
removed: Android deliberately skips the configuration activity for a pinned
widget, so it arrives knowing nothing about what it is meant to show. The
app's own screen is a catalogue, not a second way to place things.

**A stale APK will waste an afternoon.** Gradle reported `:app:packageDebug`
UP-TO-DATE while the APK on disk was from a different build -- unit tests
green on the new values, phone showing the old ones, no error anywhere.
`rm app/build/outputs/apk/debug/app-debug.apk` before building forces it.
The suspected cause is a second Gradle running in the same project.


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

Saved foods are Cuff's own, in `SavedFoods`. They can be edited and
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
first space used to do this and gave "Skin" for skin temperature, which names
an organ rather than a reading, so the ones that break are written out.

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

`defaultTiles` is the layout this app's owner actually settled on: heart rate
XL, steps XL, energy M beside nutrition M, sleep XL, vitals W beside readiness
S, activity L. Today first, then the day as it is being spent, then the night
that preceded it.

It was taken from a tab somebody had arranged and lived with, and taken again
once readiness existed. Readiness has the small slot beside the Vitals strip,
where a single figure against its own band is all the room needs. Workouts
moved up to the full width and close the tab, because a list of two lines in a
third of a row is a list of one. Burned against eaten came off, since the
Energy tile two rows above is the same arithmetic; HRV came off for the same
reason resting heart rate did, which is that the Vitals strip already carries
it.

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

**The window's end hour is exclusive, and it did not used to be.**
`shouldNudgeForHour` read `hour in activeFrom..activeUntil`, inclusive at both
ends, so a window the Alerts tab printed as "09:00–21:00" could buzz somebody
at 21:50 — fifty minutes after the time they chose. It was reported from the
phone, not found here. A test asserted the behaviour outright
(`shouldNudgeForHour(…, hour = 21, …) == true` with `activeUntil = 21`), which
is how it survived: the assertion was the bug written down rather than a guard
against it. It is `until` now, the last buzz lands at 20:50, and the boundary
is pinned from both sides so moving it again takes a deliberate act. The start
stays inclusive: the first buzz at 09:50 reports the hour that is running.

**What schedules them.** `scheduleStepCheck()` still picks the wait with
`nextStepCheckMillis`, which shortens the quarter hour so it lands inside the
nudge's `[50,59]` window — without it, a relay started at two minutes past
checks at :02, :17, :32 and :47 for as long as it runs and the nudge never
fires once. The wait then goes to an `AlarmManager` one-shot rather than the
handler, because the handler's clock stops in Doze. Each delivery reschedules
the next.

On API 31 and up an exact alarm needs either `SCHEDULE_EXACT_ALARM` — declared,
granted at install on 31–32, deny-by-default from 33 — or exemption from
battery optimisation, which the Device screen already asks for. With neither,
`armStepAlarm` returns false and the handler takes over: later than it should
be, but not gone. Nothing throws. `canScheduleExactAlarms()` sits behind a
plain `sdkInt < 31` early return with no lambda between the guard and the
call, deliberately, so lint's `NewApi` analysis can still follow it.

There is no UI to ask for the exact-alarm grant. On a phone running 33 or
later that has also refused the battery exemption, the step check is back on
the handler and will drift. That is the one gap left here.

## What a chart says out loud

Every chart was reachable and silent: a screen reader landed on the Canvas,
found no `contentDescription` and nothing under it worth walking, said
nothing, and `ChartReadout` — which fills from a scrub — stayed empty for
somebody who cannot scrub. Reachable and silent is worse than unreachable,
because it reads as an app that is broken rather than one that stopped short.

Each chart that is worth hearing now carries one sentence, built in
`ChartLabel` from the same lists it is drawing, so it cannot drift from the
picture. The shape is fixed in one place on purpose — a dozen screens with
two ways of saying the same thing has one too many:

> `<Metric>, <window>. <low> to <high> <unit>. Latest <x>. Goal <g>.`

Three rules that are easy to undo by accident. **Units are words** —
"beats per minute", not "bpm", because a reader left to spell an
abbreviation has told you less than one that says it. **Signs are words** —
a reader says nothing at all for `−`, so an energy balance of −420 has to
say "minus 420" or it reads as 420. **Durations are words**, via
`spokenMinutes`, not the card's "6h 28m".

`clearAndSetSemantics`, not `semantics`: there is nothing under a chart but a
thousand drawn circles, and a thousand nothings is not a tree worth walking.

Charts inside tiles stay silent, and that is the design rather than an
omission. A tile is already one node whose own text names the metric and
reads the figure; a sentence on the Canvas inside it says the same thing
twice. The same goes for the session rows' sparklines and Readiness's rail,
where the card's prose above and below already says what the drawing shows.

There is no point-by-point stepping action. The summary carries the range,
the latest and the goal, which is most of what a scrub yields, and a working
`CustomAccessibilityAction` would have to drive `picked` — which lives in
each of the ten screens, not in the chart — and then get focus onto a
separate Text to read the answer. Worth doing on its own; not worth
half-doing here.

## Two things that only break on somebody else's phone

**Large type.** Tile heights were fixed dp while everything inside them grew
with `fontScale`, so at the largest step in Display → Font size a small
tile's heading and figure ate the whole 112 dp and the `weight(1f)` chart was
squeezed to zero and simply vanished. Nothing crashed, which is why it went
unseen. `tileHeight` now scales with `fontScale`, clamped to `1f..1.5f`:
the floor because answering a request for smaller letters with a smaller tab
is not what was asked, the ceiling because Android 14 reports up to 2.0 and
at 2.0 the XL hero is 432 dp — half a phone for one tile.

**Process death.** Rotation has always been safe, because `MainActivity`
declares `configChanges` over orientation, fontScale, density, uiMode and
locale. Process death was not, and it took the Log food form mid-entry, every
chart window, the picked night and the food log's day. Those are
`rememberSaveable` now; `ChartWindow` goes through `ChartWindowSaver`, a
`listSaver` of length and offset, which is why `ChartWindow` has an
`initialOffset` parameter it would otherwise have no use for.

Left on plain `remember` on purpose: anything tied to an in-flight write, a
transient refusal line, something re-read from disk, and the two dialogs that
hold a `FoodEntry` or a `SavedFood` — neither is a Bundle-able kind, and
making them saveable throws.

## The Health half is opt-in, and off unless somebody asked

Several people who tried this wanted the relay and nothing else, and were
being asked for thirteen health permissions to get it. So the whole half is
behind one switch, `health/HealthEnabled.kt`, stored in `air_health` under
`health_on`, and a fresh install starts with it off.

**The migration is the part to be careful with.** Default off must not reach
back and switch off a phone already using the tab. `healthOnDecision` runs
once per install and settles it: a stored tile layout means on, otherwise any
granted read means on, otherwise off — and if Health Connect *cannot be
asked*, it writes nothing and tries again next launch. That last branch is
load-bearing. `HealthConnect.granted()` flattens a failure into an empty set,
so deciding "off" from a provider that was merely mid-update, and then never
looking again, is exactly the regression the switch was supposed to avoid.

Because the check is asynchronous, the first launch after this version lands
on Alerts for a few hundred milliseconds before an already-granted install
decides it is on. Once, and only on a launch.

**What the switch reaches.** The tab and every health route; the morning
report, the journal reminder and the readiness morning pass, which now
*cancel* rather than simply not book, so a job already in WorkManager dies
rather than outliving the feature; the step-goal buzz and the hourly nudge,
which live in the relay but cannot work without step data and which the
Alerts tab now explains rather than showing as two dead switches; the tile
widgets, which leave the picker while the band widgets stay; and both
on-disk caches, which are cleared so a switched-off feature leaves no
readings behind.

Nothing is deleted. Goals, the tile layout, saved foods and the journal's
answers all survive, so turning it back on returns the tab somebody had, and
food already written stays in Health Connect because it was never ours to
remove.

**Two traps worth knowing.** The NavHost's `startDestination` is remembered
once rather than tracked live: changing it rebuilds the graph, and `setGraph`
pops the whole back stack, so flipping the switch would throw somebody off
the Device tab they were touching. And `goToTab` pops to a `root` the app
tracks rather than to `findStartDestination()`, because the graph still names
Health as its start whether or not Health is on, and popping to a route that
is no longer on the stack pops nothing at all.

A health route asked for while the half is off lands on Device, where the
switch is. `isHealthRoute` is a predicate rather than a list — `startsWith
("health")` plus five names — so a new health screen is covered without
anybody remembering to add it.

## The journal is hidden, not deleted

The row is gone from the Health tab and `JournalReminder.hidden` turns the
booking into a cancellation, but `Journal.kt`, `JournalScreen.kt`,
`JournalTest` and the stored settings are all still here and still compile.
The route stays registered, because an unregistered route makes `navigate()`
throw rather than go somewhere.

The part that mattered was not hiding it but **cancelling what was already
booked**: the reminder is a real WorkManager job, and a build that merely
stopped scheduling it would have gone on firing an evening notification for a
screen that no longer exists. It is cancelled on the next app open and the
worker also declines to re-book itself, so the chain ends even on a phone the
app is never opened on. Notification 905 is cleared with it, because one
posted last night would otherwise sit in the shade opening nothing.

`hidden` is a non-const `internal val` on purpose, so the compiler cannot
fold the booking away as unreachable and quietly make this harder to undo.

## The tab opens on its last read

`health/HealthCache.kt` keeps the last good `HealthSnapshot` in `air_health`
as one JSON line, about 40 KB for a month, and the tab restores it before it
reads anything. The snapshot is kept rather than the derived `HealthData`,
because the trends, bands and balances are all computed from it — storing
them too would be a second copy of every figure on the tab, and a second
thing to disagree with the first. Both the live read and the restore run
through `healthDataOf`, so a restored frame and the frame that replaces it
differ only in how old they are.

**`loading` is now `refreshing`, and the rename is the point.** It used to
mean "a read is in flight", and every screen treated it as "there is nothing
to show" — which is why switching a chart to 90 days emptied the screen and
said nothing. Those are two different questions and now have two different
answers: `refreshing` is the read, and `HealthStage.Loading` on the data is
the emptiness. Anything drawing a spinner should ask the second; anything
saying "this might still fill in" should ask the first.

**What is not kept: today's individual beats.** Tens of thousands of
samples, and the only part of a snapshot that is about *now* rather than
about a day. So the heart screen says "Reading today's beats…" while a read
runs, rather than "No heart rate recorded today yet", which would have been
a false statement about a cache rather than about the user.

**The day boundary is where a cache like this goes wrong.** The entry stores
the local day it was read on, and on restore anything meaning "so far today"
— the six counters and the two hourly series — is dropped when that day has
turned. Opened at two in the morning the tab shows a month of real nights
with today's counters empty, not yesterday's nine thousand steps under
today's date.

`widen` rounds a request up to the next whole month. A seven-day chart
stepped back four times used to ask for 35, 42, 49 and 56 days — four full
reads over almost identical ground — and now costs one read of 60. The
windows asked for by name are already whole months, so nothing that was fast
became slower.

## Readiness is settled in the morning, not when you open the screen

Readiness used to be computed by the only thing that ever asked for it: the
screen. Opening it forced a 120-day Health Connect read — the tab holds 30
and the gate counts 60 nights — and until that landed the screen said
"Calibrating", which is why the figure arrived a while after the screen did.
Nothing cached the expensive part: the old store kept the score and could
not be consulted without a snapshot, because the day it is keyed by comes
out of the series the snapshot produces.

It rides `MorningWorker` now, which was already the right machinery: booked
at 06:00, looks again every twenty minutes, gives up at eleven. That retry
loop is the whole answer to "the inputs are not there yet" — the Fitbit sync
is not something this app drives, so a morning pass has to tolerate arriving
before the night does. The cheap 30-day read is what each look costs; the
120-day read happens only on the pass that can finish the job, and once a
morning is settled there are no more looks that day. A phone nothing has
written to in thirty days is not a morning running late, and is not retried.

`MorningMoment.schedule` no longer cancels when the morning report is
switched off, because the moment now carries two things and only one of them
is the notification. That is one wakeup a morning on a phone that wanted no
report; the alternative was a fourth scheduled job doing the same look a few
minutes later.

What is kept is the **series**, not the score, in `air_health`. The score is
derived from it by the same `NEEDED` rule, so there is no second field to
disagree with the first. It is keyed by the morning the figure describes —
the day a night *ended*, per the day-boundary rule above, not `now()` — and
served only for today or yesterday, because until the band syncs, yesterday
is the honest answer. Replacement is by how well-founded a figure is rather
than by how recent: a later morning wins unless it has no score at all, and
the same morning is replaced only by one standing on more parts or a longer
read. Without that, a 30-day widget read at midnight would overwrite this
morning's real figure with "calibrating".

## Three things known, and left alone

Each of these was found deliberately, is understood, and was not fixed —
either because the fix is a product decision or because it is lifecycle code
that cannot be tested from here. None of them is a mystery; do not spend an
afternoon rediscovering one.

1. **The dedupe map goes deaf at 256 keys.** `CalendarAlertFilter` keeps one
   entry per accepted alert, keyed by `sbn.key`, and `seen` only grows: the
   filter is rebuilt on relay start and on a change of selected packages, and
   never otherwise. Past 256 distinct keys, every notification with a new key
   is refused for the life of the relay process. On a phone where the service
   runs for weeks with a mail or news app selected, that is reachable, and it
   reads as "notifications stopped after a while". It is **deliberate and
   tested** — `boundedSessionNeverEvictsKeysAndReplaysOldAlerts` asserts it,
   because evicting a key lets a re-posted old notification buzz again and an
   app with no `MessagingStyle` timestamp has no second guard. The least-bad
   fix is to evict, but require a non-null `messageTimestamp > startedAt`
   before an evicted key may buzz again: it keeps the replay guard exactly
   where it works and loses nothing for messaging apps.
2. **A short listener disconnect can leave the relay down.**
   `onListenerDisconnected` calls `requestStop`, which sets `stopping` but
   only calls `stopSelf()` when nothing is pending — so with a buzz in flight
   `active` stays non-null for up to the watchdog's four minutes. A reconnect
   inside that window sees `isRunning == true`, skips the revive, and then the
   old service destroys itself with nothing left to bring it back. The revive
   check asks whether a service *object* exists; it should ask whether one is
   still relaying.
3. ~~`ALERT_LIFETIME_MILLIS` is thirty seconds and has never been
   justified.~~ **Settled: it is ninety.** Thirty arrived whole with the
   Flutter port, carrying no comment, no test and no mention here, and was
   shorter than one slow band operation — which is what made the arithmetic
   above sharp. `AirLink` gives up at forty-five seconds and the coalescer
   adds fifteen, so sixty is the worst a merely struggling band can impose;
   ninety clears it, and nothing the band can do on its own now outlives the
   window. The ceiling is a human one rather than a technical one and is
   written at the constant: past a minute and a half the wrist is answering
   a message already read on the phone in your hand.

Also pre-existing and unchanged: the readiness **widget** face has no
`day != today` guard, unlike `ReadinessTile` on the tab, so it can draw
yesterday's figure under a head that says this morning.

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

## Nothing on this phone is backed up

`allowBackup="false"`, and `backup_rules.xml` and `data_extraction_rules.xml`
exclude every domain from both cloud backup and device-to-device transfer. It
is deliberate: the app holds somebody's sleep, their resting heart rate and
every notification that reached their wrist, and none of that goes to a
Google server for the sake of a smoother reinstall.

The price is paid at uninstall. Goals, relay rules, the tile layout, saved
foods, journal answers and alert history all live in this app's
`SharedPreferences` — goals in `air_health` under `step_goal`,
`sleep_goal_minutes`, `energy_goal_kcal`, `protein_goal_g`,
`active_goal_minutes` — and the system deletes that directory with the app.
A reinstall starts at the defaults. Nothing warns about this, and anyone
who wants to change it should change the policy rather than the symptom.

Switching the Health half off is **not** that, and must not become it.
`healthSwitched` removes two cache keys by name and touches nothing written:
`HealthCache.clear` is `remove(KEY)`, not `edit().clear()`, which matters
because the goals share the `air_health` file with it. Turning the half back
on returns the tab somebody had.
