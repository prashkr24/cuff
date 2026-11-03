# Air Bridge — handover

Written 3 November 2026, updated 4 November, at commit `b1cf70d` on branch
`native`.

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

Two tabs, `Alerts` and `Device`. A third, `Health`, is designed and deliberately
not built.

```
Alerts
  [Relaying]  ──────────── the relay's on/off, as one switch
  Schedule              → pause (15 min / 1 hour / indefinitely) + quiet hours
  WHAT GETS THROUGH
    Calls               → the phone's own calls, its ring, its people
    Apps                → every app, its defaults, its alert kinds
    People              → everyone with a rule, across every app
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
`relay_rules.xml` holds the config (currently **version 5**).

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
(private). Fifteen boards plus design notes explaining *why* each screen is the
shape it is — the notes matter more than the pixels.

The working habit this project runs on: **design first, then build.** For
anything structural, update the board, show it, wait for a yes. Small fixes go
straight in. The boards are current as of version 39.

Boards for screens that exist but were never drawn: the per-app caller list, and
a person's rules across apps. Boards drawn but never built: `Health`, `Setup`,
`Widgets`.

## What is not built

- **Widgets.** Designed, with a gallery screen in the app, but no Glance
  implementation behind it.
- **"Pair a different band."** On the Device board, never built.
- **Health tab and Health Connect.** Deliberately deferred.
- **Per-day quiet windows.** The config holds one window for every day; per-day
  would be a config change, not a screen.
- **Telegram end to end.** The identity path is verified from Telegram's source
  and the contacts picker works, but no real Telegram call has matched a rule on
  a device yet.

## If you change the stored config

`RelayRuleStore` is versioned and currently at 5. Add a field by: widening the
`require(version in 2..N)`, defaulting the field from the old value in `decode`,
bumping the number in `encode`, and writing a test that an old config decodes to
the same behaviour. Version 5's migration copies the single old call default into
both call defaults, so nobody's band rings differently after an update — that is
the shape to copy, and the reason it has a test is that the way such a migration
fails is silently.
