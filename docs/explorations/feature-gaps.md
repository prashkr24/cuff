# Feature-gap analysis — Air Bridge against the market

Written 4 November 2026. Research cut-off the same day.

This is a competitive read of Air Bridge against the health apps people actually
use, grounded in what the app already does. The inventory comes first on
purpose: a gap list without one is a wish list, and half the "missing" features
in a review of this app turn out to be present under a different name.

Two constraints shape every judgement below.

- **The band is a Fitbit Air.** Screenless, no GPS, no input of any kind back to
  the phone, and the only thing it can be made to do is vibrate.
- **The data comes from Health Connect**, written there by the Google Health
  app. A gap is only real if Google Health actually writes the record. Google's
  own table of what it syncs is the arbiter:
  <https://support.google.com/fitbit/answer/14506680>.

Where the handover has already settled a question, it is marked **already
decided against — see handover** and not argued with.

---

## 1. Inventory — what Air Bridge already has

### 1.1 What it reads and writes

Thirteen read types, one write (`health/HealthConnect.kt`):

| Read | Steps · SleepSession · HeartRate · RestingHeartRate · HeartRateVariabilityRmssd · RespiratoryRate · SkinTemperature · OxygenSaturation · ExerciseSession · Distance · TotalCaloriesBurned · Nutrition · Weight |
| --- | --- |
| **Write** | Nutrition only — the food log |
| **Extra grants** | `READ_HEALTH_DATA_HISTORY` (past 30 days) and `READ_HEALTH_DATA_IN_BACKGROUND` |

Every read is individually permission-guarded and individually wrapped, so a
refused type costs one metric rather than the tab. `HealthReader` logs which
types are granted-but-empty, because "nobody granted it" and "nobody wrote it"
look identical on screen.

Window: the tab loads 30 days; `HealthState.widen` re-reads further back and
never narrows. Per-session heart rate and sessions older than a week are read on
demand by `SessionReader`, not held in the snapshot.

### 1.2 Screens

| Area | What is there |
| --- | --- |
| **The grid** | 13 tile kinds, 5 sizes (S/M/W/L/XL) over 6 columns, reordered by arrows, resized, recoloured, removed. `packTiles` lays out whatever the stored list says. Edit screen previews against fixed sample data, never your own. |
| **Heart rate** | Today's trace with 1h/4h/8h/Today pills, drawn as per-minute min-max bands above 400 samples and as a line below. Zones, summary pills, clock-range caption, scrub readout. **Live measurement** from the band — the one figure Health Connect cannot answer. |
| **Resting heart rate** | Scatter with the usual band, 30-day trend. Opens a shared trend screen (line + histogram) that also serves HRV, breathing rate, skin temperature, blood oxygen and weight — six metrics, one screen. |
| **Sleep** | Score with four components, hypnogram, stage breakdown, per-night list. Selecting a night moves the whole card — score, efficiency, sleeping heart rate, hypnogram, headers. |
| **Steps** | Ring, goal bars, window control, by-week view, hourly "when you walk" pattern. |
| **Energy** | Burned against eaten, daily and hourly, plus a per-day balance screen. |
| **Nutrition** | Macros over a fortnight, food log grouped by meal, two-step logging (start from recent / saved / typed → one form), saved foods (Air Bridge's own store; Health Connect has no food library). |
| **Activity** | Seven-day sessions on the tile; all activities back a year on its own read; one session with splits, heart trace, calories, distance. |
| **Vitals** | Five nightly readings each as a position in its own band, plus a readiness **band**. |
| **Goals** | Steps, sleep minutes, energy kcal, protein grams, weekly active minutes — Air Bridge's own, because Health Connect has none. Each default names its published source. |
| **Alerts** | Relay switch, schedule (pause + quiet hours), calls / apps / people rules, step-goal buzz, hourly nudge, recent activity. |
| **Device** | Band, battery (read live over the transport), signal, Buzz, sync, permissions, widgets gallery, about, accent. |

### 1.3 Derived figures — the arithmetic the app already owns

All of it in `health/HealthTrend.kt`, `health/SleepScore.kt` and
`health/HealthZones.kt`, with tests, and none of it in the screens.

- **Trend** (`trendOf`, floor of three readings): average, latest, lowest,
  highest, **usual = interquartile range** (not mean ± SD, so one freak night
  cannot widen normal), recent = mean of last 7, `versusAverage`,
  **`change` = newest third minus oldest third** (not endpoints, not this week
  against last), and **smallest worthwhile change = 0.5 SD** — the HRV-guided
  training threshold. `latestIsUsual` and `recentHasMoved` fall out of those.
- **Coverage**: readings present against days covered, printed rather than
  hidden, because a 30-day average from 11 nights is a different claim.
- **Sleep score**: four components — Duration, Stages (deep + REM share),
  Consistency, Interruptions — each measured against **your own** nights, never
  a recommended figure. Minimum 5 other nights. Consistency averages bedtimes on
  the clock face. The usual band has a 5% floor so a consistent sleeper is not
  marked down for consistency. A component with nothing behind it is null and
  the score says what it stood on. **Never shown without its parts.**
- **Heart-rate zones**: four zones as a share of an assumed max of 185, **rest
  excluded entirely**, time credited to the earlier reading, gaps over five
  minutes credited to nothing.
- **Sleeping heart rate**: the 10th percentile of samples inside the night — not
  the mean, not the minimum.
- **Energy balance**: eaten minus burned, days with both halves only. Days with
  no food logged are left out, not counted as a deficit.
- **Three trends derived rather than read**, because Health Connect holds no such
  record: energy balance, kilocalories eaten, exercise minutes a day (rest days
  omitted, not zeroed).
- **Readiness as a band, never a number**: recent rolling mean against the
  baseline, with the smallest worthwhile change as the band width, and a sentence
  naming which vitals have actually moved. A 0–100 figure on top was rejected as
  a clinical claim.
- **Session splits**: kilometre crossings interpolated inside the distance
  segment they fall in; no splits at all rather than fabricated ones when the
  writer logged one block.

### 1.4 The two buzzes the app raises itself

Step-goal crossing (once a day, recorded **before** the pulse is sent) and an
hourly nudge at :50 when the hour is short, inside an active window. Both go
through the relay's one link, so neither fires while relaying is off, and
silence beats both.

### 1.5 What is explicitly not there

No export of any kind. No widgets behind the gallery (Glance unimplemented). No
journal, tags or annotations. No report screen. No streaks. No notifications
from the Health half other than the two buzzes. No correlation between any two
metrics. No weekly or monthly view that is not a chart window.

### 1.6 Decisions already settled — do not re-propose these

| Decision | Where |
| --- | --- |
| No composite scores beyond sleep; readiness stays a band | handover, "No composite scores beyond sleep" |
| Health Connect has no goals and Google's cannot be read | handover, "The Health tab" |
| Blood oxygen is empty because Fitbit never writes it | handover; confirmed by Google's own sync table |
| The status light cannot be driven | handover, "Two things this hardware cannot do" |
| Wrist taps cannot be detected — tap-to-silence is impossible | same |
| Dense charts are ranges, not lines | handover, "Dense charts" |
| Edit tiles uses arrows, not drag | handover, "Design" |
| Log food shows no macro targets (nothing stored one) | handover, "Design" — see note in §4.1, this has since become half-true |
| A second copy of a workout would be double-counted | `HealthConnect.kt` doc comment |
| `connect-client` stays at 1.1.0 | handover |

---

## 2. Per-competitor digest

### Whoop (5.0 / MG, 2025–26)

Everything is subscription-gated. The structure worth stealing is not the
sensors.

- **Strain 0–21, logarithmic**, from time in personalised HR zones plus an IMU
  muscular component. The saturating scale is the idea: 20→21 is far harder than
  10→11.
- **Recovery 0–100%** from HRV (RMSSD, measured in slow-wave sleep, weighted
  highest), resting HR, respiratory rate and sleep performance, all against a
  personal baseline. Green ≥67, yellow 34–66, red ≤33.
- **Sleep Need is published arithmetic**: `baseline + debt + recent strain −
  nap credit`, exposed field-by-field in their own API. The redesigned 2025 sleep
  score has four parts: hours vs needed, consistency (4-day rolling alignment),
  efficiency, sleep stress.
- **Journal → Impacts** is the most-praised feature in every review. A daily
  yes/no questionnaire over ~300 behaviours; a behaviour unlocks after **5 yes
  and 5 no within 90 days**; it then compares mean next-day Recovery with and
  without, bucketed significant / normal / negligible, with an explicit
  confounding warning.
- **Weekly and Monthly Performance Assessment** — Monday report, needs 5 days of
  data, shows strain-vs-recovery balance, the distribution of daily recoveries,
  and the top positive and negative behaviours.
- **Strain Coach** is a lookup from this morning's Recovery to a target Strain
  band. **Health Monitor** is a morning card of each vital against your typical
  range, colour-coded. Both are presentation.
- Sensor-bound and not transferable: Stress Monitor (needs daytime HRV),
  activity auto-detection, ECG and blood pressure on MG.

### Apple Health / Fitness (iOS 18 → 27, watchOS 26)

- **Vitals app**: heart rate, respiratory rate, blood oxygen, wrist temperature,
  sleep duration (HRV added on newer hardware), each marked Typical or Outlier
  against a range learned over ~7 nights. **Two or more metrics out of range
  produce one next-morning notification** listing possible causes — medication,
  altitude, alcohol, illness. A single outlier gets a marker and no interruption.
  This restraint is the design lesson.
- **Sleep Score (watchOS 26)**: duration 50, bedtime consistency 30,
  interruptions 20. Published structure, unpublished curves. Criticised for
  ignoring HRV and resting HR.
- **Training Load**: effort rating 1–10, auto-estimated from heart rate and
  user-editable days later; load = effort × duration; **last 7 days against the
  prior 28**, labelled Well Below / Below / Steady / Above / Well Above.
- **Trends**: last 90 days against the last 365, with a direction arrow and
  coaching text when something declines.
- **State of Mind**: momentary emotion or daily mood on a valence wheel, with
  optional labels (the feeling) and associations (the cause), reviewed against
  **Life Factors** — daylight, sleep, exercise, mindful minutes — as an overlay,
  not a statistic.
- **Move goal**: suggested weekly, never forced, and allowed to go *down*.
- **Readiness (iOS 27, late 2026)**: 0–10 with four labels, from recent
  activity, vitals and sleep score, listing its contributing factors.
- Not reachable here: Walking Steadiness and mobility metrics (phone IMU gait),
  medication interactions (licensed drug database), Health Records.

### Bevel

The best model in the market for *explaining and scoring*, and the closest
philosophical neighbour to Air Bridge.

- Publishes its **inputs** for every score while keeping weights private — which
  is exactly the posture `SleepScore` takes.
- **60-day rolling baselines** for resting HRV and resting HR, with the user's
  own number quoted beside today's.
- A visible **Calibrating** state, and explicit copy about which scores cannot be
  computed and why, when overnight data is missing.
- **Cardio Load** as acute (7 days) vs chronic (6 weeks), needing at least 10
  training days in 6 weeks before it leaves Calibrating.
- **Energy Bank**: a balance that carries between days, charging from sleep and
  rest, draining from stress and activity, with charging slowing near full.
- **Journal Insights** gated on **5 yes and 5 no**, over 90-day rolling data,
  shown against Sleep and Recovery separately, labelled "correlations, not
  cause-and-effect".
- Reads a health store only — no sensor of its own. Free core tier; $14.99/mo
  for the AI layer. Reviewers praise the data and fault the guidance.

### Gentler Streak

The non-punitive load model.

- **Activity Path**: a green band of recommended daily load with your line
  inside it. The upper dark edge is a caution line; below the lower edge for too
  long is undertraining. Views at 1, 10 and 30 days, and dragging left shows a
  4-day forward projection assuming rest.
- Load derives from **acute and chronic training load** plus age and sex, with
  per-workout effort from heart rate and duration and a **manual RPE override**
  — added precisely because HR under-reads strength work.
- **Rest never breaks the streak.** Statuses — Active, Sick, Injured, On a Break
  — pause nudges and are treated as intentional rather than a lapse.
- **No composite number anywhere.** It shows a state and a band. Reviewers with
  chronic illness single this out.
- Vitals are colour-banded against your normal range: green within, blue extra
  recovery, orange/red something may be off.

### The read-only-health-store class — Athlytic, Training Today, AutoSleep, HeartWatch

Architecturally identical to Air Bridge, and therefore the most directly
transferable category.

- **Athlytic**: Recovery from overnight HRV and resting HR against a **60-day
  rolling average**, HRV weighted slightly higher; 50% explicitly means "near
  your baseline", not "average person". Exertion 0–10 from time above a
  personalised threshold. **Target Exertion** and **Target Sleep** are derived
  *prescriptions*, not just scores — Target Sleep uses today's recovery,
  exertion, last night, a **rolling 14-day sleep debt** and a training goal.
  Journal with tags analysed against Recovery and Sleep. 24 widgets. Data export
  is Pro-only and recent. A "Preferred Sleep Tracker" setting is its answer to
  overlapping sources.
- **Training Today**: readiness 0–10 from the last 24 hours of HRV against a
  **60-day baseline**, drawn as a line over a **blue band marking the baseline's
  min/max**. Notifications fire against a 4-day average. **Status Tags** log
  factors and show them on the chart. Illness awareness prompts "how are you
  feeling" when resting HR, temperature or sleep deviate, and says outright it
  is not diagnostic. Full history exports as CSV.
- **AutoSleep**: the density benchmark. Four rings — Duration, Quality, Deep and
  **Heart (HR dip: sleeping HR against the previous 24 hours' non-active waking
  HR; 10%+ is "important", with Normal / Low Dip / Athlete modes)**. **Sleep
  Bank**: a running credit-or-debt balance over the last 7 nights against your
  goal, with a derived "latest bedtime". **Sleep consistency bars** for bedtime
  and wake against 7- and 28-day averages, **highlighting the same weekday in
  previous weeks**. Prior Day Stress compares yesterday's daytime HRV to a
  28-day trend. CSV export with **7-day averages already computed in the
  columns**. One-time £8.99, no subscription, no data collected — and reviewers
  say so approvingly.
- **HeartWatch**: segments one HR stream into Daytime / Sedentary / Sleeping /
  Waking / Workout contexts, with 7- and 21-day trend lines over 30+ metrics,
  notes attachable to individual readings, and reports formatted to show a
  doctor.
- **Correlate** ($4.99 one-time): Pearson *r* auto-discovery with a **lag scan of
  up to 7 days**, every finding labelled "patterns, not proof" and shown with
  *r*, *n* and significance; 14-day wait before the first report; "With vs
  Without" for logged habits; CSV per metric and "Export Everything".
- **Android, same class**: VitalPulse (recovery from overnight HRV and resting HR
  against a 60-day baseline, journal-tag correlations, ACWR training load, all
  on-device). Vora publishes actual weights — muscle recovery 35%, last night's
  sleep 20%, HRV vs two-week average 20%, training load 15%, resting HR 10% —
  and warns that Samsung Health sends no HRV, resting HR or respiratory rate
  through Health Connect.
- **Pricing signal**: $5–9 one-time for no-cloud tools, ~$30–40/yr for score apps.

### Oura and Garmin — method ideas

- **Oura Readiness** lists **nine contributors**, each rated Optimal / Good /
  Fair / Pay Attention beside the composite. The contributor breakdown is the
  transferable part; it is what makes the score explicable.
- **Body temperature deviation**: daily value plus a 3-day weighted trend, and it
  **can only lower** readiness, never raise it. A one-directional penalty is an
  honest way to use a noisy signal.
- **Tags**: 100+ presets plus custom, with start time, comment and repeat.
  **Smart Tags** propose a tag on mornings when the score is unusually high or
  low — the suggestion is the trigger that makes journaling stick.
- **Resilience** names the *weakest contributor* and keys its advice to it.
- **Garmin HRV Status**: 7-day average against a personal baseline corridor,
  **three weeks of warm-up before it shows anything at all**, and statuses that
  include "high can also be bad".
- **Garmin load ratio**: acute against chronic, banded Low <0.8, Optimal 0.8–1.4,
  High 1.5–1.9, Very High ≥2.0, appearing after two weeks.
- **Garmin Morning Report**: pure aggregation, no new computation, and one of the
  most-liked things in the product.
- **Intensity Minutes**: vigorous counts **double**, minimum 10 consecutive
  minutes, 150/week target.

### Google Health itself — the direct substitute

This is the comparison that matters most, and the headline is that **the official
app's derived figures are not written to Health Connect at all**.

Written to Health Connect, and therefore available to Air Bridge: steps, speed,
step cadence, **VO2 max**, floors, distance, elevation gained, exercise,
**exercise route**, total calories, body temperature, sleep session, sleep
stages, skin temperature, blood glucose, heart rate, HRV, respiratory rate,
resting heart rate, weight, **body fat %**, **hydration**, nutrition,
**periods / flow / intermenstrual bleeding**, medical records.

Read by Google Health but **never written** — so unobtainable: **oxygen
saturation**, active calories burned, mental wellbeing session duration, cervical
mucus, ovulation test, sexual activity.

Not in the table at all, so never available and only ever re-derivable: Daily
Readiness Score, Sleep Score and its six components, Resilience (which replaced
the stress score), Active Zone Minutes, cardio load, Coach output, band battery,
alarms.

What the official app shows that Air Bridge does not: Daily Readiness (HRV,
resting HR and a week of sleep against your baseline, 7 nights to first score),
its own Sleep Score (six components, bands at 90/80/60), Active Zone Minutes
(personalised zones, **double credit for vigorous**, 150/week), VO2 max, a
Vitals dashboard, Resilience, hydration and weight logging, auto-detected
activities across 100+ workout types, leaderboards, and Premium's Coach and
Health Guardian.

Worth noting what Google **removed** in the 2026 rework and did not replace:
badges, challenges, groups, the community feed, Sleep Profile, the web
dashboard, daily streaks, and minute-level skin temperature. Several of those are
now gaps in the *official* app — which is a reason to build them here, not a
reason not to.

**Air Bridge already gets auto-detected workouts for free**, because Google
Health writes the ExerciseSessions. Activity auto-detection is therefore not a
gap.

### The companion-app category — Gadgetbridge, Notify, Pebble, Bangle.js

Classified by what each feature needs from the band: **(a)** a screen, **(b)**
input back to the phone, **(c)** only the ability to vibrate, **(d)** only
phone-side work.

| Feature | Needs | Possible on the Air? |
| --- | --- | --- |
| Find-my-band buzz | (c) | **Yes — already built** (Device tab's Buzz) |
| Calendar event reminder buzz | (c)+(d) | **Yes** |
| Alarms / timers set from the app | (c)+(d) | **Yes** |
| Idle / inactivity alerts | (c)+(d) | **Yes — already built** (hourly nudge) |
| Step-goal celebration buzz | (c)+(d) | **Yes — already built** |
| Hydration reminder buzz | (c)+(d) | **Yes** |
| Do-not-disturb sync (follow phone DND) | (d) | **Yes** |
| Low band battery alert | (d) + a battery read | **Yes** — `Band.readBattery` already exists |
| Lost-connection alert, reconnect, missed-notification resend | (d) | **Yes** |
| Notification filtering, quiet hours, per-app rules | (d) | **Yes — already built** |
| Data export, backup, widgets, Tasker/Intents API | (d) | **Yes** |
| Sleep as Android alarm (band vibrates, stopped in the phone app) | (c)+(d) | **Yes** |
| Smart alarm / wake in light sleep | (c)+(d) + live sleep staging | **No** — see §5 |
| Per-app vibration *patterns* | (c) | **Partial** — counts and cadence only, as the README says |
| Turn-by-turn haptics | (c)+(d) | Partial — a buzz code, not directions |
| Find-my-phone from the band | **(b)** | **No** |
| Canned replies | **(a)+(b)** | **No** |
| Media controls | **(b)** | **No** |
| Call reject / answer from the band | **(b)** | **No** |
| Weather / calendar / text display, watchfaces, camera remote | **(a)** | **No** |
| Sleep as Android sensor handoff | live sensor stream | **No** |

Gadgetbridge's genuinely distinctive extras, all phone-side: an **Intents /
Tasker API** (set a setting, trigger a sync, export, set an alarm), **auto-export
of the whole database with restore**, home-screen **widgets**, **"cache while out
of range"** so notifications missed during a disconnect are delivered on
reconnect, and a **dashboard of gauges**. Notify adds CSV/PDF reports and Home
Assistant hooks.

---

## 3. Gap bucket 1 — cheap and well-matched

The data is already read. These are presentation and derivation.

| # | Gap | Who does it best | Why you'd want it | What it needs here | Size |
| --- | --- | --- | --- | --- | --- |
| 1.1 | **Journal / tags, and their impact on your own outcomes.** Daily yes/no or timed tags; a tag unlocks an impact readout after 5 yes and 5 no within 90 days; shows mean next-day outcome with against without, bucketed significant / ordinary / negligible, labelled correlation. | Whoop (Journal → Impacts); Bevel (Journal Insights); Oura (Tags + Smart Tags); Training Today (Status Tags) | The single most-praised feature in the entire competitive set, and the only one that turns readings into something you can act on. Air Bridge has no annotation layer at all. | A local tag store; a daily outcome to correlate against — sleep score and resting HR are both already computed; the min-n gate; a "Smart Tag" prompt on unusual mornings. `smallestWorthwhileChange` is already the right significance threshold. | **M** |
| 1.2 | **Export — CSV and/or JSON.** Dated rows, with the 7-day averages already in the columns. | AutoSleep (CSV with rolling averages precomputed); Training Today (full history CSV); Correlate; Heartwood; `kas-cor/healthconnect-export` (one JSON per day with a metadata block) | Every peer in the read-only class has it and Air Bridge has none. It is also the insurance policy for an app that "rides a reverse-engineered, version-sensitive protocol" — if a firmware update breaks the band, the readings should still come out. | A writer over `HealthSnapshot` plus the derived trends; a share sheet. No new permission. | **S** |
| 1.3 | **A weekly report.** What the week held, against the weeks before it — sleep mean and debt, resting HR and HRV direction, steps, sessions, energy balance, which vitals moved. | Garmin (Morning Report); Whoop (Weekly Performance Assessment, needs 5 days); Gentler (recaps); Heartwood (weekly summary vs goals) | Air Bridge has every number and no screen that summarises a stretch of time; the chart window answers "show me a fortnight", never "how was it". Garmin's Morning Report does no new computation and is among the best-liked things in the product. | Entirely assembled from existing trends. Needs a report screen and a rule for "notable". | **S–M** |
| 1.4 | **Sleep debt as a running balance**, and a target for tonight. | AutoSleep (Sleep Bank, 7 nights, plus a derived "latest bedtime"); Athlytic (rolling 14-day debt); Whoop (`baseline + debt + strain − naps`); Garmin (Sleep Coach) | The sleep goal already exists in `Goals.sleepMinutes` and nothing uses it as a balance. A debt figure is the one derived sleep number every competitor has and this app does not. | Arithmetic on `nights` against the stored goal. Honest version: show the balance and its coverage, not a prescription. | **S** |
| 1.5 | **Multi-metric outlier flag.** Count how many vitals sit outside their own band this morning; surface it as one calm notification only when two or more have moved. | Apple (Vitals: 2+ outliers → one notification with possible causes); Whoop (Health Monitor); Oura (Symptom Radar, needs 7+ of 14 nights) | Air Bridge is one step away — `recentHasMoved` already exists per vital and the Vitals screen already names which moved. What is missing is the count, the threshold, and telling you without being opened. | A count across the five vital trends; a notification path; copy that names possible causes and refuses to diagnose. Apple's single-outlier restraint is the part to copy. | **S** |
| 1.6 | **Training load: acute against chronic**, banded, with a manual effort override. | Garmin (0.8 / 1.4 / 1.5 / 2.0 bands); Apple (7d vs prior 28d, five labels); Bevel (7d vs 6wk ATL/CTL, "Calibrating" until 10 training days); Gentler (Activity Path band) | `activityTrend` is minutes a day and nothing more. The question "am I doing too much this week relative to my usual" has no answer anywhere in the app. Four independent vendors converge on the same method, which is unusual. | Per-session load from duration × average HR (already read via `SessionSummary.averageBpm`) or calories; 7-day against 28-day; a band, in keeping with the readiness decision. Add RPE (see 4.6) for strength work. | **M** |
| 1.7 | **Sleep consistency as its own view** — bedtime and wake bars over the window, against 7- and 28-day averages, highlighting the same weekday in previous weeks. | AutoSleep | Consistency is already one of the four sleep components, computed on the clock face, and the handover's own rule is that a score is never shown without its parts — yet the part itself has no chart. The same-weekday highlight is what makes a Friday-night pattern visible. | `Night.midpoint` and `start`/`end` are already there; `circularMean` already exists inside `SleepScore.kt` and would need promoting. | **S** |
| 1.8 | **Heart-rate dip** — sleeping HR against waking HR, as a percentage. | AutoSleep (its most-praised ring; 10%+ is "important", with Normal / Low Dip / Athlete modes); Bevel (an explicit Sleep Score input) | `sleepingRate` (10th percentile of the night) is already computed and `heartRecent` already holds the waking samples. The dip is one subtraction, and it is a genuinely different signal from resting HR. | Split `heartRecent` by the night's bounds, which the sleep screen already does. | **S** |
| 1.9 | **Correlations between two metrics the app already reads**, with a lag scan. | Correlate (Pearson *r*, lag up to 7 days, every finding shown with *r*, *n* and significance, labelled "patterns, not proof", 14-day wait) | Sleep against next-day resting HR, late eating against sleep, session load against next-day HRV — Air Bridge reads both halves of each and relates none of them. | The arithmetic belongs in `HealthTrend.kt` with the rest. The honesty bar is already set by `describeDrift`. | **M** |
| 1.10 | **A streak that does not punish rest**, with Sick / Injured / On a Break statuses that pause the nudges. | Gentler Streak | Google removed daily streaks from its own app in the 2026 rework, so this is now a gap in the substitute too. Air Bridge has goals and no notion of continuity. The status flags are the half that matters: they also make the hourly nudge stop pestering someone who is ill. | Pure local state and copy. It would also want to gate `StepGoalSettings`. | **S** |
| 1.11 | **A session against your typical session of that type.** | Athlytic ("comparisons to your typical session") | `SessionReader` reads a year of history and the session screen shows each workout alone. "Faster than your usual walk" needs no new data. | Group `sessions(days=365)` by exercise type; reuse `trendOf`. | **S–M** |
| 1.12 | **Zone-weighted active minutes** — vigorous counting double, a 10-minute minimum, against the weekly target already stored. | Garmin (Intensity Minutes); Google Health (Active Zone Minutes — not written to Health Connect, so it must be re-derived) | `Goals.activeMinutes` already holds the WHO 150, and `timeInZones` already excludes rest. Minutes are currently counted flat, so an hour's stroll and twenty minutes of intervals look alike. | Weight `timeInZones` output; a weekly ring. | **S** |
| 1.13 | **Annotate the outliers on a chart.** Mark the readings outside the usual band rather than leaving the band to be eyeballed. | Oura; Apple Vitals; Gentler (green within / orange outside) | The usual band is already drawn on every trend chart; the points that escape it are not distinguished. | Drawing only. | **S** |
| 1.14 | **Macro targets on the food log.** | Bevel; most nutrition apps | The handover says Log food has no macro targets "because nothing in the app stores one" — but `Goals.proteinGrams` and `Goals.proteinFor(weightKg)` now exist, and `weight` is read. The stated reason has expired for protein at least. | Draw the protein goal line that Goals already holds. Carbs and fat would still need a target nothing stores. | **S** |

---

## 4. Gap bucket 2 — possible, but needs new data or new plumbing

Everything here is obtainable; each costs a new permission, a new store, or a
new source.

| # | Gap | Who does it best | Why you'd want it | What it needs here | Size |
| --- | --- | --- | --- | --- | --- |
| 4.1 | **VO2 max.** Google Health **does** write it to Health Connect. | Garmin (Training Status); Whoop (Healthspan); Fitbit (Cardio Fitness) | The one fitness-capacity figure available here and unread. It is also the input that makes any "health age" style figure defensible rather than invented. | One new read permission and a tile; it fits the existing six-metric trend screen exactly. Note it only updates from a manually started phone-GPS run. | **S** |
| 4.2 | **Hydration.** Google Health writes it. | Fitbit/Google Health; Gadgetbridge (Lefun hydration reminder buzz) | Reading it costs one permission; the reminder buzz reuses the hourly-nudge machinery wholesale. | New read permission, a tile, and a third entry in `StepGoalSettings`-shaped settings. | **S–M** |
| 4.3 | **Body fat %.** Google Health writes it. | Whoop (lean mass in Healthspan); Athlytic | Weight alone moves for reasons that have nothing to do with composition; the weight screen currently says less than it could. | New read permission; extend the weight trend screen. | **S** |
| 4.4 | **Mood / state of mind.** | Apple (momentary vs daily, valence, labels, associations, overlaid against Life Factors) | The highest-value *manual* signal, and the natural companion to the journal in 1.1. Note: mental wellbeing session duration is read-but-not-written by Google Health, so the valence log has to be Air Bridge's own — exactly as saved foods are. | A local store; an entry control; an overlay against sleep and exercise. Apple does no statistics here, only overlay, which is the defensible version. | **M** |
| 4.5 | **Menstrual cycle, and a retrospective temperature shift.** Google Health writes periods, flow and intermenstrual bleeding. | Oura (Cycle Insights, ~2 months before temperature prediction starts); Apple (biphasic shift, labelled an estimate) | Skin temperature delta is already read and currently supports one sparse trend line. Paired with a period log it becomes the single richest thing on the tab. Prediction would be a much larger project than retrospective detection — start with the latter. | New read permissions; a calendar view; detection arithmetic. Apple's "estimates only" tone is the right one. | **L** |
| 4.6 | **Effort rating / RPE per session.** | Apple (effort 1–10, auto-estimated from HR and editable days later); Gentler (RPE override, added because HR under-reads strength work) | This is what makes 1.6 work for anything that is not cardio. A racquet session and a heavy lift look nothing alike to a heart rate monitor. | A local annotation keyed by session start — the same pattern `SessionReader.session` already uses to find a workout by its start time. | **S** |
| 4.7 | **Floors, elevation, speed, step cadence, exercise route.** All written by Google Health, none read. | Garmin; Apple | Route is the interesting one: the Air has no GPS, but Google Health writes the route of a manually started phone-GPS session. A map on the session screen is therefore possible, contrary to the obvious assumption. | New read permissions; a map needs a tile source, which means network — see 4.10. | **M** |
| 4.8 | **Blood glucose, body temperature, medical records.** All written by Google Health. | Apple (Health Records); Bevel | Niche unless relevant to this user, but they are free data sitting unread. | A read permission each. | **S** each |
| 4.9 | **Medication log and reminders.** | Apple (schedule, taken/skipped, follow-up after 30 min, critical alerts) | The relay already knows how to buzz a wrist on a schedule, which is the hard half. Interaction checking needs a licensed database and is out of scope. | A local store; reminders through the existing relay path. | **M** |
| 4.10 | **Calendar and weather context on charts.** | Garmin (Morning Report includes weather); Apple (State of Mind against daylight) | "Why was last Tuesday awful" is often answered by what was in the diary. Calendar is a phone permission. **Weather needs network, which this app currently does not use at all** — Gadgetbridge solves the same problem by delegating to a separate provider app rather than taking the permission. That architectural decision should be made deliberately. | Calendar read permission; weather is a bigger question than it looks. | **M** |
| 4.11 | **Workout planning, or manually started workouts.** | Garmin; Gentler (daily recommendations with a target zone and duration) | **Conflicts with a settled decision.** `HealthConnect.kt` says a workout already in Health Connect would be double-counted by a second copy, and that reasoning holds: Google Health auto-detects 100+ workout types onto the Air. A *plan* (`PlannedExerciseSessionRecord`) is a different record and does not double-count — but it is also the only Health Connect "goal" type, which the handover already investigated and set aside. | Worth reopening only for planning, never for logging. | **L** |

---

## 5. Gap bucket 3 — not possible on this hardware and stack

| Gap | Why not | Evidence |
| --- | --- | --- |
| **Blood oxygen / SpO2, and anything built on it** (apnea signals, overnight SpO2, Whoop's Health Monitor SpO2 row) | The Air measures it; Google Health **reads but never writes** oxygen saturation to Health Connect. | Google's own sync table lists oxygen saturation as read-only. Confirmed on the device in the handover: `WRITE_OXYGEN_SATURATION: granted=false` while skin temperature and respiratory rate are `granted=true`, matching `oxygen=0` in the reader's log. **Already decided against — see handover.** |
| **ECG, AFib / irregular rhythm history** | No ECG sensor on the Air, and rhythm alerts are not written to Health Connect. | Google sync table — no rhythm or ECG row. |
| **Active calories as distinct from total** | Google Health reads but does not write active calories. `TotalCaloriesBurnedRecord` is the only one available, which is what the app already uses. | Google sync table. |
| **Google Health's own derived figures** — Daily Readiness, its Sleep Score, Resilience, Active Zone Minutes, cardio load | None appear in the sync table at all. They can only ever be **re-derived**, which is exactly what `SleepScore.kt` does. | Google sync table. |
| **Body Battery, daytime stress, Whoop Stress Monitor, Oura's daytime Resilience half** | All need **daytime HRV**, and HRV arrives here as one figure a night — `HealthReader` folds the records to the last of the night, because that is all that is written. Dense all-day *heart rate* is available (hence the 18,000-readings-in-eight-hours problem the band charts solve), so an Athlytic-style exertion figure from time above baseline HR **is** possible; a true stress score is not. | `HealthReader.kt`, the HRV read; handover, "Dense charts". |
| **GPS pace and route from the band** | The Air has no GPS. Distance and pace come from the phone, and only for a manually started session. | Fitbit Air specifications. Note the partial exception in 4.7 — the *route* of a phone-GPS session is written. |
| **Smart alarm / waking in light sleep** | Needs live sleep staging. Health Connect delivers sleep after the fact, and the Air sends no realtime sensor stream. The band's own Smart Wake already does this, in firmware, through Google Health. | Gadgetbridge's Sleep-as-Android integration requires a live accelerometer and HR stream from the band. |
| **Sleep as Android sensor handoff** | Same reason: no realtime stream. (Its *alarm* is possible — see the Alerts table.) | As above. |
| **Tap to silence, tap to dismiss, any band-side input** | The Air handles taps in firmware and sends nothing. Measured as zero traffic upstream, including taps that dismissed a real Fitbit alarm. | **Already decided against — see handover.** |
| **Find-my-phone from the band, canned replies, media controls, call reject or answer from the band** | All need input events from the band. | Same evidence. |
| **Weather, calendar, notification text, icons, watchfaces on the band** | All need a screen. | The Air has none. |
| **Colour-coding alerts by LED** | No CoAP resource; light-shaped paths answer `4.04`, and the device exposes exactly two settings within a one-byte selector. | **Already decided against — see handover.** |
| **True vibration waveforms** | A pulse is a settings write. Patterns are counts and cadence, at one of two strengths. | README, "How a pulse actually works". |
| **Continuous live heart rate without cost** | Holding the subscription holds the band's one DTLS session, so alerts queue behind it. The screen already stops itself after two minutes. | Handover, "Measuring takes the tile over". |
| **Band battery or alarms from Health Connect** | Neither is in the sync table. Battery is readable over the transport directly, which is what `Band.readBattery` does. | Google sync table; `ui/DeviceScreen.kt`. |

---

## 6. The Alerts half

Measured against what a notification-relay app normally offers. Already built:
per-app and per-person rules, patterns and strength, calls that ring until
answered, quiet hours, pause, a Stop action, activity history, find-my-band
(Device → Buzz), step-goal celebration, and idle nudges.

### Possible and missing

| Gap | Who does it | What it needs | Size |
| --- | --- | --- | --- |
| **Calendar event reminder buzz** | Gadgetbridge (event reminder profile); Notify | Phone-side scheduling through the existing relay path. The service is already named `CalendarRelayService`. Calendar read permission. | **M** |
| **Low band battery alert** | Gadgetbridge ("Notify on low battery" + threshold); Notify | A poll of `Band.readBattery` and a phone notification. A flat band silently stops every other feature in this app, including the health buzzes. | **S** |
| **Lost-connection alert, and missed-notification resend on reconnect** | Gadgetbridge ("Cache while out of range") | Phone-side. The relay already knows when the link drops. Needs a rule for staleness — a buzz for a message from forty minutes ago is worse than no buzz. | **M** |
| **Do-not-disturb sync** (follow the phone's DND instead of only Air Bridge's own quiet hours) | Gadgetbridge ("Follow phone DND setting") | Phone-side, and it slots into `RelaySilence.reason()` beside the existing checks. | **S** |
| **Alarms and timers set from the app** | Gadgetbridge (alarm sync, widgets for 5/10/20 min); Notify | Phone-side scheduled buzzes. Note the Air stores 8 of its own alarms, settable only through Google Health — so this is a parallel facility, not a replacement, and should say so. | **M** |
| **Hydration reminder buzz** | Gadgetbridge (Lefun) | Reuses the hourly-nudge path exactly. Pairs with 4.2. | **S** |
| **Intents / Tasker API** | Gadgetbridge (set a setting, trigger a sync, export, set an alarm, device actions) | Phone-side. This is Gadgetbridge's most distinctive feature and nothing here exposes anything. | **M** |
| **Widgets** | Gadgetbridge (steps, sleep, battery, quick alarm) | **Designed, with a gallery screen, and no Glance implementation.** Already named as unbuilt in the handover. | **M** |
| **Backup and restore of the config** | Gadgetbridge (auto-export ZIP with full restore) | The rules config is a versioned file already. Pairs with 1.2. | **S** |
| **Sleep as Android alarm** (the band vibrates; stopping happens in the phone app, so no band input is needed) | Gadgetbridge | Its broadcast API, plus a held-session ring. Worth noting the ring holds the tracker channel, so a long alarm delays syncs. | **M** |
| **Per-day quiet windows** | — | The config holds one window for all days. The handover calls this a config change, not a screen. Not built, not rejected. | **S–M** |

### Not possible — and why

Find-my-phone from the band, canned replies, media controls, call reject or mute
from the band, answering calls, custom buttons, camera remote: **all need input
events from the band, which the Air never sends.** Weather push, calendar
display, notification text, icons and watchfaces: **all need a screen.**
Tap-to-silence: **already decided against — see handover**, established by
measurement rather than assumption.

Partial: per-app vibration *patterns* exist but are counts and cadence at two
strengths, not waveforms. Turn-by-turn haptics would be a buzz code, not
directions.

---

## 7. Top 10 I'd actually build, in order

Ranked by value × fit with this app's own philosophy, divided by cost. The
philosophy matters: this app measures against your own baseline, never invents a
composite, says what each figure stands on, and refuses clinical claims. Gaps
that fight that are cheap to build and wrong to ship.

**1. Journal and tag impacts (1.1, M).** The highest-value gap by a distance.
Whoop's Journal is the most-praised feature in the entire competitive set, Bevel
and Oura both copied it, and it needs no sensor Air Bridge lacks — only a tag
store and an outcome, both of which exist. It also suits this app better than it
suits its competitors, because Air Bridge already has the honest version of
significance: `smallestWorthwhileChange` is exactly the right gate, and
`describeDrift` already has the right voice. Build it with Whoop's 5-yes/5-no
rule, Bevel's "correlation, not cause" label, and Oura's Smart Tag prompt on
unusual mornings.

**2. Export (1.2, S).** Smallest effort to highest floor. Every peer in the
read-only class has it and this app has none. For an app the README calls
unofficial and version-sensitive, where a firmware update can break the whole
transport, the readings should be extractable independently of it. Follow
AutoSleep: dated rows, with the rolling averages already computed in the columns,
so the file is useful without a spreadsheet formula.

**3. Weekly report (1.3, S–M).** All the arithmetic exists; nothing assembles it.
Garmin's Morning Report does no new computation at all and is one of the most
liked things in Garmin Connect. This is the single best return on code already
written. It also gives 1.1 somewhere to surface its findings.

**4. Multi-metric outlier flag (1.5, S).** Air Bridge is one step from it —
`recentHasMoved` exists per vital, the Vitals screen already names which moved —
and the missing step is telling you without being opened. Copy Apple's restraint
precisely: a single outlier gets a marker, two or more get one calm morning
notification that names possible causes and refuses to diagnose. That restraint
is what keeps it from becoming the feature people switch off.

**5. Sleep debt as a balance (1.4, S).** `Goals.sleepMinutes` is stored and
nothing treats it as a running total. Every sleep app has this figure; it is
arithmetic on data already read. Show the balance and its coverage, and stop
short of prescribing a bedtime — that is where AutoSleep and Whoop make a claim
this app would not want to make.

**6. Training load, acute against chronic (1.6, M).** The biggest *conceptual*
hole: there is no answer anywhere in the app to "am I overdoing it". Garmin,
Apple, Bevel and Gentler independently converge on 7-day against ~28-day, which
is strong evidence the method is sound. Draw it as a band, exactly as readiness
is drawn, and the existing decision against composites survives intact. Pair with
RPE (4.6, S) or it will mis-read every strength session.

**7. Sleep consistency view (1.7, S).** The handover's own standard is that a
score is never shown without its parts — and consistency is the one part with no
chart behind it. The circular-mean arithmetic is already written and tested
inside `SleepScore.kt`. AutoSleep's same-weekday highlight is the detail that
makes it worth building rather than just plotting bedtimes.

**8. Heart-rate dip (1.8, S).** Two figures that already exist, one subtraction,
and a genuinely different signal from resting heart rate. AutoSleep's most-praised
ring, and Bevel treats it as a first-class sleep score input. Cheapest real
insight on this list.

**9. Calendar reminder buzz (Alerts, M).** The strongest Alerts-half gap that the
hardware actually permits, and the half of the band's job that Google Health
refuses to do at all — BGR confirms there is no hidden setting for notifications
on the Air. It is also the feature that makes the app's name honest: the service
is already `CalendarRelayService`.

**10. Band battery and disconnect alerts (Alerts, S each).** Unglamorous and
protective. Battery is already readable over the transport, the relay already
knows when the link drops, and a flat or disconnected band silently disables
every other feature in this app — including the two health buzzes, which the
Alerts screen already warns about for the relay-off case but not for this one.

**Just outside:** VO2 max (4.1, S) — free data, unread, and the only capacity
figure available here. Metric correlations with a lag scan (1.9, M) — higher
value than several of the above but better built on top of the journal than
before it. Zone-weighted active minutes (1.12, S). Mood logging (4.4, M) — the
natural second half of the journal, and worth doing together if 1.1 goes well.

**Deliberately not on the list:** a composite readiness number, anything
SpO2-derived, anything needing a tap or a screen, a second copy of a workout in
Health Connect, and a drag-reorder edit screen. Each is settled, and nothing in
this research changes the reasoning behind any of them.
