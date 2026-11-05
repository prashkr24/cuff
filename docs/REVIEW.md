# Code review — consistency and speed

Read on 2026-11-05 against `fd1a8b6`. 30,034 lines of main source, 2,952 of
tests. Every claim below has a file and a line behind it; where I could not
measure something I say so rather than guessing.

**What is already right**, so nobody spends a day fixing it: typography in
the app goes through `MaterialTheme.typography` almost everywhere — only 19
hardcoded `fontSize` values exist in `ui/`, and they are confined to
`ChartAxes.kt`, `theme/Type.kt` and `WidgetPreview.kt`, all three of which
have a reason. The Health read is cached, freshness-guarded and dispatched
to IO (`HealthState.refresh`). Release builds run R8 with resource
shrinking. Kotlin is 2.2.20, so Compose strong skipping is already on.

---

## Part 1 — Consistency

### 1.1 Four composables draw "a label and a figure under a chart" (high)

This is the thing you described. Four implementations, no two alike:

| | file | label style | label colour | upper? | value style |
| --- | --- | --- | --- | --- | --- |
| `Stat` | `HealthSleepScreen.kt:579` | `labelSmall` + **Bold** | `onSurfaceVariant` | yes | `titleMedium` SemiBold |
| `Metric` | `HealthActivityScreen.kt:345` | `labelMedium` | **`extras.disabled`** | yes | `bodyLarge` SemiBold |
| `Key` | `HealthBalanceDayScreen.kt:183` | `labelMedium` | `onSurfaceVariant` | **no** | `bodyLarge` SemiBold |
| `Metric` | `DeviceScreen.kt:328` | `bodyMedium` + icon | `onSurfaceVariant` | **no** | `headlineSmall` |

Three label sizes (`labelSmall` 11sp bold, `labelMedium` 11sp, `bodyMedium`
13sp), two label colours, two capitalisations and three value sizes
(`titleMedium` 17sp, `bodyLarge` 16sp, `headlineSmall` 26sp) — for the same
object in the same app.

`Stat` is already the most used (33 call sites against 6 and 2) and its own
comment argues for exactly this rule: *"One size for every stat, everywhere.
Sizing each by its own length made a single row show three figures at two
sizes, which reads as a mistake rather than as emphasis."* That reasoning is
correct and was then not applied to the other three.

**Fix.** Move `Stat` to `Components.kt`, give it optional `colour` and
`leading` (a dot or an icon) parameters, and delete the other three. One
call site changes meaningfully — `DeviceScreen`'s `headlineSmall` value is
deliberately large because it is the top-of-screen figure, so that one wants
a `prominent = true` variant rather than a silent shrink.

### 1.2 Two muted greys used interchangeably (high)

`colorScheme.onSurfaceVariant` appears 156 times and `extras.disabled` 77,
and the four composables above show them standing in for each other in the
same role. There is no written rule for which means what.

**Fix.** One sentence in `theme/`, then enforce it: `onSurfaceVariant` is
*secondary content you are meant to read*; `extras.disabled` is *furniture
and footnotes you are not*. Most of the 77 are correct under that rule; the
label halves of stats are not.

### 1.3 The widgets have a second, undeclared type scale (high)

The widgets do not use `MaterialTheme.typography` at all — they pass raw
`Int` sp values into `Kicker`, `Sub` and `Figure` (`WidgetParts.kt`). Across
`widget/`:

* `Kicker`: 9, 10, 11 (plus 3 defaulted)
* `Sub`: 9, 10, 11, 12, 13 (plus 10 defaulted)
* `Figure`: 14, 15, 19, 20, 22, 24, 26, 40, and several computed inline

Eight figure sizes and five sub sizes, none of which is derived from the
app's scale (26 / 20 / 17 / 16 / 13 / 11). A Sleep tile and a Sleep widget
are the same reading in two unrelated type systems — which is the thing you
spent today making the charts stop doing.

**Fix.** A `WidgetType` object beside `WidgetTheme` holding the app's own
numbers — `figure = 26`, `figureSmall = 20`, `title = 17`, `body = 13`,
`label = 11`, `caption = 9` — and every call site named rather than
numbered. This is mechanical and the tests will not notice, which is the
point: it is pure drift.

### 1.4 Card padding differs by family for no reason (medium)

`WidgetParts.WidgetCard` defaults to `11.dp`. Callers pass `11`, `12` and
`14` (`WidgetBand.kt:86` is 14, `WidgetDials.kt:287` is 12,
`WidgetRings.kt:362` is 11). The tab's tile uses 12 horizontal and 10
vertical, and `WidgetCard` already has separate `horizontal`/`vertical`
parameters for that reason. So the right value exists and three families
ignore it.

### 1.5 68 distinct spacing values in `ui/`, 27 more in `widget/` (medium)

`20.dp` (128 uses) is the screen gutter and `10/12/8/4/6/16/24` carry most
of the rest — but `2, 3, 5, 7, 9, 14, 18, 22, 26, 28` all appear too, and
`1.5.dp` eleven times. There is no scale, so every new screen invents one.

**Fix.** Not a sweeping refactor — name the four that already dominate
(`Gutter = 20`, `Gap = 12`, `Tight = 8`, `Hair = 4`) in `theme/`, use them
in new code, and convert opportunistically. The odd values in chart
internals are often load-bearing and should be left alone.

### 1.6 `Note` is defined three times (low)

`AboutScreen.kt:340`, `AppearanceScreen.kt:217`, `HealthEnergyScreen.kt:471`
— plus the one I deleted from `WidgetConfigActivity` today. Identical intent,
and they will drift. One in `Components.kt`.

---

## Part 2 — Performance

### 2.1 The Health tab composes and draws every tile, always (high)

`HealthScreen.kt:146` is `Column(Modifier.fillMaxSize().verticalScroll(...))`
with `packTiles(state.tiles).forEach` inside. Thirteen tiles, most of them
drawing a `Canvas`, all composed, measured, laid out and drawn whether or
not they are on screen. Only 2 of the 31 scrolling screens in `ui/` use a
lazy container at all (`AppsScreen`, `ContactsScreen`).

This is the single biggest win available. A `LazyColumn` over the packed
rows composes three or four rows instead of thirteen tiles, and the
difference shows up directly in first-frame time on the tab you open most.

**Caveat worth testing rather than assuming:** `verticalScroll` keeps a
scroll position across tab switches more simply than `LazyColumn` does, and
the tile grid is a fixed, short list. Measure the tab's first frame before
and after; if it is already under a frame budget, spend the effort on 2.2
instead.

### 2.2 Every tile recomputes its series on every recomposition (high)

None of the per-tile arithmetic is inside `remember`. From `HealthScreen.kt`
alone:

* `:423-429` — `DoubleArray(24)`, a filter over every food entry, two
  `runningSoFar` passes. Allocated and recomputed per recomposition.
* `:826-838` — `takeLast(7)`, two `map`s, `weekdayTicks`
* `:914-939` — `takeLast(7)`, `map`, `average()`, another `map`, ticks
* `:1122`, `:1173`, `:1205` — same shape

Strong skipping saves you when nothing changed, but anything that *does*
change — the ticking header, a theme change, a scroll that re-enters
composition — pays for all of it. Wrapping each in
`remember(data.snapshot) { ... }` is a one-line change per tile and removes
the work entirely from the common path.

### 2.3 Widget chart bitmaps are large and travel by binder (medium)

`ChartBitmap.MAX_WIDTH = 640`, `MAX_HEIGHT = 320`, `ARGB_8888` → **819 KB
per bitmap** at the cap, and the caps are reached: a 320dp card at 3× is
960px before clamping.

A stack with eight cards composes eight of these in one `onDataSetChanged`
and hands them to the launcher. That is ~6 MB of bitmap crossing a binder
for one widget update, against a host budget derived from screen size. I
have not seen it fail, and I have not measured it — but it is the most
likely cause of a stack that silently shows fewer cards than it holds, and
it is worth bounding before it bites.

**Fix.** `RGB_565` for the bar and stripe charts (no alpha needed, half the
bytes), and cap the long edge nearer 480. Keep `ARGB_8888` for the scatter
and ring bitmaps, which do use alpha.

### 2.4 No Baseline Profile (medium, cheap)

Nothing in `app/build.gradle.kts` generates or consumes one. For a Compose
app this is the largest single startup win available — typically 20–30% off
cold start and a visibly smoother first scroll — for about an hour of setup
with `androidx.baselineprofile`. Given "blazing fast" is the goal, this is
the best ratio of result to effort in this document.

### 2.5 `nowTicking()` is correctly scoped — no action (none)

Flagging it because it looks alarming and is not. It is minute-aligned, not
per-second (`Components.kt:60`), and each of its four callers reads it
inside the leaf that needs it — `HealthScreen.kt:265` is inside
`HealthHeader`, not at the top of the tab. Leave it.

### 2.6 `HealthData` holds `List` fields (low, informational)

`HealthState.kt:48` onward. `List` is unstable to the Compose compiler, so
these are compared by instance rather than skipped structurally. Under
strong skipping (on by default at Kotlin 2.2.20) this is largely harmless
because the instance is stable between reads. Worth knowing before anyone
"fixes" it with `@Immutable` — that annotation would be a lie unless the
lists are genuinely never mutated, and the cost today is near zero.

---

## What I would do, in order

1. **Baseline Profile** — biggest result, least risk, touches no app code.
2. **`remember` the tile series** (2.2) — one line per tile, pure win.
3. **One `Stat`** (1.1) and the grey rule (1.2) — this is what you actually
   asked for and it is visible on every detail screen.
4. **`WidgetType`** (1.3) — mechanical, and stops the widgets drifting from
   the app again.
5. **Narrower bitmaps** (2.3) — bounds a risk that is currently unbounded.
6. **Measure, then decide on `LazyColumn`** (2.1).

Items 1.4, 1.5 and 1.6 are tidying. Do them as you pass, not as a project.
