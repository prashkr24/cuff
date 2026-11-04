# Air Bridge

Calls and notifications on a Fitbit Air, which Fitbit's own app cannot do.

The Air is screenless and ships with no notification support: its motor fires
for alarms and Smart Wake and nothing else. This app drives that motor over
Bluetooth, so your phone's calls and notifications reach your wrist.

Android only. Sideloaded. **Unofficial**: it rides a reverse-engineered,
version-sensitive device protocol, and a firmware or Google Health update can
break it without warning.

## What it does

- **Per-app rules.** Each app you enable gets its own vibration for
  notifications and, if it can ring, a separate one for calls.
- **Calls ring until answered.** A repeating pulse that stops when the phone
  stops ringing — answered, declined, missed, or timed out.
- **Per-person rules.** Give a caller their own vibration, or block them.
  Works for phone calls and for WhatsApp calls, separately.
- **Patterns and strength, chosen separately.** One pulse, two pulses, ring, or
  silent, at either of the Air's two strengths, with a ring's pacing adjustable
  per app and per person.
- **Quiet hours, a pause, and a Stop button** in its own notification.

## The Health tab

The other half of the app, and the reason it wants Google Health at all: the
Air's readings land in Health Connect, and this reads them back out.

Because it reads Health Connect rather than the band, it is not really
Air-specific: anything whose app writes there — a Pixel Watch, Garmin,
Samsung, Polar, Oura — fills most of these screens too. The Air-only parts are
the Device tab, the alerts, and the live heart rate reading.

- **A grid you arrange.** Tiles in five sizes — S, M, W, L and XL — across a
  six-column row, reordered, resized, recoloured or taken off. Each carries a
  trend: the newest third of its window against the oldest, in that tile's own
  units.
- **A screen behind every tile.** Today's heart rate, resting heart rate over
  thirty days, sleep with a score built from your own nights rather than a
  recommended figure, steps, energy in against energy out, workouts, HRV, the
  five vitals, and nutrition.
- **A live reading from the band.** The one figure no Health Connect reader
  can produce, because it is a measurement you ask for rather than a number
  already written. It holds the Air's single session while it runs, so alerts
  queue behind it, and it stops itself after two minutes.
- **Steps counted once.** Your phone counts its own steps and writes them to
  Health Connect beside the band's. Added together they make one walk into two
  and the goal buzzes early, so by default the phone's are left out — Goals
  names every source it found and lets you choose otherwise.
- **Buzzes from your own activity.** One when you cross the step goal, and an
  hourly nudge when an active hour falls short. Both go through the relay, so
  neither can reach your wrist while it is off.
- **A food log — the only thing this app writes.** Entries go to Health
  Connect as nutrition records, and deleting one there is done from the log.
  Everything else on the tab it only reads.

Saved foods are the exception that proves it: they live in Air Bridge, because
Health Connect records what you ate and keeps no list of foods to pick from.

## How a pulse actually works

The Air exposes no "vibrate" command. It has a vibration-*strength* setting,
and it buzzes once when that setting changes. Everything here is built on that
one side effect.

A single pulse therefore costs a secure session: connect, handshake, write,
verify. A ring instead opens that session **once and holds it**, so pulses land
**97–175 ms** apart rather than seconds. Three consequences worth expecting:

- **A call's first buzz is 2.5–3.7 s late.** That is the handshake, and it
  cannot be removed. Pulses after it are fast.
- **Patterns are counts and cadence, not waveforms.** "Two pulses" is two
  settings writes, not a rhythm.
- **Ringing delays Google Health syncs**, because it holds the tracker channel.
  Calls are short; messages leave a cooldown between sessions.

## Two things this hardware cannot do

**The status light cannot be driven.** Tested, not assumed: it has no CoAP
resource (light-shaped paths answer `4.04`, while resources that exist behind
Google Health's credentials answer `4.01`), and the device exposes exactly two
settings within a one-byte selector — haptics, and one that does nothing
visible when toggled. Colour-coding alerts by LED is not possible.

**Wrist taps cannot be detected.** The Air handles them in firmware and sends
nothing to the phone; upstream measured zero traffic from double-taps,
including taps that dismissed a real Fitbit alarm. Tap-to-silence is
impossible, not unimplemented.

## Build

Needs an Android SDK and a JDK 17 or newer. Gradle comes with the wrapper and
the transport is vendored, so there is nothing else to fetch.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Two modules: `:app` is the app, `:air` is the Air's transport. No NDK is
involved — `:air` carries prebuilt `libgg.so` and `libxp.so` rather than
building them, and the APK packs only `arm64-v8a`, the one ABI anybody has
verified. Debug installs as `com.srineere.airbridge.dev`, beside a release
build rather than over it.

Release builds are signed from `key.properties` or the `AIR_RELAY_*`
environment variables. Without either they fall back to the debug key and must
not be distributed.

## Setting it up

1. Pair the Air in Google Health and leave it paired. This app does not own
   the pairing and cannot create one.
2. Grant **Nearby devices** while the app is on screen — the background
   service cannot request permissions.
3. Grant **notification access** when asked.
4. Allow it to **keep running in the background** when Rules offers. On this
   phone that matters more than anything in the code: without it, Android
   stops background work once the screen has been off a while.
5. Pick your apps under Rules, set each one's rows, and press Start.
6. For the Health tab, allow it in **Health Connect** when it asks. It reads
   thirteen record types and writes one, nutrition, which is what logging food
   saves.

Contacts are optional and asked for only when you open a caller list; they are
read to show names, and a contact's number is turned into the same hashed
identity a call arrives with.

## Limits worth knowing

- Android only, and outside the EU that is permanent. iOS has no way for an
  app to read other apps' notifications — except `AccessoryNotifications`,
  which iOS 26.5 added for accessory companions and which Apple restricts to
  EU accounts on EU devices. A reduced version is no way round it: the
  blocker is reading a notification at all, not what you do with it.
- Arm64 phones only; other ABIs are untested upstream.
- Background delivery is *evidenced*, not *guaranteed*: screen-off haptics are
  confirmed, deep idle and post-reboot are not.
- A pulse is verified by reading the setting back. "Completed" means the write
  verified, not that you felt it.
- One ring at a time, and messages stay silent during a call.

## Credit

Built on [ThoreKoritzius/fitbit-air](https://github.com/ThoreKoritzius/fitbit-air)
(MIT) — an independent reverse-engineering of the Air's Golden Gate / DTLS /
CoAP transport, and the reason any of this is possible. This app began as that
project's example relay and has diverged into a product. The transport now
lives here as the `:air` module rather than as a package dependency, which is
what the rewrite from Flutter to native Android cost.

The held-session ring and the settings/resource probes developed here are
offered back upstream in
[#15](https://github.com/ThoreKoritzius/fitbit-air/pull/15) and
[#16](https://github.com/ThoreKoritzius/fitbit-air/pull/16).

MIT, with Apache-2.0 for the bundled Golden Gate transport. See `LICENSE`,
`LICENSE-APACHE-2.0` and `THIRD_PARTY_NOTICES.md`.
