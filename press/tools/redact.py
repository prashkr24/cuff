# -*- coding: utf-8 -*-
"""Blurs the real contact names and numbers out of the device captures.

Everything here is a screenshot of somebody's actual phone, so a name or a
number that would be readable in a public post is painted over before the
card is built, not after.
"""
import os
from PIL import Image, ImageFilter

RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'raw')
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'shots', 'screens')

# (file, [(x0, y0, x1, y1), ...]) in the 1080x2412 capture's own pixels.
BLUR = {
    'alerts_recent.png': [(195, 1488, 720, 1572)],          # caller name
    'calls.png':         [(205, 1290, 760, 1360),           # caller name
                          (205, 1465, 700, 1535),           # caller name
                          (205, 1540, 900, 1600),           # their number
                          (58, 1288, 175, 1400),            # their initial
                          (58, 1462, 175, 1574)],           # their initial
}
COPY = ['alerts.png', 'apps.png', 'ringpattern.png', 'health.png', 'health_2.png',
        'energy.png', 'balance.png', 'sleep.png', 'sleepbank.png', 'foodlog_yday.png',
        'activity.png', 'widgets.png', 'widgets_2.png', 'device.png', 'appearance.png',
        'nutrition_bottom.png']


def blurred(path, boxes):
    im = Image.open(path).convert('RGB')
    for box in boxes:
        patch = im.crop(box).filter(ImageFilter.GaussianBlur(14))
        im.paste(patch, box)
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, boxes in BLUR.items():
        src = os.path.join(RAW, name)
        if not os.path.exists(src):
            print('missing', name)
            continue
        blurred(src, boxes).save(os.path.join(OUT, name))
        print('redacted', name, len(boxes), 'areas')
    for name in COPY:
        src = os.path.join(RAW, name)
        if not os.path.exists(src):
            print('missing', name)
            continue
        Image.open(src).convert('RGB').save(os.path.join(OUT, name))
        print('copied  ', name)
    # The stack, cropped off the real home screen.
    for i in (1, 2, 3, 4):
        src = os.path.join(RAW, 'stack_%d.png' % i)
        if os.path.exists(src):
            Image.open(src).crop((56, 204, 1016, 1005)).save(
                os.path.join(OUT, 'stack_%d.png' % i))
            print('cropped  stack_%d.png' % i)


if __name__ == '__main__':
    main()
