# Other sources for the Health tab

Written 4 November 2026. A feasibility and effort study: how hard would it be
for Air Bridge to show health data from something other than a Fitbit Air.

Sources are linked inline. Where a claim could not be confirmed against a
primary source it says so in as many words — the thing that is wrong half the
time on the web is "app X writes type Y to Health Connect", and a confident
list is worse than an admitted gap. **The turn's shared web-search budget ran
out partway through**, so a handful of secondary items are flagged
`UNCONFIRMED` rather than nailed down; each is named at the point it matters
and again at the end.

---

## Verdict

**Air Bridge is already a multi-vendor health app and nobody told it.** The
Health tab reads Health Connect, never the Air. Pair a Pixel Watch, a Garmin,
an Oura ring, a Polar or a Galaxy Watch instead, grant it in that vendor's app,
and nine of the thirteen tiles fill in with no code change at all. The work is
not "add support for other devices". The work is three much smaller things:

1. **Six places where Air-shaped assumptions are baked in** — a hardcoded max
   heart rate, a five-minute sampling gap, an exercise-type list with eight
   entries, prose that says "your band" and "Google Health" as if those were
   the only possibilities, and an eight-entry exercise-name map. All six are a
   day's work between them. None is architectural.
2. **One real correctness bug that already exists on the Air.** `HealthReader`
   aggregates every record type with **no `dataOriginFilter` whatsoever** —
   there is not one mention of `DataOrigin` in the entire codebase. Since the
   June 2026 Health Connect update the phone's own pedometer writes steps under
   a device-specific synthetic package name, and Air Bridge is silently adding
   them to the Air's. The step goal buzzes early today, on one device. Add a
   second writer and it gets worse.
3. **A provenance story in the UI**, which does not exist and which every
   multi-source app needs: who wrote this number.

For section B the ranking is blunt. **Oura and Whoop are a weekend each.
Garmin's cloud API is closed — new applications have been paused since spring
2026 with no restart date. The Fitbit Web API no longer exists: it was switched
off around 30 October 2026, four days ago.** Apple Health has no cloud API and
never has; the only live route is an iPhone-side third-party app POSTing JSON
at an endpoint you run. And for most of the list the honest answer is "do
nothing, Health Connect already carries it".

The single highest-value piece of work in this whole document is not a new
source at all. It is **`dataOriginFilter` plus a source picker**, which fixes a
live bug, costs about two days, and is the precondition for every other item.

---

## A. Other Fitbit and Google devices, through the path that already exists

### A1. What Google Health actually writes, and what it holds back

There is one authoritative list, and it is **per app, not per device**:
[Google Health and Health Connect](https://support.google.com/googlehealth/answer/14506680).
Google publishes a single read matrix and a single write matrix for the Google
Health app. It does **not** publish a per-device breakdown, and I could not
find one anywhere official. What varies by device is not the plumbing — it is
whether the sensor exists to produce a reading at all.

**Google Health WRITES to Health Connect:**

> Steps · Speed · Step cadence · VO2 max · Floors · Distance · Elevation
> gained · Exercise · Exercise route · Total calories burned · Body
> temperature · Sleep session · **Sleep stages** · Skin temperature · Blood
> glucose · Heart rate · Heart rate variability · Respiratory rate · Resting
> heart rate · Weight · Body fat % · Hydration · Nutrition · plus cycle and
> medical-record types.

**Google Health READS but does not write:**

> **Oxygen saturation** · Active calories burned · cervical mucus · ovulation
> test · sexual activity.

Two of those lines matter a great deal here.

**Blood oxygen is confirmed from Google's own documentation.** The handover
established empirically — via `dumpsys package com.fitbit.FitbitMobile` showing
`WRITE_OXYGEN_SATURATION: granted=false` — that SpO2 never arrives. Google's
support page now says the same thing from the other direction: oxygen
saturation is on the read list and absent from the write list. **This is not an
Air limitation and never was.** It is true of every Fitbit and every Pixel
Watch. The Oxygen tile will be empty on a Charge 6, a Sense 2 and a Pixel Watch
4 exactly as it is on the Air. The empty-metric copy on `HealthVitalsScreen.kt`
is correct and should stay, but it should stop implying the band is at fault.

**Active calories burned is read-only too**, which is why `HealthReader` uses
`TotalCaloriesBurnedRecord` and is right to. Do not "improve" that to active
calories: nothing writes it.

**Active Zone Minutes and Daily Readiness are not Health Connect types at all.**
AZM has no record type; Google's own consumer app shows a readiness score
([support](https://support.google.com/googlehealth/answer/14236710)) that is
exposed through no API. The nearest Health Connect equivalent to AZM is
`ActivityIntensityRecord`, added in `connect-client:1.2.0-alpha01` — see A5.

### A2. Device capability, by class

The plumbing claim above is verified. **The per-device sensor table below is
from product specifications and general knowledge, and I could not re-verify
each line against a 2026 source in this session — treat it as a planning
sketch, not a citation.** What is verified is the shape of the argument: the
record types are identical across the range, and only the sensor presence
varies.

| Device | HR / RHR | Sleep + stages | HRV | Resp. rate | Skin temp | SpO2 | GPS → Distance | ECG / EDA |
|---|---|---|---|---|---|---|---|---|
| **Fitbit Air** | yes | yes | yes | yes | yes | sensor yes, **never reaches HC** | no — distance is step-derived | no |
| Fitbit Luxe | yes | yes | yes | yes | yes | same | no | no |
| Inspire 3 | yes | yes | yes | yes | yes | same | no (connected GPS) | no |
| Charge 6 | yes | yes | yes | yes | yes | same | **built-in GPS** | ECG + EDA |
| Sense 2 | yes | yes | yes | yes | yes | same | built-in GPS | ECG + EDA + cEDA |
| Versa 4 | yes | yes | yes | yes | yes | same | built-in GPS | no |
| Pixel Watch 1–4 | yes | yes | yes | yes | yes | same | **built-in GPS**, Exercise route | ECG (+ loss-of-pulse on later models) |

The practical consequences for Air Bridge:

- **Distance and Exercise route become real.** On the Air, `DistanceRecord` is
  step-derived and `splitsOf` in `SessionReader.kt` usually has fewer than four
  segments to work with, so it returns no splits. On a GPS watch there will be
  many short segments, splits will populate, and `SessionDetail.splits` will
  suddenly be exercised for the first time on real data. **This is untested
  code about to run.** The interpolation logic looks sound but has never seen a
  dense input.
- **`ExerciseRouteRecord` arrives and nothing reads it.** A GPS watch writes
  routes; Air Bridge has no map and no permission for it. That is a gap, not a
  bug.
- **ECG and EDA produce nothing.** Health Connect has no ECG or EDA record
  type. A Sense 2's two headline sensors are invisible here, as they are to
  every Health Connect reader.
- **Sampling density changes by an order of magnitude.** This is the one that
  will actually break something — see A3, `HealthZones`.

### A3. How much of Air Bridge is implicitly Air-specific?

I audited every file in `health/` plus `ui/HealthState.kt`,
`ui/HealthMeasure.kt` and `Band.kt`. The result is better than expected: the
**reader is genuinely device-agnostic** — it asks for record types, never for a
device — and the Air-shaped assumptions are concentrated in a handful of
constants and a lot of prose. Here is every one of them.

---

#### Finding 1 — **no `DataOrigin` anywhere. This is a live bug.**

`grep -rn "DataOrigin\|dataOrigin\|metadata\.device\|dataOrigins" app/src` returns
**nothing**. Not one match.

Every read in
[`HealthReader.kt`](../../app/src/main/kotlin/com/srineere/airbridge/health/HealthReader.kt)
and
[`SessionReader.kt`](../../app/src/main/kotlin/com/srineere/airbridge/health/SessionReader.kt)
constructs its `ReadRecordsRequest` and `AggregateRequest` with a
`timeRangeFilter` and nothing else. The app therefore reads the *union of every
writer on the phone* and presents it as one person's band.

This is already wrong on a single-Air phone. From Google's
[read-data guide](https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data):

> Starting with the Health Connect update in **June 2026**, on-device steps are
> attributed to a device-specific **Synthetic Package Name (SPN)** instead of
> the generic `"android"` package name.

and

> If your app reads aggregated step counts using `aggregate` and doesn't filter
> by `DataOrigin`, **on-device steps are automatically included in the total**.

Health Connect counts the phone's own pedometer whenever any app holds
`READ_STEPS` — which Air Bridge does. So `stepsToday`, `stepsHourly`,
`stepsNow()` and therefore **the step-goal buzz and the hourly nudge** are
running on phone steps plus Air steps. The goal fires early. The handover lists
"confirming the goal pulse on the wrist" as unwitnessed; when it is witnessed,
expect it to fire before the band says 7,500.

**On deduplication, the honest answer:** Health Connect's `aggregate()` does
have dedup logic and Google recommends it precisely for this —

> For cumulative types like `StepsRecord`, use `aggregate()` instead of
> `readRecords()` to **avoid double counting from multiple sources**… The
> aggregation API also contains logic to handle duplicate records.

— but Google does **not** publish a cross-app priority list the way Google Fit
did, and the exact cross-origin semantics are not specified publicly. **I could
not confirm what `aggregate()` does when two different apps write genuinely
overlapping step intervals**, and I would not design around a guess. The safe
reading: aggregation protects you from the same app writing a record twice; it
does not pick a winner between two vendors who both think they own your steps.
**That arbitration is the reader's job**, which means it is Air Bridge's job.

Air Bridge is well placed for this, as it happens: it already uses `aggregate`
for every cumulative type (steps, calories, nutrition macros) and `readRecords`
only for instantaneous ones (HRV, respiratory rate, SpO2, weight, sleep,
sessions, food). That is the right split. It just needs the filter.

#### Finding 2 — `ASSUMED_MAX_HEART_RATE = 185`, hardcoded

[`HealthZones.kt:28`](../../app/src/main/kotlin/com/srineere/airbridge/health/HealthZones.kt).
There is no age setting and no way to change it; `Goals` holds steps, sleep,
energy, protein and active minutes, and no heart-rate ceiling. 185 is 220 minus
35. Every zone boundary on the Heart screen is drawn from it. The constant's
own KDoc says it is "the one number on the Heart screen worth replacing with a
measured figure" — that is still true, and it becomes more visible the moment
a device that computes its own zones is paired, because the two will disagree
on screen.

Not Air-specific exactly, but it is the kind of thing a second device exposes.

#### Finding 3 — `maxGap = Duration.ofMinutes(5)` is tuned to the Air's cadence

`timeInZones` in `HealthZones.kt` credits the interval between two samples to
the earlier one and **discards any gap over five minutes**. That is calibrated
to how often Google Health writes the Air's heart rate.

A Pixel Watch in a workout writes at a far higher rate; a Whoop writes a sleep
summary and little else; an Oura ring writes heart rate at **one-minute
intervals** ([Oura](https://ouraring.com/blog/data-sharing-partner-integrations/)).
Five minutes is a reasonable number for all of them, but it was chosen for one
device and nothing says so. Worse, a device that samples only during workouts
produces a day made almost entirely of >5-minute gaps, and `timeInZones` will
return a nearly empty map that the Heart screen will draw as "you did nothing
today". Make it a named constant with a comment, and consider whether it should
scale with observed median sample spacing.

#### Finding 4 — `exerciseName()` knows eight exercise types

[`HealthActivityScreen.kt:365`](../../app/src/main/kotlin/com/srineere/airbridge/ui/HealthActivityScreen.kt),
and its own comment admits it: *"the handful this band produces are worth
naming properly"*. Walk, Run, Cycle, Hike, Swim, Strength, Yoga, else
"Workout". Health Connect defines roughly eighty `EXERCISE_TYPE_*` constants. A
Garmin or Pixel Watch user will see "Workout" for rowing, elliptical, HIIT,
pilates, tennis, skiing and most of the rest.

Mitigated by the fact that `exerciseName()` prefers `title` when the writer
supplied one, and Garmin and Samsung generally do. Still a visible wart.

#### Finding 5 — the prose says "your band" and "Google Health" everywhere

Twenty-plus user-facing strings name Google Health as *the* writer. The clearest
cases:

| File | Line | Text |
|---|---|---|
| `HealthScreen.kt` | 643 | "Google Health writes nights once your band syncs." |
| `HealthScreen.kt` | 1454 | "Read from Health Connect, written there by Google Health." |
| `HealthSleepScreen.kt` | 89, 248 | "No nights yet. Google Health writes them…" / "Stages come from Google Health…" |
| `HealthEnergyScreen.kt` | 68 | "Google Health writes them once your band syncs." |
| `HealthHeartScreen.kt` | 83 | "No heart rate recorded today yet. Google Health writes it…" |
| `HealthHrvScreen.kt` | 69–70 | "Your band records it overnight, and Google Health passes it on…" |
| `HealthActivityScreen.kt` | 106, 239 | "Google Health writes them to Health Connect." / "the Air already reports them…" |
| `HealthBalanceDayScreen.kt` | 92 | "Burn arrives from Google Health as your band syncs." |
| `HealthTrendScreen.kt` | 121 | "Seeing it inside Google Health is not the same as…" |
| `WidgetsScreen.kt` | 64 | "…whatever Google Health last wrote there." |
| `HealthVitalsScreen.kt` | 186 | comment: "Google Health reads it off the band but does not pass it on" |

Two of these are *more* correct than they look and should survive any rewrite.
`HealthVitalsScreen`'s SpO2 explanation is now confirmed by Google's own
support page (A1), and `HealthEnergyScreen.kt:302` already says the right
thing: *"Eaten is whatever any app writes to Health Connect."* That sentence is
the model for the rest.

The rest should name **the source actually seen**, not a vendor. "Nothing has
written a night yet" is true on every phone; "Google Health writes them" is
true on one.

#### Finding 6 — `SleepScore` is fine, but its inputs vary more than it knows

[`SleepScore.kt`](../../app/src/main/kotlin/com/srineere/airbridge/health/SleepScore.kt)
is the best-defended code in this audit and needs **no structural change**. It
already handles the thing that varies:

- `Night.hasStages` is `(light + deep + rem) > 0`, and a night without stages
  scores on three parts with `basis` saying so.
- `toNight` maps `STAGE_TYPE_SLEEPING` into `Light` and leaves `STAGE_TYPE_UNKNOWN`
  out of the totals while keeping it in `segments`.
- `MINIMUM_NIGHTS = 5` guards every component that needs a personal baseline.

Two things to know before a second source arrives.

**`sleepScoreOf` mixes nights from different writers without noticing.** It
builds its "usual" band from `history`, which is every `SleepSessionRecord` in
the window regardless of origin. A fortnight of Air nights followed by a
fortnight of Oura nights gives a baseline that is the average of two devices'
measurement biases, and the score will read as a change in the sleeper when it
is a change in the hardware. **A per-source baseline is the correct fix** and
it is cheap: group `history` by `DataOrigin` before computing the band.

**Who actually supplies stages** (all vendor-confirmed):

| Writer | Stages to Health Connect? | Source |
|---|---|---|
| Google Health (Fitbit, Pixel Watch) | **yes** — "Sleep stages" on the write list | [support.google.com](https://support.google.com/googlehealth/answer/14506680) |
| Samsung Health | **yes** — "sleep session *and* sleep stage" in the official mapping | [developer.samsung.com](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect) |
| Garmin Connect | **yes** — "sleep stages" named in the shared-wellness list | [notebookcheck](https://www.notebookcheck.net/Garmin-officially-reveals-Google-Health-Connect-support-for-wearables.1054577.0.html) |
| Polar Flow | **yes** — "sleep with stages" | [support.polar.com](https://support.polar.com/en/flow-app-health-connect) |
| Oura | **probably** — Oura's blog says sleep including "stages"; not stated at record-type level | [ouraring.com](https://ouraring.com/blog/data-sharing-partner-integrations/) `UNCONFIRMED` |
| Whoop | **unknown** — 2023 reporting says "sleep summary". Whoop's own support article is a JS-only page that could not be read. `UNCONFIRMED`, and it is the load-bearing unknown for Whoop | [gadgetsandwearables](https://gadgetsandwearables.com/2023/07/14/whoop-health-connect-integration/) |

If Whoop writes summaries only, `hasStages` goes false, the Stages component
goes null, and the score drops to three parts — which is exactly the designed
behaviour. **The code already handles the worst case.** That is worth saying
out loud.

#### Finding 7 — the reader hardcodes "last of the night" folding

`HealthReader` folds HRV, respiratory rate, SpO2 and weight with
`rows.last()` per day. The HRV comment says this is "the one a tracker means by
'your HRV'", which is true for an overnight-reading band. A device that takes
spot HRV readings through the day makes `last()` an arbitrary choice. Low
priority; note it.

#### Finding 8 — things that are correctly *not* Air-specific

Worth recording so nobody "fixes" them:

- `HealthConnect.readPermissions` is a set of record types. No device, no
  package.
- `records()` already tolerates `""` as a page token, which the comment
  attributes to "some providers" — that is already multi-provider thinking.
- `HealthWriter` writes `Metadata.manualEntry()`, so its own records are
  correctly distinguishable from measured ones by any reader including itself.
- `Tiles.kt` is pure layout, keyed by metric. A new source needs no tile change.
- `HealthTrend.kt` is arithmetic over `List<Reading>`. Source-blind by
  construction.
- `Night`, `Session`, `FoodEntry` and `Reading` are all vendor-neutral shapes
  with every field optional but time.
- The `Log.i("AirBridge", "health: missing=…")` lines log granted-but-empty
  types. That diagnostic becomes *more* valuable with multiple sources, not
  less.

### A4. The concrete code-change list

Ordered by value. Rough sizes assume one person who knows this codebase.

**Must do — fixes a bug that exists today**

1. **Add `dataOriginFilter` to every read.** Thread a `Set<DataOrigin>?` through
   `HealthReader.read`, `stepsNow`, `dailyTotals`, `hourlyTotals`, `total`,
   `records`, and the five `SessionReader` entry points. `null` means "every
   source", which preserves today's behaviour until a source is chosen. **~half
   a day.**
2. **Enumerate the sources actually present.** `HealthConnect` gains
   `suspend fun writers(client, types): Set<DataOrigin>` — read a small window
   of each type, collect `record.metadata.dataOrigin`, and resolve each package
   name to an app label and icon via `PackageManager` (`InstalledApps` already
   has the caching pattern for this and the hard-won lesson about not reading
   it on a first frame). **~half a day.**
3. **Handle the SPN case explicitly.** On-device steps are no longer package
   `"android"`. Either exclude phone steps from the band's figures, or label
   them "Your phone" and let the user choose — but decide, rather than silently
   summing. Note `getCurrentDeviceDataSource()` is a framework API not yet in
   the Jetpack library and needs `compileSdkExtension = 22`; matching on both
   `"android"` and the SPN is the documented approach. **~half a day, plus a
   decision.**

**Should do — makes a second device honest**

4. **Per-source sleep baselines** in `sleepScoreOf`: group `history` by origin
   before `bandOf`. **~2 hours including a test.**
5. **Expose max heart rate.** Add `maxHeartRate: Int?` to `Goals` (default
   null → `ASSUMED_MAX_HEART_RATE`), a row on the Goals screen, and plumb it
   into `zoneOf`/`timeInZones`/the Heart screen. The Goals screen already names
   the published source of every default, so it has the right idiom. **~3 hours.**
6. **Widen `exerciseName()`** to the common forty or so exercise types.
   Mechanical. **~1 hour.**
7. **Name `maxGap`** and document that it is a sampling-cadence assumption, not
   a fact about people. **~15 minutes.**
8. **Rewrite the twenty-odd "Google Health writes…" strings** to name the
   observed source, or to say "nothing has written one yet" when there is no
   source. Keep `HealthEnergyScreen.kt:302` as the pattern and keep the SpO2
   explanation, which is now confirmed by Google's own docs. **~half a day,
   mostly careful writing.**

**Nice to have**

9. **Provenance in the UI** — see section C.
10. **Exercise route**, if a GPS device is ever actually used. New permission,
    new screen, a map dependency this app does not currently carry. **Days, not
    hours.** Out of scope unless asked for.
11. **Exercise the splits path** against real GPS session data before trusting
    it. Not a change; a test.

### A5. `connect-client:1.1.0` versus the 1.2 alphas

The handover pins 1.1.0 and says the 1.2 alphas "demand AGP 9 and
`compileSdk 37`". **The published release notes do not say that.** Reading
[the androidx changelog](https://developer.android.com/jetpack/androidx/releases/health-connect)
for 1.2.0-alpha01 through alpha06 (latest: alpha06, 26 August 2026), the only
toolchain note in the whole 1.2 line is alpha05 *lowering* the floor to
`minSdk 24`. The Android-16-SDK requirement the handover is probably
remembering belongs to the **Personal Health Records APIs**, which shipped in
`1.1.0-beta02` — i.e. they are in the 1.1.0 line, and compiling against them is
what pulls the SDK requirement in, not 1.2 itself.

**Flagging that as a possible stale note rather than asserting it is wrong** —
a real upgrade attempt may well have hit something the changelog does not
mention, and there is no reason to doubt a trap that cost somebody time. But it
is worth one re-test, because of what 1.2 contains:

| Added in | What | Worth it here? |
|---|---|---|
| 1.2.0-alpha04 | **Matchmaking APIs** — `checkIfMatchmakingIsPossible()` and `createMatchmakingIntent()`: "help users **discover and connect compatible data sources** that can provide the health data your app needs" | **This is literally the feature this document is about.** Google shipped the source-discovery onboarding flow. It would replace a hand-built source picker with a system one. |
| 1.2.0-alpha01 | **Activity Intensity API** (Android 14+) | The nearest thing Health Connect has to Active Zone Minutes — a real new metric, and a candidate 14th tile. |
| 1.2.0-alpha01 | Skin temperature **backwards compatibility** | Skin temp currently needs Android 15+ to land. This would widen the Skin tile's reach on older phones. |
| 1.2.0-alpha01 | Mindfulness backcompat | New category; no tile for it. Ignore. |
| 1.2.0-alpha03 | `getChanges(token, pageSize)`; richer `ExerciseSessionRecord`/`ExerciseSegment` fields | Marginal. Note `getChanges` itself already exists in 1.1.0 — see section C. |
| 1.2.0-alpha02 | New `Device` type enums | Marginal, but relevant if provenance badges name device types. |

**No new record types** that the Health tab is missing, apart from Activity
Intensity. **Recommendation: stay on 1.1.0 for the work in A4 — none of it
needs 1.2 — and revisit when Matchmaking reaches beta.** Building a source
picker by hand two months before Google ships one would be a waste.

### A6. The Device tab, Alerts, and the BLE transport

Health is portable. **Everything else is not, and the asymmetry is total.**

The `:air` module is a vendored, reverse-engineered Golden Gate → DTLS → CoAP
transport with two prebuilt `.so` files, arm64-only, carrying one device's
pairing model and one device's quirks. The haptics are not even a feature — a
pulse is the *side effect* of writing a vibration-strength setting, and the
held-session ring exists because that side effect costs a full handshake. The
`/settings` one-byte selector, `AIR_NOT_FOUND`, `HEART_RATE_TIMEOUT` and the
45-second `OPERATION_TIMEOUT_MS` are all Air facts.

So, scoped honestly:

| Surface | What "another band" means |
|---|---|
| **Health tab** | Nothing, or at most the day of work in A4. It reads Health Connect. |
| **Live heart rate** (`HealthMeasure.kt` → `Band.kt` → `AirLink`) | A per-device transport. Some bands expose the standard BLE Heart Rate Service (0x180D) and would be *easier* than the Air; most Fitbits and all Pixel Watches do not expose anything standard at all. **Per-device reverse engineering, indefinite.** |
| **Device tab** (battery, signal, device info, Buzz) | Four CoAP reads against Air-specific resources. Per-device, days to never. |
| **Alerts half** (the whole relay: rules, callers, quiet hours, ring cadence) | The rule engine, identity hashing and scheduling are device-neutral. The **delivery** is 100% Air. Everything above `AirLink` survives; `AirLink` itself is the whole problem. |
| **`Band.kt`'s single-session queue** | Models the Air's one-DTLS-session-at-a-time constraint. A device with a normal GATT stack would not need it — but `measureHeartRate`'s deference to `CalendarRelayService.holdsBand` assumes it throughout. |

The Device board's unbuilt **"Pair a different band"** is therefore a transport
abstraction, not a screen: an `AirLink`-shaped interface with per-device
implementations, each one its own reverse-engineering project. **Treat Health
and Alerts as two different products with a shared shell.** Health can go
multi-vendor this month. Alerts cannot go multi-vendor at all without a second
protocol, and there is no reason to let that stop the first.

---

## B. Other platforms as sources

### B1. The three findings that change the question

**1. The Fitbit Web API no longer exists.** Per
[dev.fitbit.com](https://dev.fitbit.com/build/reference/web-api/): support
ended 30 September 2026, and **"on October 30, 2026 the Fitbit Web API will be
turned off… will no longer function or sync data to or from Fitbit users."**
Google's migration pages cite 2 November for the same event — the dates
conflict by three days and it does not matter, because **today is 4 November**.
Do not plan anything against `api.fitbit.com`.

Its replacement is the **[Google Health API](https://developers.google.com/health/about)**
(`health.googleapis.com/v4/`), Google OAuth, and **all its scopes are
Restricted, requiring a privacy and security review**. In Testing mode you get
100 allowlisted users and **refresh tokens that expire**, so a personal
integration means periodic re-auth. Going beyond that means OAuth verification
plus a third-party security review.

What it would actually buy Air Bridge over Health Connect:

| | Google Health API | Health Connect | Verdict |
|---|---|---|---|
| **Active Zone Minutes** | yes (`active-zone-minutes`) | no such type | **API-only win** |
| **SpO2** | yes (`oxygen-saturation`, `daily-oxygen-saturation`) | **read-only into Google Health; never written out** | **API-only win** — the fix for the empty Oxygen tile |
| **Daily Readiness** | **no** | no | **Unavailable anywhere.** Corroborated by [open-wearables #1470](https://github.com/the-momentum/open-wearables/issues/1470) |
| **Fitbit Sleep Score** | **no** | no | **Unavailable anywhere** |
| HRV, resp. rate, VO2max, skin temp | yes, often intraday | yes, daily summary | API adds granularity only |

So: Air Bridge's two real gaps — SpO2 and AZM — **are** fixable via the Google
Health API, and the two most-asked-for Fitbit figures, Readiness and Sleep
Score, **are not available through any API at all**. If those were motivating
the work, drop them now.

The cost is a cloud OAuth flow, a restricted-scope review, token refresh in a
sideloaded app, and a permanent network dependency in a tab that is currently
entirely local. **For two metrics.** It is the single worst effort-to-value
ratio in this document that is nonetheless technically possible.

**2. Garmin's cloud API is closed to new applicants.** Garmin's own forum
response: *"The application form for new API access requests is currently
unavailable"* while they *"complete updates to the Garmin Connect Developer
Program"*
([forums.garmin.com](https://forums.garmin.com/developer/connect-iq/f/discussion/434798/cannot-access-developer-program-application-form-under-construction)).
Independently tracked: the form came down in spring 2026, an applicant from 2
March was still pending on 11 September, and there is **no announced restart
date**
([the5krunner, updated 24 Oct 2026](https://the5krunner.com/2026/09/14/garmin-developer-api-access-paused/),
[Terra, 7 Sep 2026](https://tryterra.co/blog/garmin-connect-developer-program-pause)).
One undated third-party doc claims the program has reopened with tiers; it
contradicts everything else and is `UNCONFIRMED`. Even when open, access needs
a registered legal entity and a company-domain email. **Do not plan on it.**

**3. Google Fit is a dead end in both directions.** Developers **cannot sign up
for the Fit APIs as of 1 May 2024**, and Google says plainly *"There is no
alternative to the Fit REST API"*
([migration FAQ](https://developer.android.com/health-and-fitness/health-connect/migration/fit/faq)).
Support runs "until the end of 2026" with no published switch-off date; as of
today they appear to still respond. The Fitbit app became the Google Health app
on 19 May 2026 and Fit is being folded into it. **Nothing to add here, and any
Apple→Fit→Health Connect bridge dies with it.**

### B2. The comparison table

Effort assumes a developer who knows this codebase. "Weekend" means a weekend.

| Platform | Route into an Android app | Data it would add | Auth friction | Effort | Verdict |
|---|---|---|---|---|---|
| **Pixel Watch / Charge 6 / Sense 2 / Versa 4 / Inspire 3 / Luxe** | **Health Connect, already working** | Everything the tab already draws, plus real GPS distance and routes | none | **zero** | **Already supported.** Ship A4 and say so. |
| **Polar** | **Health Connect.** 14 types written by Flow 6.22+: active/total calories, distance, exercise + **route**, HR, height, **SpO2**, RHR, **sleep with stages**, speed, steps, **VO2 max**, weight ([support.polar.com](https://support.polar.com/en/flow-app-health-connect)) | **The richest HC writer of any vendor — and the only one that writes SpO2**, which Google Health will not | **none** | **zero** | **Best value on the list.** A Polar user's Oxygen tile fills in. Nothing to build. |
| **Samsung Health** | **Health Connect** (official [mapping, Feb 2025](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect)): steps, SpO2, blood glucose/pressure, exercise + calories/distance/HR/power/speed/VO2max, HR, nutrition, **sleep + stages**, weight/body fat/BMR/height | Everything the tab draws except HRV, resp. rate, skin temp — **explicitly held back**, along with sleep apnea and energy score | **none** | **zero** | **Supported now.** ⚠️ An unresolved [community thread](https://community.samsung.com/us/conversations/Samsung-Apps-and-Services/Samsung-Health-Not-Writing-Steps-Data-to-Google-Health-Connect/us_3483502) reports Samsung Health silently stopping step writes, Feb–May 2026, no fix, thread closed. |
| Samsung Health **Data SDK** | SDK, publicly downloadable, **runs on non-Samsung phones**, no Samsung device needed | 25 read types incl. **skin temperature, sleep apnea, energy score, goals** — things HC never sees | **partner approval required to ship.** Unapproved signature → `AuthorizationException`; developer mode is for debugging only and Samsung says not to show users how ([process](https://developer.samsung.com/health/data/process.html)) | ~1 week + indefinite wait | **No.** Approval timeline and hobby-app eligibility are undocumented. HC gives most of it for free. |
| **Oura** | **Both.** HC writes ([official](https://support.ouraring.com/hc/en-us/articles/10786105824531)): active calories, distance, exercise, steps, height, weight, **sleep**, HR (1-min), **HRV**. Held back: readiness, sleep score, stress, **SpO2**, resp. rate, temp. Cloud: **API v2**, OAuth2, **10 users without approval** | API adds `daily_readiness`, `daily_sleep` score, `daily_stress`, `daily_spo2`, workouts | **low.** ⚠️ **Personal access tokens were removed** (Oura docs label them *"[Deprecated]"*; the spec says no longer usable — date disputed, Dec 2025 vs Aug 2026, `UNCONFIRMED`). OAuth2 only now | HC: **zero**. API: **a weekend** | **Best cloud candidate.** Two restrictions, both third-party readings of the agreement and `UNCONFIRMED`: Gen3+ **needs an active membership**, and **sending Oura data to an AI model is prohibited regardless of user consent** ([Terra, Sep 2026](https://tryterra.co/blog/oura-api-ai-apps-access-permissions-commercial-restrictions)). |
| **Whoop** | **Both.** HC: bidirectional since Jul 2023 — cardio workouts, sleep summary, **RHR**, **resp. rate**, **blood oxygen**. **Steps are not written.** Cloud: **API v2**, OAuth2, **Sandbox tier = 10 members, self-serve, no request** | `recovery` carries `recovery_score`, `resting_heart_rate`, `hrv_rmssd_milli`, **`spo2_percentage`**, **`skin_temp_celsius`** in one call. No HR time series | **low** for ≤10 users; tiered review beyond, needing 50% of your tier connected to advance ([app approval](https://developer.whoop.com/docs/developing/app-approval)) | HC: **zero**. API: **a weekend** | **Good.** Rate limits are generous and published: 100/min, 10,000/day. ⚠️ **Whether sleep *stages* reach HC is unconfirmed** — the official article is a JS page that would not render, and the 2023 list is three years old. |
| **Garmin** | **Health Connect only.** One-way since Jul 2025, **Android 14+**, opt-in per type: calories, cadence, distance, elevation, HR, speed, steps, strokes, body fat, floors, **sleep stages**, weight | Everything the tab draws except HRV and SpO2 | **none** for HC | **zero** | **Supported now, via HC.** Held back (secondary sources — Tom's Guide and Android Central both blocked WebFetch): training load, intensity minutes, **HRV status**, Body Battery, stress. **SpO2 and resp. rate absent from every verified list — an `UNCONFIRMED` negative.** |
| Garmin **Health API** | Partner program | Body Battery, stress, pulse ox, respiration | **closed — applications paused, no restart date** (B1) | n/a | **No.** |
| Garmin via `python-garminconnect` | Unofficial; **Python**, so it needs a server | Everything Garmin Connect holds | holds the user's **username and password**; maintainers note the refresh token *"can provide persistent account access"* and the library **cannot revoke it** | days + a server | **No.** Password-based and server-side. Disqualifying for a sideloaded Android app. |
| **Withings** | **Both.** HC: weight, steps, sleep, HR, basic body composition (**no official type list found — `UNCONFIRMED`**). Cloud: **Public Cloud API, free, open to individuals, self-serve OAuth2** | Body composition detail, blood pressure | **lowest of any cloud API here** | HC: zero. API: **a weekend** | **Good, with a 2026 catch:** since **13 Oct 2026** automatic sync to third parties **including Health Connect** needs **Withings+ (~$99.50/yr)** for accounts created on or after that date ([Android Authority](https://www.androidauthority.com/withings-apple-health-google-health-connect-sync-paywall-3724240/)). Older accounts keep it free. The API *appears* unaffected — **not confirmed**. |
| **Suunto** | **Health Connect only, and thin.** Third-party sources agree on workouts, steps, distance, HR; **sleep and HRV not written**. **No official Suunto page found — `UNCONFIRMED`** | little the tab does not have | n/a | zero | **HC or nothing.** The API is explicitly closed: a forum post quotes the portal — *"We currently don't offer the API access for personal use"*. |
| **Zepp / Amazfit** | **Health Connect only.** Steps, HR, sleep, calories, **SpO2 on higher-end models**; coverage is **model-dependent**. **No official Zepp page found — `UNCONFIRMED`, all third-party** | SpO2, on some models | n/a | zero | **HC or nothing.** No public cloud API; Zepp's developer portal is for on-watch mini-apps, and Zepp staff state history extraction *"isn't available"*. |
| **Xiaomi / Mi Fitness** | **Health Connect only**, since June 2025 (announced at Google I/O 2025). Steps, HR, sleep, calories reported. **No official type list — `UNCONFIRMED`** | little | n/a | zero | **HC or nothing.** No public API found. |
| **Huawei** | **Neither, practically.** Two independent 2026 checks find **no official Health Connect path**; workarounds route through Google Fit, which dies at end of 2026. Huawei Health is not on the Play Store | n/a | HMS Health Kit: 100-user test cap, verification ~15 working days, **HR/SpO2/BP/glucose manually reviewed**, lifting the cap needs **enterprise registration** | high | **Skip.** Highest friction, lowest payoff. |
| **Strava** | **Both, and both are poor.** HC is **bidirectional but tiny**: Strava writes only **time, distance and calories from GPS activities**, and reads weight back ([support.strava.com](https://support.strava.com/en-us/articles/15401554-health-connect-and-strava)) | essentially nothing the tab lacks | **worst terms on the list.** A **Strava subscription is now required to create an app** (~$79.99/yr, since mid-2026); new apps start at **athlete capacity 1**, self-serve to 10, review beyond | n/a | **No.** Three deal-breakers in the [API policy](https://www.strava.com/legal/api_policy): **§6.2 — may not cache Strava Data beyond 7 days** (fatal for a local store); **§5.3 — blanket prohibition on use with any AI application**, explicitly including derived and anonymized data, RAG and grounding; **§5.5 — no persistent index, including vector stores.** Its HC write set is already free of all of this. |
| **Apple Health** | **No cloud API. None has ever existed.** Reading HealthKit requires an iOS app holding the entitlement — so every route needs an iPhone-side component | whatever the iPhone holds | — | — | **One-way import at best.** See B3. |
| **Fitbit Web API** | **Gone.** Switched off ~30 Oct 2026 | — | — | — | **Does not exist.** Successor is the Google Health API (B1). |
| **Google Fit** | **Closed to new developers since 1 May 2024**; APIs end 2026; app folding into Google Health | — | — | — | **Dead end.** |

### B3. Apple Health, specifically

Apple publishes no server API and never has. I could not get Apple to *state*
the negative — `developer.apple.com/documentation/healthkit` rendered
title-only — so the claim rests on absence plus unanimous third-party
assertion. **High confidence, no primary citation.** The structural fact is
what matters: reading Apple Health requires an iOS app with the HealthKit
entitlement, so **every route below needs an iPhone the user operates.**

Three routes, in descending order of realism:

1. **Health Auto Export (REST automation).** The only genuinely live route. The
   iOS app POSTs JSON to any URL the iPhone can reach, with custom headers
   (their documented example is a bearer token), one data type per automation
   ([docs](https://help.healthyapps.dev/en/health-auto-export/automations/rest-api)).
   Payload is a top-level `data` object with `metrics`, `workouts`, `ecg` and
   more; timestamps `yyyy-MM-dd HH:mm:ss Z`. **Premium-only** ($24.99 lifetime
   or $6.99/yr). Three hard limits, all from Apple's side and all documented by
   the vendor: scheduling is **best-effort** — *"does not guarantee that the
   app will open or run at that interval"* — it depends on Background App
   Refresh and dies under Low Power Mode; and **health data can only be read
   while the device is unlocked.** A field report calls it *"a bit flaky, mainly
   due to iOS limitations regarding background tasks"*, settling on ~10 metrics
   up to 4×/hour.

2. **Manual "Export All Health Data".** A ZIP containing `export.xml`, often
   `export_cda.xml`, an `electrocardiograms/` folder and `workout-routes/*.gpx`.
   **Apple publishes no schema and the structure has changed between iOS
   versions without notice.** Size reports vary wildly — one project measured
   **~72 MB zipped / ~644 MB of XML for a single year**. If this is ever
   parsed, parse it streaming; never DOM. One-shot and user-initiated.

3. **Bridges — nothing lands Apple → Health Connect continuously.** Health
   Sync lists many sources and **Apple Health is not among them on Android**.
   One open lead worth chasing: Google Health v5.05 (3 Aug 2026) added **two-way
   Apple Health sync**, but the coverage says *"Apple Health syncing remains
   exclusive to iPhone"*. **If** HealthKit data imported there lands in the
   Google Health cloud account, **and** the Android Google Health app writes
   that account's data to Health Connect, that would be a vendor-operated
   Apple→HC bridge with zero code. **Nothing confirms the Android leg carries
   imported data. `UNCONFIRMED` and the single most interesting open question
   in this document** — if it turns out to be true, Apple Health support costs
   nothing at all.

**Shape, if ever built:** an HTTPS receiver the user runs, a bearer token, a
JSON parser, and imported records written into Health Connect under Air
Bridge's own `DataOrigin` — at which point the existing reader picks them up
unchanged and every provenance mechanism in section C labels them correctly.
That is the elegant part. Everything before it is the problem.

### B4. Ranked by value ÷ effort

**Free — ship a sentence, not a feature**

1. **Polar.** Zero work, and the only vendor that writes SpO2. The Oxygen tile
   fills in.
2. **Garmin, Samsung, Pixel Watch, other Fitbits, Xiaomi, Zepp.** Already
   supported. The change is the A4 work plus telling people.

**A weekend each, if genuinely wanted**

3. **Oura cloud** — readiness, sleep score, stress, SpO2. OAuth2, 10 users
   self-serve. Watch the membership and no-AI restrictions.
4. **Whoop cloud** — recovery score, SpO2 *and* skin temp in one call. Sandbox
   tier needs no approval at all.
5. **Withings cloud** — the lowest-friction API here, and possibly the only way
   to get Withings data free on a new account after October 2026.

**A real project, for two metrics**

6. **Google Health API** — the only route to Fitbit SpO2 and Active Zone
   Minutes. Restricted scopes, security review, expiring refresh tokens in
   Testing mode, and a network dependency in a local tab.

**No**

7. **Samsung Data SDK** — partner approval of unknown latency for data HC
   mostly already gives you.
8. **Apple Health** — one-way, iPhone-side, best-effort. Unless the Google
   Health bridge lead pans out, in which case it is free.
9. **Strava** — a subscription, a 7-day cache limit and a blanket AI
   prohibition, for data Health Connect hands over free.
10. **Garmin Health API** — the door is shut.
11. **Huawei** — highest friction, lowest payoff.
12. **Fitbit Web API, Google Fit** — gone and going.

---

## C. Architecture

### C1. Is Health Connect a good enough universal adapter?

**For what Air Bridge actually does: yes, overwhelmingly.** It already
normalises nine vendors into one vocabulary the app speaks fluently. Nothing in
`HealthTrend`, `SleepScore`, `HealthZones` or `Tiles` knows or cares where a
`Reading` came from. That is not luck — the reader's shapes (`Night`,
`Session`, `FoodEntry`, `Reading`, every field optional but time) were designed
for a world where writers differ, and they hold.

It is not sufficient for exactly three things, and it is worth being precise
about which:

- **Metrics with no record type.** Readiness, sleep scores, Body Battery, Whoop
  Strain, Active Zone Minutes. These are vendor computations, not measurements,
  and Health Connect is a measurement store by design.
- **SpO2 from a Fitbit.** Google Health reads it and will not write it. The only
  route is the Google Health API.
- **Anything from an iPhone.**

**So: do not build a source-abstraction layer yet.** Introducing a
`HealthSource` interface with `HealthConnectSource` as its only implementation
is speculative generality, and it would put an adapter between the reader and
the twelve screens that currently enjoy a direct, well-shaped path. **Health
Connect *is* the abstraction layer.**

The shape to build towards instead, when a cloud source is actually added:

```
    screens ── HealthState ── HealthSnapshot        (unchanged)
                     │
                 HealthReader                        (unchanged shapes)
                     │
            ┌────────┴────────┐
    Health Connect      cloud importer
     (every vendor)     (Oura / Whoop / Google Health)
                              │
                     writes into Health Connect
                     under Air Bridge's DataOrigin
```

**A cloud source should not become a second reader. It should become a
writer.** Fetch from Oura, write `OxygenSaturationRecord` and friends into
Health Connect tagged as Air Bridge's own, and the entire existing read path —
aggregation, dedup, permissions, provenance, the food log's delete-by-id —
works unchanged. One new permission set, one `WorkManager` job, no new
plumbing. The app is already a Health Connect writer; `HealthWriter` is the
precedent and `Metadata.manualEntry()` is the pattern.

The honest counter-argument: readiness and sleep scores have **no record type
to write them into**, so those genuinely need a side channel. That is a small
local table beside the snapshot, not an abstraction layer.

### C2. What the UI needs

**A source picker — but a lightweight one.** Not a wizard. The Device tab or
Goals screen gets a "Sources" row listing every `DataOrigin` found, with its app
icon, and a toggle each. Default: everything on, which is today's behaviour.
Note that `connect-client:1.2.0-alpha04`'s **Matchmaking APIs** are Google's
own answer to exactly this, so build the minimum now and watch that API.

**Per-metric priority — only where it is forced.** The honest rule: cumulative
types (steps, distance, calories, nutrition) **must** have exactly one chosen
source or they double-count, while instantaneous types (HR, HRV, sleep, SpO2,
weight) can safely merge, since the reader already folds them per day. So the
picker needs one global source list plus a per-metric override for the four
cumulative types. Four overrides is a screen; thirteen is a configuration
exercise nobody will complete.

**Provenance badges, following the existing idiom.** `HealthReader` already
carries `writtenAt` and `HealthScreen.kt:1449` already ages it — *"Read from
Health Connect, written there by Google Health."* That line is the hook.
Replace the hardcoded vendor with the observed one, and when a window holds two
sources say so: "Steps from Pixel Watch · last written 4 minutes ago". The tab
already has a place for this; it just needs the right noun.

**Conflict rules worth stating in the UI, not just in code:**

- A day with two step sources shows the chosen one, with a tappable note that
  another source also wrote that day. **Never silently sum.**
- A sleep night from a different source than the baseline it is scored against
  is scored against *its own* source's baseline (Finding 6), and the score's
  `basis` line — which already exists and already explains itself — gets a
  fourth thing it can say.
- Nutrition is the one place merging is *correct*: food comes from apps, not
  devices, and `mealsDaily` counting entries per day is already the right
  model.

### C3. The local store — there isn't one

**Checked, not assumed.** `HealthState` holds `data` in a
`mutableStateOf(HealthData())`, with `loadedAt` and `FRESH_MILLIS = 60_000`.
That is **in-memory only**, for the life of the process. There is no Room
database, no file, no cache. Every cold start re-reads thirty days from Health
Connect. The only things persisted anywhere are **settings**: `air_health.xml`
(goals, tiles, buzz settings) and `SavedFoods` — and `SavedFoods` exists only
because Health Connect has no food library, which the handover establishes and
three screens explain.

For a single live Health Connect provider this is the right design and the
handover defends it well: Health Connect is an IPC call per query and
rate-limits callers, so the tab loads once and only on request. `widen()` is
one-way because a 90-day read is a superset of a 30-day one.

**It stops being right the moment a cloud source exists**, for four reasons:

1. A network fetch cannot be on the critical path of drawing a tile.
2. Cloud data arrives on a schedule, not on a tab open.
3. Historical backfill is expensive and must happen once, not per launch.
4. Rate limits are real and published — Whoop is 100/min and 10,000/day.

**The recommendation, in order:**

- **Change C1 first and the store may not be needed at all.** If a cloud source
  writes into Health Connect, Health Connect *is* the store, and `HealthState`
  stays exactly as it is.
- **For the metrics with no record type** — readiness, sleep score, stress,
  AZM — add one small Room table, `(source, metric, day, value)`, read beside
  the snapshot. Tens of lines, not a layer.
- **If reads ever become expensive**, the change-log API is the answer and it
  **already exists in 1.1.0**: `getChanges(changeLogsToken)` turns the thirty-day
  re-read into an incremental sync. (1.2.0-alpha03 adds a `pageSize` overload;
  the base API does not need the upgrade.) Worth remembering, not worth doing
  now.
- **Whatever is added, keep `HealthState`'s own `CoroutineScope`.** The handover
  records what it cost to learn that a long read must not borrow a screen's
  scope, and a background sync is a longer read than any of them.

### C4. What not to do

- **Don't abstract over Health Connect while it is the only source.** It is the
  abstraction.
- **Don't write a second copy of anything Health Connect already holds.** The
  `HealthConnect` object's own KDoc has the rule: *"a workout already in Health
  Connect would be double-counted by a second copy."* That reasoning extends
  exactly to cloud imports.
- **Don't build a source picker by hand two months before Matchmaking ships**,
  beyond the minimum A4 needs.
- **Don't put Strava data anywhere near an LLM feature**, now or later. §5.3 of
  their policy covers derived, aggregated and anonymized data, RAG and
  grounding, and the easiest way to never breach it is to never ingest it.

---

## Recommended order of work

**1. Fix the bug that already exists** *(~2 days)* — A4 items 1–3.
`dataOriginFilter` on every read, source enumeration, and an explicit decision
about phone steps. This corrects today's step goal on today's phone and is the
precondition for everything else. Ship it on its own.

**2. Make the app tell the truth about itself** *(~1 day)* — A4 items 4–8.
Max heart rate on the Goals screen, per-source sleep baselines, the wider
exercise-name map, `maxGap` named, and the Google-Health prose rewritten to name
the source actually seen. Keep the SpO2 explanation; it is now confirmed by
Google's own documentation.

**3. Say so.** The README's Health section and the handover both read as though
the Air is the only possible source. One paragraph: *"The Health tab reads
Health Connect, so it works with any device whose app writes there — Pixel
Watch, Garmin, Samsung, Polar, Oura, Whoop, Withings. The Air-specific parts
are the Device tab, Alerts, and the live heart rate reading."* That paragraph is
worth more than most of the code in step 2.

**4. Verify against one non-Fitbit source.** The cheapest real test is a Samsung
Health or Polar user, because both write stages and need no approval, no token
and no code. Find out what actually breaks before building for it.

**5. Stop, unless something specific is wanted.** After steps 1–4 the Health tab
genuinely supports nine vendors. Everything beyond is a cloud integration whose
value is a handful of vendor-computed scores.

**6. If a cloud source is wanted**, take **Oura or Whoop**, in the shape of
C1: a `WorkManager` job that fetches and **writes into Health Connect** under
Air Bridge's own origin, plus a small Room table for the scores that have no
record type. A weekend. Whoop's Sandbox tier needs no approval; Oura's 10-user
limit needs none either.

**7. Revisit `connect-client` 1.2 when Matchmaking reaches beta** — and re-test
the AGP 9 / `compileSdk 37` claim, which the published changelog does not
support. Matchmaking is Google building the source picker from step 1.

**8. Chase one lead before anything else on this list** — whether Google
Health's August 2026 Apple Health import reaches Health Connect on Android. If
it does, Apple Health support costs nothing. If it does not, Apple is an
iPhone-side HTTP receiver and a flaky background task, and the answer stays no.

---

## Open items

Flagged `UNCONFIRMED` above; none blocks steps 1–4.

1. Whether Google Health's Apple Health import surfaces on Android/Health
   Connect — the one potentially free Apple→Android bridge.
2. Whether **Whoop writes sleep *stages*** or only summaries. Load-bearing for
   Whoop; the official article is a JS-only page.
3. Whether **Garmin writes SpO2 or respiratory rate** to Health Connect — an
   unconfirmed negative. Both Tom's Guide and Android Central blocked fetching.
4. Official Health Connect data-type lists for **Withings, Zepp, Xiaomi and
   Suunto** — all four rest on third-party sources only.
5. Whether the **Withings+ paywall** (13 Oct 2026) touches the free Public
   Cloud API as well as Health Connect sync.
6. Exact date Oura removed personal access tokens (Dec 2025 vs Aug 2026). The
   direction — PATs gone, OAuth2 only — is consistent everywhere.
7. Whether Oura's **membership requirement** and **AI prohibition** are in the
   current agreement text, or a third party's reading of it.
8. Whether the 1.2 alphas really require **AGP 9 / `compileSdk 37`**. The
   changelog does not say so; the handover does. One re-test settles it.
9. Exactly how `aggregate()` behaves when **two different apps write
   overlapping step intervals**. Google documents that dedup logic exists but
   not its cross-origin semantics. Filter rather than find out.
10. Apple's own primary statement that HealthKit has no server API — absence of
    evidence only.
11. Exact Google Fit API switch-off date. Never published.
