# Getting food into Air Bridge from somewhere other than the keyboard

Written 4 November 2026. A feasibility study, not a plan: nothing here has
been built, and two of its findings are corrections to things the handover
currently asserts.

---

## Verdict

**The plumbing is already done and nobody noticed.**

`HealthReader` does not filter nutrition by `DataOrigin`. There is no
`dataOriginFilter` anywhere in `app/src/main/kotlin` — not on the paged
`readRecords` call, not on any of the four nutrition aggregates. Health
Connect's default for an absent origin filter is *every* origin. So the moment
MyFitnessPal, Cronometer, Lose It!, Samsung Health or anything else writes a
`NutritionRecord` on this phone, it already appears in Air Bridge's food log,
already lands in the day totals, already moves the energy balance, and already
counts toward the "fewer entries than usual" heuristic on the Energy screen.

No new permission is needed either. `READ_NUTRITION` is already in the
manifest and already in `HealthConnect.readPermissions`; so are
`READ_HEALTH_DATA_HISTORY` and `READ_HEALTH_DATA_IN_BACKGROUND`. Reading
another app's nutrition is the same permission as reading your own — Health
Connect scopes permissions by *data type*, never by writer.

So the question is not "how do we import food from MyFitnessPal". It is
**"what does the food log need to say, now that we know the entries in it may
not be ours?"** That is a UI problem of roughly an evening's size, and it is
the single highest-value thing on this page.

Three findings fall out of that, in descending order of how much they matter:

1. **Foreign records are already visible.** Effort to "support" them: zero.
   Effort to *handle them honestly* (provenance, the delete problem below, the
   meal-type hole below): an evening.
2. **The food log probably cannot delete other apps' records, and the screen
   currently promises that it can.** Google's own documentation says an app may
   only delete what it wrote. The handover records the delete path as
   unwitnessed. The on-screen caption says "Deleting removes it there too" —
   that sentence is likely false for a foreign record, and the handover's
   corresponding claim in **The food log** is likely wrong. See A.5.
3. **Entries with `MEAL_TYPE_UNKNOWN` are invisible but counted.**
   `HealthFoodLogScreen` groups by `Meal.entries` — the four meals Air Bridge
   knows — so a record whose `mealType` is `0` is dropped from every group
   while `DayTotals` still sums it. A day whose rows do not add up to its own
   total. Air Bridge's own writes always set a real meal, so this has never
   fired; a foreign writer is exactly what fires it. See A.6.

Everything in sections B and C is a distant second to that. The one item in
them worth building is **barcode scanning against Open Food Facts** (free,
on-device ML Kit, no key, but thin on Indian food), and the one worth
*considering* is an **AI photo estimate that pre-fills the form** — which fits
the existing "start from → form" shape exactly, because an estimate is
precisely the kind of figure that must pass through a screen where it can be
corrected before it is written.

---

## A. Health Connect as the shared channel

### A.1 The code audit — what Air Bridge does today

This is the load-bearing finding, so here is the evidence rather than a summary.

`HealthReader.records()`, the only path that produces `FoodEntry` rows:

```kotlin
client.readRecords(
    ReadRecordsRequest(
        recordType = type,
        timeRangeFilter = TimeRangeFilter.between(...),
        pageToken = token,
    ),
)
```

`ReadRecordsRequest` has a `dataOriginFilter` parameter. It is not passed. Its
default is an empty set, which Health Connect reads as "no restriction".

The four aggregates behind the Nutrition and Energy screens —
`dailyTotals`, `total`, and their `AggregateRequest` /
`AggregateGroupByPeriodRequest` bodies — are the same story. `AggregateRequest`
also takes a `dataOriginFilter`; also not passed; also unrestricted.

A repository-wide grep for `dataOrigin` returns nothing in
`app/src/main/kotlin`. There is no allowlist, no package check, no "ours only"
flag stored anywhere.

| Surface | Reads foreign nutrition today? | Where |
|---|---|---|
| Food log rows | **Yes** | `records(client, NutritionRecord::class, …)` |
| Day totals on the log | **Yes** | `DayTotals` sums the same rows |
| Today's kcal / protein / carbs / fat | **Yes** | `total(client, NutritionRecord.*_TOTAL, today)` |
| Nutrition tile + 14-day macro split | **Yes** | `dailyTotals(…)` |
| Energy balance (eaten vs burned) | **Yes** | `eatenDaily` |
| Nutrition trend chip | **Yes** | derived from `eatenDaily` |
| "Fewer entries than usual" | **Yes** | `mealsDaily` from the same rows |

The only thing `FoodEntry` keeps from `Metadata` is `metadata.id`. It drops
`metadata.dataOrigin.packageName` and `metadata.recordingMethod` — which is why
the screens cannot currently say where a row came from even though the
information arrives with every record.

**This has very likely never been exercised**, because the phone has nothing
else writing nutrition. That is a reason to go and look, not a reason to
assume it works: the handover's own `READ_DISTANCE` story is exactly this
shape — a feature that looked broken and was only unconfigured.

### A.2 What `NutritionRecord` actually carries

Checked against the `connect-client` reference, which matches the 1.1.0 jar
the app builds against. Mandatory: `startTime`, `endTime`, `mealType`,
`metadata`. Optional: `name`, the zone offsets, and **forty-two nutrient
fields**, every one of them nullable:

- **Energy (2):** `energy`, `energyFromFat`
- **Macros (3):** `protein`, `totalCarbohydrate`, `totalFat`
- **Fat breakdown (5):** `saturatedFat`, `unsaturatedFat`,
  `monounsaturatedFat`, `polyunsaturatedFat`, `transFat`
- **Other gross (4):** `dietaryFiber`, `sugar`, `cholesterol`, `caffeine`
- **Minerals (16):** `sodium`, `potassium`, `calcium`, `iron`, `magnesium`,
  `phosphorus`, `zinc`, `copper`, `chloride`, `chromium`, `iodine`,
  `manganese`, `molybdenum`, `selenium`, plus the two above
- **Vitamins (12):** `vitaminA`, `vitaminB6`, `vitaminB12`, `vitaminC`,
  `vitaminD`, `vitaminE`, `vitaminK`, `folate`, `folicAcid`, `niacin`,
  `pantothenicAcid`, `riboflavin`, `thiamin`, `biotin`

Every one has a matching `*_TOTAL` aggregate metric, so a micronutrient screen
would be the same `dailyTotals` call with a different metric — cheap, if it is
ever wanted.

Air Bridge reads four of the forty-two. **Fibre, sugar and sodium are the three
most commonly written fields it currently throws away**, and MyFitnessPal in
particular is reported to write all three.

**How well it round-trips: badly, in one specific way.** A `NutritionRecord`
is *absolute totals plus a free-text name*. There is no quantity, no serving
size, no unit, no food id, no barcode, no recipe reference. "150 g basmati
rice" arrives as the string `"Basmati rice"` and 195 kcal. You cannot scale it,
cannot re-log "the same thing but double", cannot match it to a database entry.
This is the same gap that forces `SavedFoods` to exist, and it applies to
foreign records too: **Air Bridge can show and total another app's food, but it
can never reconstruct the other app's diary.** Anything wanting portions has to
go to that app's own export (section C.1).

**Edits.** Health Connect has `updateRecords`, and a well-behaved writer that
holds a `clientRecordId` will update in place, keeping the same record id.
Air Bridge, which re-reads the window on every `refresh`, sees the new figures
with no work. A writer that instead deletes and re-inserts produces a *new*
record id, which matters only if something cached the old one — nothing does.

**Deletes.** If the source app deletes its Health Connect record, the row
leaves Air Bridge on the next read. Whether source apps actually do this on an
in-app delete is **unverified for every app below**, and is the most common
real-world complaint about Health Connect nutrition: stale duplicates that the
source app no longer shows.

**Retroactive writes.** MyFitnessPal's own support material says only new data
syncs from the day you link, with no backfill. Expect the same elsewhere.
Nothing you ate before connecting is coming across.

### A.3 Who writes `NutritionRecord` on Android

Health Connect support claims on the open web are wrong constantly — two
competing vendor blogs in this research disagreed about Cronometer in opposite
directions, and both were wrong about something. Confidence is graded, and the
settling experiment is in A.7.

| App | Writes nutrition to HC? | Fields | Meal type | Confidence / evidence |
|---|---|---|---|---|
| **Cronometer** | **Yes** | "Nutrition" as a bundle; water split out to `HydrationRecord` | unstated | **High.** Cronometer's own blog, 7 Feb 2024, lists Nutrition in its *Export to Health Connect* set and omits it from the import set. Its help centre repeats this. One recent forum report of sync failing outright. |
| **MyFitnessPal** | **Yes** | kcal, protein, carbs, fat, fibre, sugar; sodium and cholesterol where logged | unstated | **Medium-high.** An MFP representative at Google's Health Connect beta launch (14 Nov 2022) said MFP engineers used it to "read and write nutrition, hydration, exercise and steps". MFP's own Health Connect FAQ documents linking and a per-type permission list but does not enumerate directions. A third-party integration dated 29 Sep 2026 pulls MFP calories and macros through Health Connect and calls it the default. No MFP page states the write explicitly — this is the weakest link in the chain and the one the adb check in A.7 settles. |
| **Lose It!** | **Yes** | calories, macronutrients **and micronutrients**, plus weight and manual workouts | unstated | **Medium-high.** Lose It!'s own help centre says so; one competitor blog disputes it. The vendor's own doc wins. |
| **Samsung Health** | **Yes** | its food log, mapped by Samsung to `NutritionRecord` | likely | **High.** Samsung's *developer* documentation maps Samsung Health nutrition onto `NutritionRecord` and describes both directions. Needs Samsung Health ≥ 6.22.5. Not relevant on a non-Samsung phone unless Samsung Health is installed. |
| **MacroFactor** | **Yes** | kcal, protein, carbs, fat, weight | unstated | **Medium.** MacroFactor's help centre names Health Connect as its Android integration and its troubleshooting tells users to check that MacroFactor has permission to *write* nutrition. Field list is from a third-party aggregator. |
| **Yazio** | Probably | calories and macros | unknown | **Low-medium.** Named alongside MFP by a Sep 2026 third-party integration; contradicted by a competitor's comparison table. Some reports of EU-only beta. Unresolved. |
| **Lifesum** | Probably | food, nutrition, water | unknown | **Low.** One source only; Lifesum was a named Health Connect launch partner in 2022, which is suggestive and not proof. |
| **FatSecret** | Probably **not** | — | — | **Low.** One audit calls its integration read-only; its own marketing names Google Fit / Apple Health / Samsung Health / Fitbit rather than Health Connect. Treat as "assume no". |
| **Carbon Diet Coach** | Unknown | — | — | **None.** No usable 2025–2026 evidence found either way. |
| **Google Fit** | Dead-ish | — | — | Google Fit's APIs run to the end of 2026; the Fit *app* is being replaced by Google Health (the renamed Fitbit app, 19 May 2026). The Fit app wrote through Health Connect latterly anyway. Not a route to build on. |
| **Nutritionix Track** | Unknown | — | — | **None.** No Health Connect evidence found. The Nutritionix *API* is a separate matter — see B.3. |
| **OpenNutriTracker** | **No** | — | — | **High.** Its own README: Health Connect access is **read-only, workouts only, off by default**, and "nothing is ever written back". |
| **Waistline** | **No** (local only) | — | — | **Medium-high.** Describes itself as entirely on-device with CSV/JSON export; no Health Connect integration documented. |
| **Open Food Facts** | n/a | — | — | Not a logger. It is a database — see B.4. |

The honest summary: **Cronometer and Lose It! are documented by their own
vendors. MyFitnessPal is near-certain but not vendor-documented. Everything
else is folklore until checked on the phone.**

Also worth knowing: **Cronometer and Samsung Health both enforce "one Android
health app at a time"** — connecting Health Connect means disconnecting
Samsung Health or Google Fit in those apps. That is a constraint on the
*other* app's setup, not on Air Bridge.

### A.4 Permissions: nothing new is needed

| Need | Already held? |
|---|---|
| Read any app's nutrition | **Yes** — `android.permission.health.READ_NUTRITION`, manifest line 28 |
| Read nutrition older than 30 days | **Yes** — `READ_HEALTH_DATA_HISTORY`, line 33 |
| Read nutrition while off screen | **Yes** — `READ_HEALTH_DATA_IN_BACKGROUND`, line 35 |
| Know which app wrote a record | **Yes** — arrives in `Metadata.dataOrigin`, no permission, currently discarded |
| Be told when nutrition changes | Not used. `getChangesToken` / `getChanges` exist and are unused; the app re-reads instead, which is fine for a tab you open. |

Health Connect scopes by data type only. There is no per-writer permission and
no "read other apps' data" permission. **Reading MyFitnessPal's food costs
exactly one more thing than reading our own: nothing.**

One caveat for a future Play release, not for a sideload: Google requires a
health apps declaration in the Play Console for the data types an app touches.
Irrelevant while the APK is sideloaded.

### A.5 The delete problem — a likely error in the current app

The handover, under **The food log**, says:

> Deleting goes through Health Connect and takes the record with it, whichever
> app wrote it.

and `HealthFoodLogScreen` prints, at the bottom of every day:

> Everything Health Connect holds for this day, whichever app wrote it.
> Deleting removes it there too.

**The evidence says an app can only delete what it wrote.** Google's Health
Connect documentation states the rule outright for medical records — "your app
can only delete records that it has written. An app cannot delete records
written by another app" — and the reference for the time-range `deleteRecords`
overload says the deletion is "automatically filtered to Record belonging to
the calling application". Third-party bindings report that an id-based delete
of a foreign record fails with an authorization error, and that because the
call is transactional, **one foreign id in a batch fails the whole batch**.

Caveats, stated plainly: the explicit sentence is on the *medical records*
page, and the per-id `deleteRecords` guide does not restate the rule. So this
is strong evidence, not a certainty, for `NutritionRecord` by id.

It matters for three reasons.

1. The handover already flags the delete path as **unwitnessed** — "no real
   `NutritionRecord` has been removed from the phone". The one claim about it
   that was never tested is also the one most likely to be wrong.
2. `HealthWriter.deleteFood` returns `LogResult.Refused` with Health Connect's
   own message on failure, and `HealthFoodLogScreen` prints that message at the
   bottom of the list. So the failure mode is **not** a crash or silent loss —
   it is a confusing sentence after a tap that promised to work. Tolerable, but
   the caption is a promise the app cannot keep.
3. **Even if it succeeded it would be the wrong thing.** Deleting MyFitnessPal's
   record from Health Connect does not delete it from MyFitnessPal's diary. The
   source app still holds it and may well write it again on the next sync. The
   user would tap Delete, watch the row vanish, and find it back tomorrow —
   worse than a refusal, because a refusal is honest.

**The recommendation is therefore not to make foreign deletes work. It is to
stop offering them.** Keep the row openable, keep "save to my foods", and
replace Delete on a foreign row with the name of the app that owns it:
"Logged in MyFitnessPal — delete it there." That is the same move the Activity
screen already makes for the distance permission: say exactly why, rather than
print a dash and leave someone guessing.

### A.6 The meal-type hole — a real bug waiting for its first foreign record

`HealthFoodLogScreen` draws the day like this:

```kotlin
Meal.entries.forEach { meal ->
    val forMeal = entries.filter { it.mealType == meal.type }
    …
}
```

`Meal` knows four values: breakfast, lunch, dinner, snack. Health Connect also
defines `MEAL_TYPE_UNKNOWN = 0`, which is a legal value for a mandatory field
and is what `FoodEntry` defaults to. Any record written with it is **filtered
out of every group and never drawn** — while `DayTotals`, which sums `entries`
directly, still counts it.

The visible symptom is a day whose meal rows do not add up to the figure above
them, with no row to blame. Air Bridge's own writer always picks a real meal
(`Meal.at(hour)`), which is why this has never appeared. A foreign writer that
does not bother with meal type is exactly what produces it.

Fix: a fifth group, drawn last, for anything that matched no meal — "Other",
or the honest "Not filed under a meal". Perhaps twenty lines.

### A.7 The settling experiment (do not run from here — run it on the phone)

One command answers the whole of A.3 for this specific phone, with no web
source involved:

```bash
adb shell "dumpsys package com.myfitnesspal.android | grep -i nutrition"
```

and, more usefully, the list of everything holding the permission at all:

```bash
adb shell "dumpsys package --all | grep -B40 'health.WRITE_NUTRITION: granted=true'"
```

This is the same method that settled the blood-oxygen question — Fitbit's
`WRITE_OXYGEN_SATURATION: granted=false` — and it is the only method in this
document that cannot be wrong. The second, cheaper check: install one
nutrition app, log one meal, open Air Bridge's food log, and see whether the
row is there. If it is, section A is finished and sections B and C are
optional.

Note ColorOS blocks `adb shell pm grant` on this phone, so the permission must
be granted by hand in the other app; `dumpsys` reading still works.

---

## B. Direct APIs, if Health Connect is not enough

### B.1 Diary APIs — all closed

| Source | Public API? | Auth | Friction | Data | Cost | Status |
|---|---|---|---|---|---|---|
| **MyFitnessPal** | Private, approval-only | OAuth, after a business review | Email `API@myfitnesspal.com` with "information about your company"; a hobby app is not a company | Full diary | n/a | **Dead end.** The developer portal still describes it as "a private API available to approved developers only". Nothing suggests individual access. |
| **MyFitnessPal premium CSV export** | n/a | Premium subscription | Paid tier, manual download | Full diary with portions | Subscription | **Workable but manual** — see C.1 |
| **`python-myfitnesspal`** | Unofficial scraper | Account cookies | Breaks on every login change; PyPI package shows no release in over a year | Full diary | free | **Do not build on it.** It is Python, this is an Android app, so it would need a server; it is explicitly unsanctioned; and MFP's login is behind bot protection. A desktop-side one-off to produce a CSV is the only defensible use. |
| **Cronometer** | No public API | — | — | — | — | Export-only; see C.1 |

Air Bridge is a sideloaded Kotlin app with one user. Every one of these wants a
server, a secret, and a relationship with a company. **None of them is worth a
line of code while Health Connect is sitting there doing it for free.**

### B.2 The point of the database APIs is different

These do not solve "get my MyFitnessPal diary". They solve a *different and
arguably better* problem: letting the Log food screen **search or scan** rather
than demanding five typed figures. That is the real daily friction in the
current app, and it is the thing a food library would fix — the thing the
handover notes Health Connect refuses to provide.

### B.3 The food databases, judged as a search/barcode backend

| Database | Free tier (late 2026) | Licence | Barcode? | India coverage | Offline? | Verdict for this app |
|---|---|---|---|---|---|---|
| **Open Food Facts** | Free, no key. "One API call per real user scan" is the stated rule; bulk scraping is blocked | **ODbL** + DbCL; images CC-BY-SA | **Yes, the point of it** | ~10,000 Indian products as of Sep 2024 (official milestone); thin but real, and packaged goods are its strength | **Yes** — full CSV ~0.9 GB compressed / ~9 GB raw, plus JSONL, Parquet and 14-day deltas | **Best fit.** No key, no quota, no account, packaged-food barcodes, and an offline path if wanted. See the licence note below. |
| **USDA FoodData Central** | Free, keyed, generous | US Government, public domain | No | Poor — generic US foods | Downloadable | Good free fallback for generic foods ("1 egg", "100 g chicken"). Useless for an Indian packet. |
| **Nutritionix** | **Free developer tier appears to be gone**; reports of a 2-user trial and paid plans from ~$499/month | Proprietary | Yes | US-centric | No | **Out.** Price alone ends it. |
| **FatSecret Platform API** | Free basic tier historically, now key-gated with regional premium tiers | Proprietary, attribution required | Yes | Has an India region in premium tiers | No | Possible, but an account, a key and uncertain terms for a one-user app. |
| **Edamam** | Free tier, then ~$49/month upward | Proprietary | Partial | Poor | No | Not worth the account. |
| **Spoonacular** | Small free quota (~500/day, unconfirmed) | Proprietary | Partial | Poor | No | Recipe-oriented; wrong shape. |
| **IFCT 2017 (NIN/ICMR)** | **Free, and it is the right data** | Zenodo "Other (Open)" — check before shipping | No | **528–542 Indian foods, measured across six regions** | **Yes — it is a table, not a service** | **The India answer.** No official API; community packages (`ifct2017` on npm/Zenodo/Kaggle) and a derived open Indian Nutrient Databank with cooked recipes. Ship it as an asset. |

**The India point matters and cuts against the obvious choice.** Every
US-centric database is close to useless for home-cooked Indian food, which is
most of what gets logged. Open Food Facts is good at *packets* — a barcode on a
biscuit packet — and weak on *dishes*. IFCT 2017 is the opposite: no barcodes
at all, but real measured values for Indian foods, as a bundled table with no
network call. **The sensible combination is IFCT as the bundled search index
and Open Food Facts for barcodes**, and the two answer different questions well
enough that neither replaces the other.

**ODbL, stated plainly, because it has teeth.** Attribution is required and
Open Food Facts asks apps for prominent in-UI attribution with clickable links
to the project and the licence. Share-alike bites only if you publicly
distribute an *adapted database*: using the API per scan, or shipping a cached
subset for your own use, does not trigger it; publishing a corrected extract
does. For a sideloaded one-user app this is a line in the About screen and a
line in `THIRD_PARTY_NOTICES.md` — the file already exists.

### B.4 Barcode scanning on Android

| Option | Dependency | Size | Cost | Fit |
|---|---|---|---|---|
| **ML Kit, Play Services (unbundled)** | `com.google.android.gms:play-services-mlkit-barcode-scanning` | **~200 KB**, model downloaded by Play Services | Free, on-device | **Pick this.** |
| **ML Kit, bundled** | `com.google.mlkit:barcode-scanning` | ~2.4 MB | Free, on-device | Only if Play Services cannot be relied on |
| **Google code scanner** | part of ML Kit | smallest | Free | **Needs no camera permission** and brings its own UI. The cheapest possible integration; you give up a custom viewfinder. |
| **ZXing** | third-party, long in maintenance mode | moderate | Free | No reason to prefer it now |

EAN-13 and UPC-A/E — the formats on food packets — are supported, and the app
already targets a modern SDK well past ML Kit's API 23 floor. **The Google code
scanner is the right first move**: it needs no camera permission at all, which
on a sideloaded app with an already-long permission story is worth more than a
custom viewfinder. One new dependency, one launcher call, a barcode string out,
one Open Food Facts fetch, and you land on the existing form with the figures
filled in. That is the same shape as "start from a saved food".

---

## C. Other import routes

### C.1 File import

| Route | Produces | Worth it? |
|---|---|---|
| **MFP premium CSV export** | Full diary with portions and meals — more than Health Connect can carry | **Only as a one-off backfill.** Health Connect does not backfill, so if there is a year of MFP history worth having, this is the only way to get it. Writing it in would mean Air Bridge inserting hundreds of `NutritionRecord`s attributed to itself, which is a provenance lie and, per A.5, the only records it could later delete. |
| **Cronometer CSV export** | Same shape | Same answer |
| **Generic CSV mapping UI** | Anything | **No.** A column-mapping screen is a week of work and a maintenance burden for a one-user app, to replace a `NutritionRecord` stream that already arrives automatically. |

Verdict: **skip, unless a specific pile of history is actually wanted.** If it
is, do it as a throwaway desktop script that writes via adb or a debug-only
import screen — not as a product feature.

### C.2 Share target / intent interop

Android's `ACTION_SEND` would let Air Bridge appear in another app's share
sheet. The problem is that **there is no standard for a shared food entry.**
No MIME type, no schema, no app that emits one. You would be able to receive
`text/plain` — a line someone copied — and parse it with a regex. That is a toy.

The one legitimate variant: register for `text/plain` so a *name* can be shared
in and land on the Log food form with the What field pre-filled, saving some
typing. Half a day, marginal value, no interop standard behind it.

**Verdict: no.** Health Connect *is* the Android interop standard for this, and
it already works.

### C.3 Photo / AI food logging

What the current generation does: Cal AI, SnapCalorie, MyFitnessPal's Meal
Scan, Lose It!'s Snap It and Samsung Food all take a photo, identify the dish,
estimate portion, and return calories and macros.

**Accuracy, honestly.** A 2026 NIH study tested the photo features of
MyFitnessPal, Lose It!, Cal AI and Appediet against 102 meals prepared in a
controlled metabolic kitchen. **All four underestimated** — by roughly
250–345 kcal per meal and about 30 g of fat per meal, with Cal AI worst at
345 kcal and MFP at 327. It is a conference abstract rather than a peer-reviewed
paper. Vendor claims range from "about 80% accurate" to "±1.1%" and are
unverifiable; a 2023 systematic review put image-based calorie error anywhere
from under 1% to over 38%. **Assume a photo estimate is wrong by a few hundred
calories on a mixed cooked meal, and systematically low.**

**Does it fit this app? Yes, unusually well — and the handover is the reason.**

The Log food flow is deliberately two steps: "start from" offers something
recent, a saved food, or typing it, and *whichever you pick lands on the same
form*, because — in the handover's words — "nothing is written from a list: the
figures pass through the form, where they can still be corrected." An AI
estimate is the single strongest argument that shape was right. A photo is just
a fourth thing to start from. It produces exactly what the other three produce
— a name and four numbers — and it lands in the same form, where a figure that
is systematically 300 kcal low can be dragged up before anything is written.
**No new screen, no new write path, no new invariant.** One row on "Start from",
one camera intent, one API call, one mapping into the existing form state.

Cost per call, at current vision pricing for a single user: a photo plus a
short prompt is on the order of a fraction of a US cent per log. Somebody
logging four meals a day lands in the region of a few tens of cents a month.
The real cost is not money — it is an API key living in a sideloaded APK, which
means either accepting that (single user, own phone, own key, revocable) or
putting a one-endpoint proxy on the `neer` home server, which already runs
things like this.

Two cautions. The estimate must be **labelled as an estimate on the form**, in
the same spirit as the existing macros-disagree-with-calories message — the app
already refuses to decide which of the user's numbers is the lie, and it should
not start presenting its own guess as a reading. And Indian food is the hard
case for every one of these models: mixed dishes, gravies, unknown oil content.
Expect it to be worse than the NIH figures, not better.

**Verdict: the best of the optional work, and only after section A is done.**

---

## D. The reverse direction

**It is already free, and it already happens.** `HealthWriter.logFood` inserts
a real `NutritionRecord` with `energy`, `protein`, `totalCarbohydrate`,
`totalFat`, `mealType` and `name`, marked `Metadata.manualEntry()`. Any app the
user grants `READ_NUTRITION` sees it immediately. Nothing needs building.

Who reads nutrition from Health Connect:

| App | Reads nutrition? | Note |
|---|---|---|
| **Cronometer** | Only water | Its official import list is vitals/body/exercise; of the nutrition category it takes hydration alone. So Air Bridge's food will **not** appear in Cronometer. |
| **Samsung Health** | **Yes** | Samsung documents two-way `NutritionRecord` sync. The clearest consumer, on a Samsung phone. |
| **MyFitnessPal** | Claimed yes | The 2022 launch quote says "read and write nutrition". In practice MFP wants to be the source of truth, and double-counting is the obvious risk. |
| **Google Health (Fitbit)** | Unconfirmed | Could not establish that the renamed Fitbit app reads `NutritionRecord`. Given the blood-oxygen finding — Fitbit withholding a type it owns — do not assume generosity here. |
| **OpenNutriTracker** | **No** | Read-only for workouts, by design |

The one thing worth saying out loud: **two apps both writing food to Health
Connect double-counts.** The moment MyFitnessPal is writing, Air Bridge's own
Log food screen becomes a second writer into the same pool it reads back, and
the energy balance counts both. The handover's own framing in
`HealthConnect.kt` anticipated this exactly — "a workout already in Health
Connect would be double-counted by a second copy, while an unlogged meal simply
leaves the energy balance guesswork" — and the arrival of a real nutrition
writer is the event that turns food into the same case as workouts.

So if a serious logger is adopted, Air Bridge's Log food screen should
**become the fallback, not the habit**: still there for the meal you cannot be
bothered to open MyFitnessPal for, but no longer the thing the Nutrition screen
pushes you toward. That is a copy and emphasis decision, not a code one.

---

## Ranked, by value ÷ effort

Effort scale: **an hour**, **an evening**, **a weekend**, **a week**.

| # | Do this | Effort | Value | Why it ranks here |
|---|---|---|---|---|
| **1** | **Run the adb check** (A.7) and install one nutrition app, log one meal, look at the food log | **an hour** | Decides everything below it | The only step in this document that cannot be wrong. Everything in section A is predicted behaviour until this is done. |
| **2** | **Carry `dataOrigin.packageName` into `FoodEntry` and show it** — a "logged in MyFitnessPal" line on an expanded foreign row | **an evening** | High | One field added to a data class, one label resolved through `InstalledApps`, one line in `EntryRow`. Turns an already-working read into an honest one. |
| **3** | **Stop offering Delete on rows Air Bridge did not write**, and fix the caption that promises it works | **an evening** | High | Section A.5. The app currently makes a promise the platform probably refuses, about a path that is unwitnessed. Replacing it with "delete it in MyFitnessPal" is more correct *and* less code. |
| **4** | **Add the fifth meal group for `MEAL_TYPE_UNKNOWN`** | **an hour** | Medium-high | Section A.6. A genuine arithmetic bug, armed the moment a foreign writer appears. |
| **5** | **Read fibre, sugar and sodium** and show them on the Nutrition screen | **an evening** | Medium | Three more `dailyTotals` calls against existing aggregate metrics. Only worth it once something is actually writing them — MFP and Lose It! both do. |
| **6** | **Barcode scan → Open Food Facts → the existing form** | **a weekend** | Medium-high | The Google code scanner needs no camera permission; Open Food Facts needs no key. Fits "start from" exactly. Caveat: packaged goods only, and thin on Indian products. |
| **7** | **Bundle IFCT 2017 as a searchable saved-foods seed** | **a weekend** | Medium | 528 measured Indian foods as an asset, searchable from "start from", landing on the form. No network, no key, no licence anxiety beyond checking the Zenodo terms. Arguably more useful than the barcode scanner for this user, and they compose. |
| **8** | **AI photo estimate pre-filling the form** | **a weekend** | Medium | Section C.3. Fits the existing two-step shape perfectly and is the most *fun* item here, but it needs a key or a proxy on `neer`, and it is wrong by a few hundred kcal on exactly the food this user eats. Label it an estimate. |
| **9** | **Reconsider whether Air Bridge should keep writing food at all** | **an evening of thinking** | Medium | Section D. Only becomes a live question once another writer exists. Copy and emphasis, not code. |
| **10** | MFP / Cronometer CSV one-off backfill | **a weekend** | Low | Only if a specific pile of history is wanted. Debug-only, not a feature. |
| **11** | Generic CSV mapping screen | a week | Very low | No. |
| **12** | Share-target intent interop | half a day | Very low | No standard exists. Health Connect already is the standard. |
| **13** | MyFitnessPal API / scraper | weeks + a server | Negative | Closed to individuals, unsanctioned, needs a backend this app does not have. |

**The first four lines total about a day and a half and are the whole of the
real answer.** Items 6–8 are a different project — making *Air Bridge's own*
logging less tedious — and should be judged on their own merits, after the
phone has told you whether anything else is writing nutrition at all.

---

## What could not be confirmed

Stated plainly, because the web is unreliable on exactly these points:

- **MyFitnessPal writing nutrition to Health Connect** is not confirmed by any
  MyFitnessPal-owned page. The evidence is a 2022 launch quote from an MFP
  representative, MFP's own Health Connect permission-management instructions,
  and a Sep 2026 third-party integration that depends on it. Strong, not
  certain. A.7 settles it.
- **Yazio, Lifesum, FatSecret, Carbon Diet Coach, Nutritionix Track** — no
  reliable evidence in either direction. Competing vendor blogs contradict each
  other and none is authoritative.
- **Whether any of these apps propagate an in-app edit or delete** to their
  Health Connect record. No vendor documents it. Expect stale rows.
- **Whether `deleteRecords` by id rejects a foreign `NutritionRecord`.** Google
  states the rule explicitly only on the medical-records page; the per-id guide
  does not restate it. The time-range overload's documented auto-filtering and
  third-party authorization-error reports point the same way.
- **Open Food Facts' current India product count.** The last official figure is
  10,000, from Sep 2024.
- **Whether Google Health (the renamed Fitbit app) reads `NutritionRecord`.**

## Sources

Health Connect and Android:
[NutritionRecord reference](https://developer.android.com/reference/androidx/health/connect/client/records/NutritionRecord) ·
[Health Connect data types](https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types) ·
[Delete data guide](https://developer.android.com/guide/health-and-fitness/health-connect/develop/delete-data) ·
[Write medical data (ownership rule)](https://developer.android.com/health-and-fitness/health-connect/medical-records/write-data) ·
[Synchronize data](https://developer.android.com/health-and-fitness/health-connect/sync-data) ·
[ML Kit barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android) ·
[Manage connected apps in Health Connect](https://support.google.com/android/answer/12201230) ·
[Health Connect beta launch partners, 14 Nov 2022](https://9to5google.com/2022/11/14/google-health-connect-beta/)

Apps:
[Cronometer — Health Connect, 7 Feb 2024](https://cronometer.com/blog/health-connect/) ·
[Cronometer help — Health Connect](https://support.cronometer.com/hc/en-us/articles/22731903751316-Health-Connect) ·
[MyFitnessPal — Health Connect FAQ](https://support.myfitnesspal.com/hc/en-us/articles/10553948248973-Health-Connect-FAQ-and-Troubleshooting) ·
[Lose It! — Using Health Connect](https://loseit.zendesk.com/hc/en-us/articles/47650100219028-Using-Health-Connect-With-Lose-It) ·
[Samsung — Accessing Samsung Health data through Health Connect](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect) ·
[MacroFactor — Integrations](https://help.macrofactorapp.com/en/articles/102-integrations) ·
[MyFitCoach — Health Connect sync, 29 Sep 2026](https://intercom.help/myfitcoach/en/articles/11631892-activating-health-connect-synchronization-android) ·
[OpenNutriTracker](https://github.com/simonoppowa/OpenNutriTracker) ·
[Waistline](https://f-droid.org/packages/com.waist.line/) ·
[Google Fit sunset](https://sahha.ai/blog/google-fit-api-sunset-migration/)

APIs and databases:
[MyFitnessPal developer portal](https://www.myfitnesspal.com/apps/api/version) ·
[python-myfitnesspal](https://github.com/coddingtonbear/python-myfitnesspal) ·
[Open Food Facts — data, API and licence](https://world.openfoodfacts.org/data) ·
[Open Food Facts India 10k milestone, Sep 2024](https://blog.openfoodfacts.org/en/news/open-food-facts-india-database-reaches-10k-product-milestone) ·
[IFCT 2017 dataset](https://zenodo.org/records/7088653) ·
[IFCT 2017 tables](https://ifct2017.github.io/) ·
[Development of an Indian Food Composition Database](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11277795/) ·
[Nutritionix pricing reporting](https://calorieapi.com/blog/nutritionix-api-pricing)

Photo logging accuracy:
[Photo-based meal apps underestimate calories and fat (NIH study, 2026)](https://www.medscape.com/viewarticle/photo-based-meal-apps-underestimate-calories-and-fat-2026a1000qo9) ·
[Four photo calorie apps, 102 meals](https://dev.to/jukkablomberg/cal-ai-myfitnesspal-lose-it-four-photo-calorie-apps-looked-at-102-meals-all-four-came-in-low-3o4d)
