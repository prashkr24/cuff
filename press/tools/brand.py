# -*- coding: utf-8 -*-
"""The wordmark, straight off ic_cuff_wordmark.xml, as SVG."""

WORDMARK = '''<svg viewBox="0 0 234 56" width="%(w)s" height="%(h)s" xmlns="http://www.w3.org/2000/svg">
<defs>
 <linearGradient id="g1" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="40" y2="56">
  <stop offset="0" stop-color="#FFD8D1"/><stop offset="1" stop-color="#CE7B82"/></linearGradient>
 <linearGradient id="g2" gradientUnits="userSpaceOnUse" x1="38" y1="26" x2="70" y2="82">
  <stop offset="0" stop-color="#FFE4DE"/><stop offset="1" stop-color="#C06C76"/></linearGradient>
 <linearGradient id="g3" gradientUnits="userSpaceOnUse" x1="54" y1="47" x2="54" y2="61">
  <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#EDCFCB"/></linearGradient>
 <linearGradient id="g4" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="30" y2="56">
  <stop offset="0" stop-color="#FFD8D1"/><stop offset="1" stop-color="#CE7B82"/></linearGradient>
 <clipPath id="strap"><path d="M49,26 h10 a11,11 0 0 1 11,11 v34 a11,11 0 0 1 -11,11 h-10 a11,11 0 0 1 -11,-11 v-34 a11,11 0 0 1 11,-11 z"/></clipPath>
</defs>
<path d="M54.2,18 A28,28 0 1 0 54.2,38 L37.8,38 A14,14 0 1 1 37.8,18 Z" fill="url(#g1)"/>
<g transform="translate(48,-26)">
 <path d="M49,26 h10 a11,11 0 0 1 11,11 v34 a11,11 0 0 1 -11,11 h-10 a11,11 0 0 1 -11,-11 v-34 a11,11 0 0 1 11,-11 z" fill="url(#g2)"/>
 <g clip-path="url(#strap)">
  <path d="M36,34 h36 M36,40 h36 M36,46 h36 M36,52 h36 M36,58 h36 M36,64 h36 M36,70 h36 M36,76 h36" stroke="#8E4A52" stroke-width="2" stroke-opacity="0.38"/>
  <path d="M42,28.5 h24" stroke="#FFFFFF" stroke-width="3.5" stroke-opacity="0.45" stroke-linecap="round"/>
 </g>
 <path d="M40,47 h28 a7,7 0 0 1 0,14 h-28 a7,7 0 0 1 0,-14 z" fill="url(#g3)"/>
 <path d="M34,48 Q30,54 34,60" stroke="#E7A6A6" stroke-width="3.5" stroke-linecap="round" fill="none"/>
 <path d="M28,44 Q23,54 28,64" stroke="#E7A6A6" stroke-width="3.5" stroke-opacity="0.45" stroke-linecap="round" fill="none"/>
 <path d="M74,48 Q78,54 74,60" stroke="#E7A6A6" stroke-width="3.5" stroke-linecap="round" fill="none"/>
 <path d="M80,44 Q85,54 80,64" stroke="#E7A6A6" stroke-width="3.5" stroke-opacity="0.45" stroke-linecap="round" fill="none"/>
</g>
<g transform="translate(150,0)"><path d="M0,0 h36 v13 h-23 v9 h18 v12 h-18 v22 h-13 z" fill="url(#g4)"/></g>
<g transform="translate(198,0)"><path d="M0,0 h36 v13 h-23 v9 h18 v12 h-18 v22 h-13 z" fill="url(#g4)"/></g>
</svg>'''


def wordmark(height=26):
    return WORDMARK % {'w': 234 * height / 56.0, 'h': height}
