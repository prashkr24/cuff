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

## Title

> My Fitbit band has no screen, so I spent a month building the app that decides
> what's worth a buzz

## Body

My band only has one output: a vibration. No screen, no display, nothing to
read. So the whole question is what earns one — and the official app doesn't
really let you answer it.

So I built Cuff.

- **Per app, per person, per hour.** One pulse, two, or ringing until you pick
  up. A different speed for your partner than for work.
- **A nudge each hour** you've spent sitting still, if you want one.
- **An optional health half** — steps, sleep, energy, food — that's off by
  default and stays off unless you ask for it.
- **Widgets** in six families and five sizes, stackable in one cell.
- **Nothing leaves your phone.** The app doesn't even request the INTERNET
  permission, so it physically can't phone home.

Free, Android, unofficial, sideloaded. The original thread with the download
and the whole backstory is here: [link]

---

## Shorter body, if the above runs long

My band has no screen. It just buzzes. So I built the app that decides what's
worth a buzz: per app, per person, per hour — one pulse, two, or ringing until
you answer. Optional health half, off by default. Widgets in six families.
Nothing ever leaves the phone; the app doesn't even hold the INTERNET
permission.

Free, Android, unofficial. Original thread and download: [link]

---

## How these were made

- `tools/cap.sh <name> [x y]` — tap, then `adb exec-out screencap` into `raw/`.
- `tools/redact.py` — blurs the personal boxes, copies the rest into
  `shots/screens/`, and crops the home-screen stack off `raw/stack_*.png`.
- `tools/cards.py` — writes `cards/*.html` and shoots each in headless Chrome
  at 2×. Run it with card names to rebuild only those.
