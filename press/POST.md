# r/HowToMen post

Six cards in `press/shots/`, in the order they should be uploaded. Every phone
in them is a real screenshot off CPH2569, taken over adb — nothing is drawn.

| # | File | Screens |
| --- | --- | --- |
| 1 | `1-alerts.png` | Alerts · Apps |
| 2 | `2-buzzes.png` | Calls · How it buzzes · What got through |
| 3 | `3-health.png` | Health · Energy · Burned vs eaten |
| 4 | `4-health-2.png` | Sleep · Food log · Activity |
| 5 | `5-widgets.png` | Widgets · Every style · One cell, swiped |
| 6 | `6-device.png` | Device · Theme |

## Before posting — check the redactions

Three real contact names and one phone number were in the captures. They are
blurred by `tools/redact.py` (boxes listed at the top of that file) and the
blur is checked into `shots/screens/`. **Look at cards 1 and 2 at full size
before uploading** — if anything readable slipped through, add a box there and
re-run:

```bash
cd press/tools && python redact.py && python cards.py
```

The rest of what is on screen is real and harmless: step counts, sleep,
calories, two logged meals, the band at 31%. Say so if you would rather swap
in a different day.

---

## Not checked: the subreddit's own rules

r/HowToMen could not be read from here — reddit.com is blocked on every path
this machine has. So the copy below is written to the app, not to that
subreddit's house style, and **two things need checking by eye before it goes
up**:

1. **Whether self-promotion is allowed at all.** Plenty of subs ban "I made a
   thing" outright, or require a flair, or hold it to one day a week. Read the
   rules in the sidebar first; nothing below is worth a ban.
2. **Whether the register fits.** A sub called HowToMen may want the practical
   framing rather than the launch framing — there is a second title for that
   below.

Paste the sidebar and a dozen recent titles into the chat and the copy can be
matched to them properly.

---

## Title — pick one

**A. The launch framing** (recommended if "I built this" posts are welcome)

> My fitness band has no screen, so I spent a month building the app that
> decides what is worth a buzz

**B. The practical framing** (safer if the sub is strictly how-to)

> How I stopped checking my phone: a screenless band and one rule per person

**C. The short one**

> I made my watch stop telling me things I do not care about

## Body

A screenless band has exactly one way to talk to you: it vibrates. No display,
nothing to read. Which makes the only interesting question *what earns a
vibration* — and the official app does not really let you answer it.

So I built one that does. It is called Cuff.

- **Per app, per person, per hour.** One pulse, two, or ringing until you pick
  up. Your partner gets a different rhythm from your work chat, and after a
  week you know who it is without taking the phone out.
- **A nudge each hour** you have spent sitting still, if you want one.
- **An optional health half** — steps, sleep, energy, food — that is off by
  default and stays off unless you ask for it. The relay works fine without it.
- **Home screen widgets** in six families and five sizes, several stackable in
  one cell.
- **Nothing leaves your phone.** The app does not even request the INTERNET
  permission, so it physically cannot phone home.

Free, Android only, unofficial, sideloaded.

The whole backstory — the band, the protocol, the download — is in the
original thread:
https://www.reddit.com/r/fitbit/s/pxps62M6Ui

---

## Shorter body, if the above runs long

My band has no screen. It just buzzes. So I built the app that decides what is
worth a buzz: per app, per person, per hour — one pulse, two, or ringing until
you answer. An optional health half, off by default. Widgets in six families.
Nothing ever leaves the phone; the app does not even hold the INTERNET
permission.

Free, Android, unofficial. The backstory and the download are in the original
thread: https://www.reddit.com/r/fitbit/s/pxps62M6Ui

---

## How these were made

- `tools/cap.sh <name> [x y]` — tap, then `adb exec-out screencap` into `raw/`.
- `tools/redact.py` — blurs the personal boxes, copies the rest into
  `shots/screens/`, and crops the home-screen stack off `raw/stack_*.png`.
- `tools/cards.py` — writes `cards/*.html` and shoots each in headless Chrome
  at 2×. Run it with card names to rebuild only those.
