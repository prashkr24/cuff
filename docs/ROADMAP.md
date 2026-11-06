# What to build next

Chosen 5 November 2026, from the four explorations in `explorations/`. This is
the list, in the owner's order, with what each one costs and what it has to
avoid.

> **All seven were built on 5 and 6 November 2026.** This document is kept as
> the record of what was chosen and why — in particular the reasoning in
> **Readiness, and the composite rule** below, which is the reversal of a rule
> `HANDOVER.md` had recorded as settled. Read it as a brief, not as a list of
> outstanding work. Where a constraint here was honoured, the code says so:
> readiness is withheld below sixty readings, the composite is never shown
> without its parts, and "biological age" is called **fitness age** throughout
> for the reason item 2 gives.

Two of these overturn a decision the handover records as settled. That is the
owner's call and it is made — see **Readiness, and the composite rule** below,
which is the first thing to read if you are about to build either.

---

## 1. Readiness

A single readiness figure, of the kind Whoop, Oura, Garmin and Athlytic all
show.

**Method.** The read-only class converges hard here, which is the best evidence
available that the shape is right: overnight **HRV against a 60-day rolling
baseline**, plus **resting heart rate** against the same, HRV weighted slightly
higher. Athlytic's framing is the one to copy — **50% means "near your own
baseline"**, not "average for a person your age". Oura lists **nine
contributors** each rated Optimal / Good / Fair / Pay attention beside the
composite, and that breakdown is the transferable part: it is what makes a
score explicable rather than oracular.

**What this app already has.** `HealthTrend` computes the baseline and the
smallest worthwhile change; `Vitals` already draws readiness as a band and
names which metrics moved. The figure is the new part, not the arithmetic.

**Constraints that are not negotiable.** HRV arrives as **one figure a night**,
not continuously, so there is no daytime readiness and no Body Battery here —
see `explorations/feature-gaps.md`. A 60-day baseline needs 60 days: show a
**Calibrating** state until it has them, the way Bevel does, rather than a
confident number built on nine nights.

**The rule it must still keep:** never show the total without its parts. That
is what makes the sleep score defensible and it is what will make this
defensible.

---

## 2. Biological age

A "fitness age" / Whoop Age figure.

**Method.** Published fitness-age models are built on VO2 max first, then
resting heart rate, activity volume and body composition. **VO2 max is not
currently read** — it is one of the four metrics written up under **What is not
built** in the handover, with its record type and field name. So this feature
has a hard prerequisite: build that read first, and expect a sparse series,
because VO2 max is re-estimated every week or two rather than daily.

**The honest version.** Say what it is made of and over what period, give it
the same Calibrating state, and resist a number that moves day to day — a
biological age that drops two years because you slept well is not measuring
anything. Whoop publishes Whoop Age as a slow figure for exactly this reason.

**This is the weakest item on the list** and it is here because it was asked
for, not because the evidence supports it. Of everything here it is the one
most likely to read as a claim about health rather than a summary of readings.
Build it last, and if it will not come out honest, say so rather than shipping
a number.

---

## 3. The morning moment

**The biggest structural gap, and it is a flow rather than a feature.**
Everything on the Health tab is pull: you open it and look. Every competitor
has one pushed moment a day that assembles what already exists. Garmin's
Morning Report **does no new computation at all** and is among the most-liked
things in Garmin Connect.

Last night's sleep and its score, resting heart rate and HRV against their
bands, yesterday's steps against the goal, and what today's readiness says.
Every one of those figures is already computed; nothing assembles them.

**Best return on code already written**, which is why it is third despite being
cheaper than the two above.

Use the existing relay notification path, not a new one. It must respect quiet
hours — a morning report at 5am is the fastest way to have it turned off.

---

## 4. Journal

Tags logged at a fixed moment, analysed later against sleep and readiness.

**The most-praised feature in the entire competitive set.** Whoop's Journal;
Bevel and Oura both copied it. It needs no sensor this band lacks.

**It suits this app better than it suits them**, because the honest version of
significance already exists here: `smallestWorthwhileChange` is exactly the
right gate and `describeDrift` already has the voice.

Discipline to copy, all three of them:

- **Bevel gates an insight on 5 yes and 5 no** before it says anything.
- Everything is labelled **"correlations, not cause"**. Correlate shows *r* and
  *n* beside every finding and waits 14 days before its first report.
- Correlate also **scans up to 7 days of lag**, which is what catches "a late
  dinner costs you two nights later" rather than only tonight.

Tags are this app's own, like saved foods: Health Connect has no record for
them, and Google Health reads mental-wellbeing records without ever writing
one.

---

## 5. Sleep bank

A running credit-and-debit balance over the last seven nights against
`Goals.sleepMinutes`, which is stored and which nothing currently treats as a
running total. AutoSleep's version is the reference.

Arithmetic on data already read; one of the cheapest items here.

**Stop short of prescribing a bedtime.** AutoSleep derives a "latest bedtime"
from the balance and that is a step past what this app says elsewhere: show the
balance and its coverage, and let the person draw the conclusion.

---

## 6. Export

Dated rows, with the rolling averages already computed in the columns, so the
file is useful without writing a spreadsheet formula. That detail is
AutoSleep's and it is the difference between an export people use and one they
open once.

**Every peer in this app's architectural class has this and this app has
none.** It matters more here than for any of them: the README calls this app
unofficial and version-sensitive, and a firmware or Google Health update can
break the transport without warning. The readings should outlive the app that
drew them.

Pair it with a backup of the rules config, which is already a versioned file.

---

## 7. Day-wise exploration in every graph

**Read this before building anything: it is mostly already done.** Audited on
5 November, every detail-screen chart already scrubs —
`Modifier.scrubbable`/`scrubbableAcross` with a marker and a value, on Heart,
Sleep, Steps, Energy, Nutrition, HRV, Activity, Trend, Session and Balance,
**including the secondary charts inside those screens** (`ByWeek`,
`WhenYouWalk`). Do not rebuild it.

What is actually missing is narrower:

- **The readout is drawn three different ways.** Steps, Sleep, Energy,
  Nutrition, HRV, Activity and Trend use the shared `ChartReadout` under the
  chart. Heart, Session and Balance pass `selected` and render it themselves.
  Two idioms for one job is one too many — the handover makes that argument
  about the heart window already.
- **Vitals cannot be explored at all.** It draws range bars, not series, and
  three of its five rows have no screen behind them. A vital you cannot open is
  a vital you cannot examine. This is the real gap behind the request.
- **Tiles do not scrub, by design** — a tap navigates. Leave that alone.

So this is a consistency and coverage job of perhaps a day, not a feature.

---

## Readiness, and the composite rule

The handover records, under **The Health tab**, a settled decision:

> No composite scores beyond sleep. […] Readiness is drawn as a band around
> your own baseline, never as a number — the method is a rolling average
> against a smallest worthwhile change, which is a band, and inventing a 0–100
> figure on top would be a clinical claim.

**Items 1 and 2 overturn that, deliberately, on the owner's instruction after
the reasoning was put to them twice.** It is recorded here rather than quietly
contradicted in code, because a settled decision reversed without a note is
indistinguishable from one nobody knew about.

What should survive the reversal, and what the handover was actually protecting:

- **A total is never shown without its parts.** `SleepScore` already works this
  way and says when it stood on three components instead of four.
- **Every figure is measured against your own history**, never a population
  reference. 50% means "like your usual".
- **A figure with nothing behind it is null**, and says so, rather than
  defaulting to a confident middle.

A readiness score that keeps those three is a summary of your own readings. One
that drops them is the clinical claim the rule was written against. The rule was
right about the danger; the owner's judgement is that the feature is worth it
anyway, with the guardrails kept.

---

## Order

As listed, with two amendments from the evidence rather than preference:

1. **Export can land any time** — it depends on nothing and protects everything.
2. **Biological age needs the VO2 max read first**, so it cannot be second in
   practice. Build the metric, live with it for a fortnight, then decide whether
   the figure on top is honest enough to ship.

Everything else in `explorations/feature-gaps.md` is deliberately not on this
list: heart-rate dip, acute-vs-chronic training load, sleep consistency, the
outlier flag, calendar buzz, and the battery and disconnect alerts. They were
the report's own top ten; these seven are the owner's.
