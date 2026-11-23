# Privacy policy

**Cuff** — Android app, package `com.srineere.cuff`.

Effective 23 November 2026.

## The short version

Cuff reads some of the most personal data a phone holds: every notification
that arrives, who is calling, and — if you switch the Health tab on — your
steps, sleep and heart rate.

**None of it leaves your phone.** Not to us, not to anyone else. There is no
account, no sign-in, no analytics, no crash reporting, no advertising, and no
server belonging to this app for data to be sent to.

That is not only a promise. The app does not request Android's `INTERNET`
permission, which is what Android checks before any app may open a network
connection. A build that tried to send your data somewhere would be stopped
by the operating system, not by our good intentions.

## What the app reads, and why

**Your notifications.** Granting notification access lets Android hand Cuff
every notification your phone posts. Cuff reads the metadata it needs to
decide whether something is worth a buzz and what that buzz should feel like:
which app posted it, whether it is a call, which notification channel it came
from, and whether it is part of a conversation you have chosen to allow.
**Message text and sender names are not read, not stored, and not sent to the
band.** The band has no screen; it only vibrates.

**Who is calling.** To let you give one person their own vibration, Cuff has
to recognise that person again next time. The phone number or call handle is
hashed with SHA-256 and only the hash is kept. The raw number is never
written to storage.

**Your contacts** (optional). Used for one thing: turning a number into a name
so a row says "Priya" instead of digits. The lookup happens when a screen is
drawn and the name is not stored. Refuse this permission and the app shows
numbers.

**Your health data** (optional, off by default). The Health tab is switched
off on a fresh install. Turning it on asks Health Connect for read access to
steps, sleep, heart rate, resting heart rate, heart rate variability,
respiratory rate, skin temperature, VO2 max, exercise, distance, calories
burned, nutrition and weight — granted one at a time, and the app works with
whatever subset you allow. These are read to draw the charts, scores and
widgets in the app and for nothing else.

Two further Health Connect grants are asked for separately: reading further
back than thirty days, so a ninety-day chart is not empty, and reading while
the app is off screen, so a widget and the hourly step nudge work without the
app open.

**The one thing Cuff writes** is food you log yourself, written to Health
Connect as nutrition records so other apps can see it. Nothing else is ever
written, and food you logged before is left alone if you switch the Health
tab off.

**Your band's Bluetooth connection.** Bluetooth permissions are used to find
and talk to your band and nothing else. `BLUETOOTH_SCAN` is declared with
`neverForLocation`: the app does not derive, request or use your location.

## Where it is kept

On your phone, in the app's own private storage, which no other app can read.
That includes your relay rules, your goals, the alert history shown on the
Alerts tab, a cached copy of recent health readings so screens open with data
instead of a spinner, and a local crash log you can read and clear from the
About screen.

**There is no backup.** `allowBackup` is off and both Android's cloud backup
and its device-to-device transfer are explicitly excluded. Nothing is copied
to Google's servers for a smoother reinstall. The cost of that is real:
uninstalling the app deletes everything it held, permanently, and a new phone
starts empty.

## Who it is shared with

Nobody. There is no third party, no analytics provider, no advertising
network, no cloud sync, and no data sold or transferred to anyone for any
purpose.

Two things leave the app only because you ask them to, and only where you
send them:

- **Export** writes your readings as CSV files to a location you pick in the
  system file picker.
- **Food logging** writes nutrition records to Health Connect, where other
  apps you have authorised can read them.

## Deleting it

Uninstalling the app deletes everything it stored. Within the app, the Alerts
history and the crash log can be cleared on their own, and switching the
Health tab off empties the cached readings immediately.

Food already written to Health Connect is not deleted, because at that point
it is your record in Health Connect rather than the app's — remove it there
if you want it gone.

## Children

Cuff is not directed at children and collects nothing from anyone.

## About the code

Cuff is unofficial, and is not made, endorsed or supported by Fitbit or
Google.

The source is not public, so the claims above cannot be checked by reading
it. One of them can be checked without it, and it is the one that matters
most: an app's permissions are listed in the APK itself. Any APK inspector,
or Android's own app info screen, will show that Cuff does not hold the
`INTERNET` permission — and an app without it cannot open a network
connection, whatever its code says.

One honest note for anyone who does inspect the APK: the vendored Golden Gate
transport includes an OkHttp-based remote-debugging class from upstream, so
OkHttp appears among the bundled libraries. Nothing in Cuff calls it, and
without the `INTERNET` permission it could not open a connection if it did.

## Changes

If this policy changes, the new version is published here with a new
effective date, and the change is noted in the app's changelog.

## Contact

<!-- Fill this in before publishing anywhere that requires a privacy policy.
     A store listing needs a contact a stranger can actually reach, and with
     this repository private that cannot be its issue tracker: it has to be
     an address. Left blank rather than guessed at, because which address to
     put in public is not a decision to make on somebody's behalf. -->

_Contact details to be added._

## Where this document lives

This file is the source of truth and sits in a private repository, so the
copy you are reading may be a published mirror of it. A store listing needs
a privacy policy at a public URL, and this app's code is not public; if the
two ever disagree, the version carrying the later effective date is the
current one.
