# Screenshots

**No image files are in this folder yet.** The README's Screenshots table
already points at the seven filenames below, so the pictures appear the moment
the files are dropped in — no README edit is needed.

Nothing here is generated. Every one of these has to come off a real phone with
a real band paired and real readings in Health Connect, because the whole point
of the table is to show what the app actually looks like with somebody's own
data in it. A mock-up would be a nicer picture and a worse one.

## What is wanted

Seven shots, one per surface, in this order.

| File | Surface | What it has to show |
| --- | --- | --- |
| `health-tiles.png` | Health tab | The tile grid, scrolled to the top, with the default-ish layout: a heart rate XL at the head, steps, the energy/nutrition pair, sleep. Tiles should have real figures and visible change chips — an empty grid shows the layout and none of the point. |
| `sleep-detail.png` | Sleep screen | The score with its four parts, the hypnogram, and the stage breakdown, on a night that actually has stages. The Heart screen is an acceptable alternative if a good night is not available: today's trace, the zone pills and the window control. |
| `alerts-apps.png` | Alerts › Apps | The app list with several apps switched on and their rows showing different vibrations, so the per-app model reads at a glance. |
| `person-rule.png` | A person's rules | One caller's screen, showing the rule they hold across apps — the thing that makes per-person rules different from per-app ones. **Blur or replace the name and number before sharing.** |
| `device-live-heart.png` | Device tab | The band's name, battery and signal, with a live heart rate reading in progress so the measuring state is visible. If the timing is awkward, a Device tab with the band connected and battery read is acceptable. |
| `widgets-home.png` | Home screen | At least three widgets of different sizes on a real launcher — ideally one tile widget, one band widget and one arc or type widget — against the owner's own wallpaper. Not the in-app catalogue: the catalogue is a picture of pictures. |
| `morning-report.png` | Morning report | The morning screen with a real report in it, not the settings row that leads to it. |

Two optional extras, if they are easy: `widget-catalogue.png` (Device ›
Widgets, which is worth showing because the catalogue draws every design) and
`edit-tiles.png` (the tile editor mid-edit, with a size panel open).

## Capture settings

- **Portrait**, full screen, no status-bar clutter. Turn on Android's demo mode
  if the clock and battery matter:
  `adb shell settings put global sysui_demo_allowed 1`, then the usual
  `am broadcast -a com.android.systemui.demo` commands.
- **One theme throughout.** Pick the ground the app is actually used in and
  keep every shot in it; a table that alternates light and dark reads as two
  different apps. The README's table is a plain grid and does nothing clever
  for light-or-dark, so whichever is chosen is what everyone sees.
- **PNG**, at the phone's own resolution. No upscaling and no device frames —
  a frame adds 40% to the file for no information.
- **Keep each file under about 400 KB.** `oxipng -o4 --strip safe` or
  `pngquant --quality 70-90` both get a 1080×2400 screenshot there without
  anything visible changing. These are committed to the repository and read on
  the GitHub front page, so weight matters.
- Capture with `adb exec-out screencap -p > health-tiles.png`, which avoids the
  shell mangling the bytes. From Git Bash, device paths need doubling:
  `//sdcard/x.png`, not `/sdcard/x.png`.

## Before committing

- **Check every shot for private data.** Contact names, phone numbers, the
  content of notifications, the app list itself if it is revealing. The person
  screen is the obvious one; the Apps screen and Recent activity are the two
  that are easy to forget.
- Confirm the filename matches the table above exactly, including the `.png`.
  The README links are literal paths.
- `.gitattributes` already marks `*.png` as binary, so nothing needs doing
  there.
