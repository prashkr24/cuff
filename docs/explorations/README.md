# Explorations — 4 November 2026

Four questions asked in one sitting, answered in parallel. Each report stands
alone; this page is the index and the part that only shows up when you read
all four together.

> **These are dated reports, left as they were written.** Two things have
> changed under them since and they have deliberately not been edited to
> match, because a research note quietly updated is a research note you can
> no longer date. The app was called **Air Bridge** on 4 November and is
> called **Cuff** now — every mention of the old name in these four files
> means this app. And the finding below about `DataOrigin` was true when it
> was written and is not any more: per-reading source filtering landed on
> 6 November, and `health/Written.kt` is now the list and the reasoning.
> `docs/HANDOVER.md` is the living document; these are the thinking that
> went into it.

| Report | Question | Verdict |
|---|---|---|
| [ios-support.md](ios-support.md) | Can this run on iOS? | Health half yes. Alerts half **only in the EU**, on an API that shipped after the README's claim was written. |
| [feature-gaps.md](feature-gaps.md) | What do Whoop, Apple Health, Bevel have that this doesn't? | A journal, an export, a weekly report. Plus seven metrics Google Health already writes and this app never reads. |
| [other-sources.md](other-sources.md) | How hard is supporting other devices? | Already done. Nine of thirteen tiles fill from any Health Connect writer today. |
| [food-sources.md](food-sources.md) | How hard is reading MyFitnessPal's food? | Already done. It would appear in the log today. |

## The thing all four found

**Nothing in `app/src` mentions `DataOrigin`.** Verified: a grep returns no
hits, and every `ReadRecordsRequest` and `AggregateRequest` carries a
`timeRangeFilter` and nothing else. Health Connect reads an absent origin
filter as *every* origin.

That one omission is why three of these four reports say "already done", and
it is also the one bug of the set:

- **Good:** another band's readings, or MyFitnessPal's food, would already
  show up. Multi-source support was never written, and is mostly already
  there.
- **Bad:** the phone's own pedometer writes steps too, under a synthetic
  package name, and `aggregate()` includes it. `stepsToday`, the step-goal
  buzz and the hourly nudge have been running on phone + band steps added
  together. The handover lists the goal pulse as unwitnessed; when it is
  witnessed, expect it to fire early.

The fix is the same line in both directions — the question is per metric
whether you want every writer or only yours, and those answers differ:
steps wants one source, nutrition wants all of them.

## Four corrections to the docs

Each verified in the code, not taken on the subagents' word:

1. **`HANDOVER.md`: "the Log food screen has no macro targets, because
   nothing in the app stores one."** Stale — `HealthGoals.proteinGrams`
   exists, with a `proteinFor(weightKg)` derivation at 1.6 g/kg.
2. **`README.md`: "iOS does not let an app read other apps' notifications."**
   Was true when written. iOS 26.5 shipped `AccessoryNotifications`, which
   forwards full notifications to an accessory's companion app — EU accounts
   on EU devices only.
3. **`README.md`: resources "behind Google Health's credentials."** The DTLS
   handshake uses `BOOTSTRAP_KEY`, hardcoded in the vendored Apache-2.0
   Golden Gate (`air/.../dtls/BootstrapTlsKeyResolver.kt`). The `4.01`s are
   CoAP resource authorisation, which is a later and separate thing. There is
   no credential blocker standing between another platform and the band.
4. **The food log's delete copy** — "whichever app wrote it" — is probably
   false. Health Connect lets an app delete only what it wrote. The handover
   already flags this path as the one never exercised on a device.

And one that is right for a better reason than it knew: the empty blood
oxygen tile. The handover established it from `dumpsys` on this phone;
Google's own Health Connect sync matrix lists oxygen saturation as
**read-only**, meaning Google Health never writes it for any device. Not an
Air limitation — true of every Fitbit and every Pixel Watch.

## What changed under the project while it was being built

- **The Fitbit Web API was switched off around 30 October 2026** — four days
  before these reports. Any plan that routed through it is gone.
- **Garmin's developer program has been closed to new applicants** since
  spring 2026, with no announced restart.
- **Google Fit closed to new developers** on 1 May 2024, and Google states
  there is no alternative to the Fit REST API.

Three doors that a plan written a month earlier would have walked into.

## If you only do one thing

Fix the origin filter. It is two days, it is the only outright bug any of the
four reports found, and it converts "support other devices" and "read
MyFitnessPal" from projects into properties the app already has.

Then write the README paragraph saying the Health tab works with any Health
Connect writer. That sentence is worth more than most of the code in these
reports.

## Open items

Each report ends with its own list. The load-bearing ones:

- **Is the owner EU-based?** Decides the entire iOS alerts question before
  any code is written.
- **Does a notification-forwarding session activate with a phone-side
  keypair?** The iOS alerts half rests on this one experiment, and it may
  simply fail.
- **Does Google Health's Apple Health sync reach Health Connect on Android?**
  If so, Apple Health support costs nothing.
- **`adb shell dumpsys package … WRITE_NUTRITION`** — one command, settles
  which apps on this phone can write food.

Two of the four agents ran out of web-search budget before finishing; their
reports mark the unconfirmed claims inline.
