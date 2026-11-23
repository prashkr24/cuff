# -*- coding: utf-8 -*-
"""Builds the store-style cards from real device screenshots.

Nothing here is drawn: every phone is a PNG off the connected handset, with
the personal bits blurred first by redact.py. The page only frames them.
"""
import os, sys, html, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from brand import wordmark

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..'))
CARDS = os.path.join(ROOT, 'cards')
SHOTS = os.path.join(ROOT, 'shots')
SCREENS = os.path.join(SHOTS, 'screens')
CHROME = r'C:\Program Files\Google\Chrome\Application\chrome.exe'

# The app's own colours. Ink ground, Rose accent, exactly as Scheme.kt mixes
# them -- except the page ground, which is lifted a step so the near-black
# screenshots sit on something rather than bleeding into it.
GROUND = '#15181B'
INK = '#E8EDF0'
MUTED = '#92979A'
DIM = '#777B7E'
ROSE = '#E7A6A6'
BEZEL_A = '#2A2E33'
BEZEL_B = '#15181B'
CARD_W = 1600
SHOT_W, SHOT_H = 1080, 2412


def e(s):
    return html.escape(str(s), quote=False)


def phone(name, width):
    """One capture in a device frame, scaled to `width` CSS px."""
    h = round(width * SHOT_H / SHOT_W)
    bez = max(9, round(width * 0.035))
    return ('<div class="ph" style="padding:%dpx;border-radius:%dpx">'
            '<img src="../shots/screens/%s" width="%d" height="%d" style="border-radius:%dpx">'
            '</div>' % (bez, bez + 26, name, width, h, 26))


def slot(name, label, width):
    return ('<div class="slot">%s<div class="tag">%s</div></div>'
            % (phone(name, width), e(label)))


def stacked(names, width, label):
    """Three faces of the same home-screen cell, with one label under them."""
    imgs = ''.join('<img src="../shots/screens/%s" width="%d" '
                   'style="border-radius:18px;display:block;'
                   'box-shadow:0 18px 40px -22px #000000cc">' % (n, width)
                   for n in names)
    return ('<div class="slot"><div class="col">%s</div>'
            '<div class="tag">%s</div></div>' % (imgs, e(label)))


CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:%(w)dpx;height:%(h)dpx;overflow:hidden}
body{background:%(ground)s;color:%(ink)s;
  font-family:Roboto,"Segoe UI",system-ui,-apple-system,sans-serif;
  -webkit-font-smoothing:antialiased;
  display:flex;flex-direction:column;align-items:center;padding:46px 56px 0}
.mark{margin-bottom:24px;opacity:.95}
h1{font-size:54px;line-height:1.06;font-weight:600;letter-spacing:-1.5px;
  text-align:center;max-width:1240px}
h1 em{font-style:normal;color:%(rose)s}
.sub{font-size:21px;line-height:1.45;color:%(muted)s;margin-top:15px;
  text-align:center;max-width:1120px}
.row{display:flex;gap:%(gap)dpx;justify-content:center;align-items:flex-start;
  margin-top:38px}
.col{display:flex;flex-direction:column;gap:20px;justify-content:center}
.slot{display:flex;flex-direction:column;align-items:center}
.ph{background:linear-gradient(155deg,%(bez1)s,%(bez2)s);
  box-shadow:0 1px 0 #ffffff14 inset, 0 34px 70px -28px #000000b3;
  font-size:0}
.tag{margin-top:20px;font-size:17px;font-weight:700;letter-spacing:2.2px;
  text-transform:uppercase;color:%(rose)s;background:%(rose)s1C;
  border:1px solid %(rose)s33;border-radius:999px;padding:9px 20px;white-space:nowrap}
.foot{position:absolute;left:0;right:0;bottom:24px;text-align:center;
  font-size:16px;color:%(dim)s;letter-spacing:.3px}
"""


def card(name, height, headline, sub, body, gap=52, foot=None):
    page = ('<!doctype html><html><head><meta charset="utf-8">'
            '<link href="https://fonts.googleapis.com/css2?'
            'family=Roboto:wght@400;500;600;700&display=swap" rel="stylesheet">'
            '<style>%s</style></head><body>'
            '<div class="mark">%s</div><h1>%s</h1><div class="sub">%s</div>'
            '<div class="row">%s</div>%s</body></html>'
            % (CSS % dict(w=CARD_W, h=height, ground=GROUND, ink=INK, muted=MUTED,
                          dim=DIM, rose=ROSE, bez1=BEZEL_A, bez2=BEZEL_B, gap=gap),
               wordmark(21), headline, sub, body,
               '<div class="foot">%s</div>' % e(foot) if foot else ''))
    os.makedirs(CARDS, exist_ok=True)
    open(os.path.join(CARDS, name + '.html'), 'w', encoding='utf-8').write(page)
    return name, CARD_W, height


# Phone widths, and the card height each implies.
TWO, THREE = 372, 300


def height_for(width, extra=0):
    return 46 + 21 + 24 + 118 + 15 + 31 + 38 + \
        round(width * SHOT_H / SHOT_W) + 2 * max(9, round(width * 0.035)) + \
        20 + 40 + 56 + extra


def build():
    made = []

    made.append(card(
        '1-alerts', height_for(TWO),
        'The band has no screen.<br>It has <em>one buzz</em>, and you decide what earns it.',
        'Every app and every caller, ruled in or ruled out on the phone — then felt on '
        'the wrist.',
        slot('alerts.png', 'Alerts', TWO) + slot('apps.png', 'Apps', TWO),
        gap=84,
        foot='Cuff · an unofficial companion app for the screenless Fitbit Air'))

    made.append(card(
        '2-buzzes', height_for(THREE),
        'Tell two people apart<br><em>without looking.</em>',
        'A pattern, a speed and a strength — per app, per caller. Then a list of what '
        'actually reached the band.',
        slot('calls.png', 'Calls', THREE) +
        slot('ringpattern.png', 'How it buzzes', THREE) +
        slot('alerts_recent.png', 'What got through', THREE)))

    made.append(card(
        '3-health', height_for(THREE),
        'A whole health app<br>you can <em>switch off.</em>',
        'Health Connect readings, drawn properly. Off by default on a fresh install — the '
        'relay runs perfectly well without it.',
        slot('health.png', 'Health', THREE) +
        slot('energy.png', 'Energy', THREE) +
        slot('balance.png', 'Burned vs eaten', THREE)))

    made.append(card(
        '4-health-2', height_for(THREE),
        'Stages, meals and workouts —<br><em>all of it already on your band.</em>',
        'Sleep down to the hypnogram, a food log you can type into, and every session '
        'Google Health wrote.',
        slot('sleep.png', 'Sleep', THREE) +
        slot('foodlog_yday.png', 'Food log', THREE) +
        slot('activity.png', 'Activity', THREE)))

    made.append(card(
        '5-widgets', height_for(TWO),
        'Seventeen tiles, six families,<br>and a <em>stack</em> to swipe through.',
        'Rings, dials, cards and plain type with no card at all — several of them in one '
        'cell when a single reading is not enough.',
        slot('widgets.png', 'Widgets', TWO) +
        slot('widgets_2.png', 'Every style', TWO) +
        stacked(['stack_%d.png' % i for i in (1, 3, 4)], 324, 'One cell, swiped'),
        gap=56))

    made.append(card(
        '6-device', height_for(TWO),
        'Eight grounds, six accents,<br>and <em>nothing hard-coded.</em>',
        'Every grey is mixed from the ground you picked, so changing theme is a theme '
        'change and not a contrast bug.',
        slot('device.png', 'Device', TWO) + slot('appearance.png', 'Theme', TWO),
        gap=84))
    return made


def shoot(made, only=()):
    os.makedirs(SHOTS, exist_ok=True)
    for name, w, h in made:
        if only and not any(o in name for o in only):
            continue
        src = os.path.join(CARDS, name + '.html')
        dest = os.path.join(SHOTS, name + '.png')
        subprocess.run([CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars',
                        '--force-device-scale-factor=2', '--virtual-time-budget=6000',
                        '--screenshot=' + dest, '--window-size=%d,%d' % (w, h),
                        'file:///' + src.replace('\\', '/')],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        size = os.path.getsize(dest) / 1024.0 if os.path.exists(dest) else 0
        print('%-16s %dx%d  %.0f KB' % (name + '.png', w * 2, h * 2, size))


if __name__ == '__main__':
    shoot(build(), tuple(sys.argv[1:]))
