"""Generates the "One UI 8" status / tile icon pack for RM Tweaks: original vector glyphs in a rounded, filled style.

Usage: python3 tools/oui8_icons.py app/src/main/res/drawable   (OUI8_SHEET=1 also writes sheet.html, an SVG contact sheet)
Output: <out>/oui8_<key>.xml (Android VectorDrawable, 24x24, white, tinted at runtime).
Elements: ("fill", d, alpha, evenodd) / ("stroke", d, width, alpha).
"""
import math, os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
DIM = 0.3


def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def P(x, y): return f(x) + "," + f(y)


def pt(cx, cy, a, r): return (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))


# ------------------------------------------------------------------ shapes
def band(cx, cy, r, R, a0, a1, rho):
    """Annular sector r..R between angles a0<a1 (degrees, y-down screen coords) with filleted corners; r == 0 → sector."""
    rho = min(rho, (R - r) / 2.2)
    phi = math.degrees(math.asin(rho / (R - rho)))
    d = []
    # outer-left corner
    lt = (R - rho) * math.cos(math.radians(phi))
    p = pt(cx, cy, a0, lt); d.append("M" + P(*p))
    p = pt(cx, cy, a0 + phi, R); d.append("A%s,%s 0 0 1 %s" % (f(rho), f(rho), P(*p)))
    p = pt(cx, cy, a1 - phi, R); d.append("A%s,%s 0 0 1 %s" % (f(R), f(R), P(*p)))
    p = pt(cx, cy, a1, lt); d.append("A%s,%s 0 0 1 %s" % (f(rho), f(rho), P(*p)))
    if r > 0.01:
        phi2 = math.degrees(math.asin(rho / (r + rho)))
        li = (r + rho) * math.cos(math.radians(phi2))
        p = pt(cx, cy, a1, li); d.append("L" + P(*p))
        p = pt(cx, cy, a1 - phi2, r); d.append("A%s,%s 0 0 1 %s" % (f(rho), f(rho), P(*p)))
        p = pt(cx, cy, a0 + phi2, r); d.append("A%s,%s 0 0 0 %s" % (f(r), f(r), P(*p)))
        p = pt(cx, cy, a0, li); d.append("A%s,%s 0 0 1 %s" % (f(rho), f(rho), P(*p)))
    else:
        half = (a1 - a0) / 2
        ta = rho / math.tan(math.radians(half))   # tangent distance along each edge from the apex
        p = pt(cx, cy, a1, ta); d.append("L" + P(*p))
        p = pt(cx, cy, a0, ta); d.append("A%s,%s 0 0 1 %s" % (f(rho), f(rho), P(*p)))
    d.append("Z")
    return "".join(d)


def rrect(x0, y0, x1, y1, rx, cw=True):
    rx = min(rx, (x1 - x0) / 2, (y1 - y0) / 2)
    if cw:
        return ("M%s H%s A%s,%s 0 0 1 %s V%s A%s,%s 0 0 1 %s H%s A%s,%s 0 0 1 %s V%s A%s,%s 0 0 1 %s Z" % (
            P(x0 + rx, y0), f(x1 - rx), f(rx), f(rx), P(x1, y0 + rx), f(y1 - rx), f(rx), f(rx), P(x1 - rx, y1),
            f(x0 + rx), f(rx), f(rx), P(x0, y1 - rx), f(y0 + rx), f(rx), f(rx), P(x0 + rx, y0)))
    return ("M%s V%s A%s,%s 0 0 0 %s H%s A%s,%s 0 0 0 %s V%s A%s,%s 0 0 0 %s H%s A%s,%s 0 0 0 %s Z" % (
        P(x0 + rx, y0) if False else P(x0, y0 + rx), f(y1 - rx), f(rx), f(rx), P(x0 + rx, y1), f(x1 - rx), f(rx), f(rx), P(x1, y1 - rx),
        f(y0 + rx), f(rx), f(rx), P(x1 - rx, y0), f(x0 + rx), f(rx), f(rx), P(x0, y0 + rx)))


def circle(cx, cy, r, cw=True):
    s = 1 if cw else 0
    return "M%s A%s,%s 0 1 %d %s A%s,%s 0 1 %d %s Z" % (P(cx - r, cy), f(r), f(r), s, P(cx + r, cy), f(r), f(r), s, P(cx - r, cy))


def poly(pts):
    return "M" + " L".join(P(x, y) for x, y in pts) + " Z"


# ------------------------------------------------------------------ icons
icons = {}


def wifi(level, cx=12, cy=20.6, scale=1.0, dx=0.0):
    radii = [(0, 5.2), (6.7, 10.3), (11.8, 15.3), (16.8, 20.2)]
    els = []
    for i, (r, R) in enumerate(radii):
        d = band(cx + dx, cy, r * scale, R * scale, 225, 315, 1.25 * scale)
        on = level >= 4 - (3 - i) if False else (i < level)
        els.append(("fill", d, 1.0 if i < max(level, 0) or (level == 0 and False) else DIM, False))
    # level 0 still shows the dot as dim (no signal); level N lights the N inner bands + always the dot when N>0
    return els


for lv in range(5):
    # dot + bands: level 1 → dot, 2 → dot+band1, 3 → +band2, 4 → all
    els = []
    radii = [(0, 4.5), (5.8, 8.9), (10.2, 12.9), (14.2, 16.4)]
    lit = lv
    for i, (r, R) in enumerate(radii):
        els.append(("fill", band(12, 19.6, r, R, 225, 315, 1.1), 1.0 if i < lit else DIM, False))
    icons["wifi_%d" % lv] = els
    # no-internet: smaller fan on the left + exclamation mark
    els = []
    s = 0.84
    for i, (r, R) in enumerate(radii):
        els.append(("fill", band(10.0, 19.4, r * s, R * s, 225, 315, 1.0), 1.0 if i < lit else DIM, False))
    els.append(("fill", rrect(20.1, 8.6, 22.3, 17.0, 1.1), 1.0, False))
    els.append(("fill", circle(21.2, 19.6, 1.25), 1.0, False))
    icons["wifi_noinet_%d" % lv] = els


def bars(level, x0=3.0, w=3.5, gap=1.4, bottom=21.0, heights=(5.5, 9.5, 13.5, 17.5), lit_alpha=1.0):
    els = []
    for i, h in enumerate(heights):
        x = x0 + i * (w + gap)
        els.append(("fill", rrect(x, bottom - h, x + w, bottom, 1.35), lit_alpha if i < level else DIM, False))
    return els


for lv in range(5):
    icons["signal_%d" % lv] = bars(lv)
    # data cut / no service: bars shifted right, small cross on the lower left
    els = bars(lv, x0=8.4, w=3.0, gap=1.2, heights=(5.0, 8.8, 12.6, 16.4))
    els.append(("stroke", "M2.6,15.4 L6.6,19.4 M6.6,15.4 L2.6,19.4", 1.9, 1.0))
    icons["signal_cut_%d" % lv] = els

icons["roaming"] = [("stroke", "M8.2,20 V4.5 H13 A3.9,3.9 0 0 1 13,12.3 H8.2 M12.4,12.3 L16.6,20", 2.4, 1.0)]

bt = "M6.8,7.6 L16.8,16.3 L12,20.6 V3.4 L16.8,7.7 L6.8,16.4"
icons["bluetooth"] = [("stroke", bt, 2.0, 1.0)]
icons["bluetooth_connected"] = [("stroke", bt, 2.0, 1.0), ("fill", circle(3.6, 12, 1.45), 1.0, False), ("fill", circle(20.4, 12, 1.45), 1.0, False)]

# airplane (all subpaths clockwise)
air = [
    rrect(10.55, 1.8, 13.45, 20.6, 1.45),
    poly([(13, 8.6), (21.6, 13.4), (21.6, 15.7), (13, 12.9)]),
    poly([(11, 8.6), (11, 12.9), (2.4, 15.7), (2.4, 13.4)]),
    poly([(13, 17.0), (16.4, 19.5), (16.4, 21.4), (12.6, 20.4)]),
    poly([(11, 17.0), (11.4, 20.4), (7.6, 21.4), (7.6, 19.5)]),
]
icons["airplane"] = [("fill", " ".join(air), 1.0, False)]

icons["alarm"] = [
    ("stroke", circle(12, 13.2, 7.4), 2.1, 1.0),
    ("stroke", "M12,9.4 V13.4 L14.6,15.2", 2.0, 1.0),
    ("stroke", "M3.6,6.4 L6.6,3.8 M20.4,6.4 L17.4,3.8", 2.1, 1.0),
]

icons["dnd"] = [("fill", circle(12, 12, 9.6) + " " + rrect(6.6, 10.75, 17.4, 13.25, 1.25, cw=False), 1.0, False)]

bell = "M12,2.6 C12.8,2.6 13.4,3.2 13.4,4 V4.6 C16.2,5.3 18,7.8 18,10.7 V15.2 L19.6,17.2 C20.1,17.8 19.7,18.6 18.9,18.6 H5.1 C4.3,18.6 3.9,17.8 4.4,17.2 L6,15.2 V10.7 C6,7.8 7.8,5.3 10.6,4.6 V4 C10.6,3.2 11.2,2.6 12,2.6 Z"
clap = "M9.6,19.8 H14.4 A2.4,2.4 0 0 1 9.6,19.8 Z"
icons["ring"] = [("fill", bell + " " + clap, 1.0, False)]
icons["silent"] = [("fill", bell + " " + clap, 1.0, False), ("stroke", "M3.4,3.4 L20.6,20.6", 3.6, 1.0)]
# knock the slash out of the bell: draw a transparent gap by layering the slash twice is not possible in a vector;
# instead the slash is drawn thick and solid (reads as "crossed out" at status-bar size).
icons["silent"] = [("clipfill", bell + " " + clap, "M4.25,0 L24,0 L24,19.75 Z M0,2.25 L0,24 L21.75,24 Z"), ("stroke", "M3.2,2.2 L21,20", 2.2, 1.0)]

icons["vibrate"] = [
    ("fill", rrect(7.4, 3.2, 16.6, 20.8, 2.2) + " " + rrect(9.3, 5.4, 14.7, 18.6, 0.9, cw=False), 1.0, False),
    ("stroke", "M4.2,8.2 V15.8 M19.8,8.2 V15.8 M1.6,10 V14 M22.4,10 V14", 1.8, 1.0),
]

icons["hotspot"] = [
    ("fill", circle(12, 12.6, 2.3), 1.0, False),
    ("stroke", "M8.3,16.3 A5.2,5.2 0 1 1 15.7,16.3", 2.0, 1.0),
    ("stroke", "M5.3,19.3 A9.5,9.5 0 1 1 18.7,19.3", 2.0, 1.0),
]

pin = "M12,2.2 C16.1,2.2 19.2,5.3 19.2,9.4 C19.2,14 14.6,18.6 12.9,20.9 C12.4,21.5 11.6,21.5 11.1,20.9 C9.4,18.6 4.8,14 4.8,9.4 C4.8,5.3 7.9,2.2 12,2.2 Z"
icons["location"] = [("fill", pin + " " + circle(12, 9.4, 2.6, cw=False), 1.0, False)]

icons["nfc"] = [
    ("fill", rrect(3.2, 3.2, 20.8, 20.8, 4.6) + " " + rrect(5.2, 5.2, 18.8, 18.8, 2.9, cw=False), 1.0, False),
    ("stroke", "M8.8,15.6 V9.4 C8.8,8.4 9.9,8 10.5,8.8 L13.5,15.2 C14.1,16 15.2,15.6 15.2,14.6 V8.4", 1.9, 1.0),
]

phones = "M4.2,17.4 V12.6 A7.8,7.8 0 0 1 19.8,12.6 V17.4"
cups = rrect(3.0, 13.4, 7.6, 21.0, 2.0) + " " + rrect(16.4, 13.4, 21.0, 21.0, 2.0)
icons["headset"] = [("stroke", phones, 2.0, 1.0), ("fill", cups, 1.0, False)]
icons["headset_mic"] = [("stroke", phones, 2.0, 1.0), ("fill", cups, 1.0, False), ("stroke", "M18.7,20.6 C18.7,22.2 16.6,22.6 13.6,22.6", 1.6, 1.0), ("fill", circle(12.4, 22.6, 1.2), 1.0, False)]

icons["data_saver"] = [
    ("stroke", "M13.5,3.6 A8.6,8.6 0 1 1 5.9,6.1", 2.2, 1.0),
    ("stroke", "M12,8.4 V15.6 M8.4,12 H15.6", 2.2, 1.0),
]

icons["vpn"] = [
    ("fill", circle(7.6, 12, 4.9) + " " + circle(7.6, 12, 2.0, cw=False), 1.0, False),
    ("fill", rrect(11.8, 10.8, 21.6, 13.2, 1.2), 1.0, False),
    ("fill", rrect(16.0, 12.0, 18.2, 16.8, 1.1) + " " + rrect(19.4, 12.0, 21.6, 15.4, 1.1), 1.0, False),
]

cast_frame = "M2.6,8.4 V6.6 A2.4,2.4 0 0 1 5,4.2 H19 A2.4,2.4 0 0 1 21.4,6.6 V17.4 A2.4,2.4 0 0 1 19,19.8 H13.6"
cast_waves = [("stroke", "M2.6,11.6 A8.2,8.2 0 0 1 10.6,19.8", 1.9, 1.0), ("stroke", "M2.6,15.2 A4.6,4.6 0 0 1 7.2,19.8", 1.9, 1.0), ("fill", circle(3.5, 18.9, 1.25), 1.0, False)]
icons["cast"] = [("stroke", cast_frame, 1.9, 1.0)] + cast_waves
icons["cast_connected"] = [("stroke", cast_frame, 1.9, 1.0), ("fill", rrect(7.4, 7.4, 18.2, 16.6, 1.2), 1.0, False)] + cast_waves

icons["battery_saver"] = [
    ("fill", rrect(9.6, 1.8, 14.4, 4.2, 0.8), 1.0, False),
    ("fill", rrect(6.2, 3.4, 17.8, 22, 2.8) + " " + rrect(8.2, 5.4, 15.8, 20, 1.4, cw=False), 1.0, False),
    ("stroke", "M12,9.4 V16 M8.7,12.7 H15.3", 2.0, 1.0),
]

icons["flashlight"] = [
    ("fill", "M6.4,2.4 H17.6 A1,1 0 0 1 18.6,3.4 V6.2 L15,10.8 V20.4 A1.6,1.6 0 0 1 13.4,22 H10.6 A1.6,1.6 0 0 1 9,20.4 V10.8 L5.4,6.2 V3.4 A1,1 0 0 1 6.4,2.4 Z"
             " " + circle(12, 14.2, 1.3, cw=False), 1.0, False),
]

icons["data"] = [("stroke", "M8.2,20 V4.8 M4.4,8.6 L8.2,4.8 L12,8.6 M15.8,4 V19.2 M12,15.4 L15.8,19.2 L19.6,15.4", 2.1, 1.0)]

icons["night"] = [("fill", "M13.4,2.6 A9.6,9.6 0 1 0 21.4,15.2 A7.6,7.6 0 0 1 13.4,2.6 Z", 1.0, False)]
icons["dark"] = [("fill", circle(12, 12, 9.4) + " " + "M12,4.6 A7.4,7.4 0 0 0 12,19.4 Z", 1.0, True)]

icons["screenrecord"] = [("stroke", circle(12, 12, 8.6), 2.0, 1.0), ("fill", circle(12, 12, 4.4), 1.0, False)]

icons["rotate"] = [
    ("stroke", "M19.4,9.2 A8,8 0 0 0 5.2,7.4 M4.6,14.8 A8,8 0 0 0 18.8,16.6", 2.0, 1.0),
    ("fill", poly([(4.0, 3.6), (8.2, 7.6), (3.6, 8.6)]), 1.0, False),
    ("fill", poly([(20.0, 20.4), (15.8, 16.4), (20.4, 15.4)]), 1.0, False),
]
icons["portrait"] = [
    ("fill", rrect(7.0, 2.6, 17.0, 21.4, 2.4) + " " + rrect(9.0, 4.6, 15.0, 19.4, 1.0, cw=False), 1.0, False),
    ("fill", rrect(10.6, 16.6, 13.4, 17.8, 0.6), 1.0, False),
]

KEYS_STATUS = ["wifi_%d" % i for i in range(5)] + ["wifi_noinet_%d" % i for i in range(5)] + ["signal_%d" % i for i in range(5)] + \
    ["signal_cut_%d" % i for i in range(5)] + ["roaming", "bluetooth", "bluetooth_connected", "airplane", "alarm", "dnd", "ring", "silent",
                                              "vibrate", "hotspot", "location", "nfc", "headset", "headset_mic", "data_saver", "vpn", "cast",
                                              "cast_connected", "battery_saver", "flashlight", "data", "night", "dark", "screenrecord", "rotate", "portrait"]
assert set(KEYS_STATUS) == set(icons), set(icons) ^ set(KEYS_STATUS)

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<!-- RM Tweaks "One UI 8" icon pack: original artwork generated by tools/oui8_icons.py -->\n'


def vector(els):
    out = [HEADER, '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n']
    for e in els:
        if e[0] == "clipfill":
            out.append('  <group>\n    <clip-path android:pathData="%s"/>\n    <path android:fillColor="#FFFFFFFF" android:pathData="%s"/>\n  </group>\n' % (e[2], e[1]))
            continue
        if e[0] == "fill":
            _, d, a, eo = e
            attrs = 'android:fillColor="#FFFFFFFF"'
            if a < 1: attrs += ' android:fillAlpha="%s"' % f(a)
            if eo: attrs += ' android:fillType="evenOdd"'
            out.append('  <path %s android:pathData="%s"/>\n' % (attrs, d))
        else:
            _, d, w, a = e
            attrs = 'android:strokeColor="#FFFFFFFF" android:strokeWidth="%s" android:strokeLineCap="round" android:strokeLineJoin="round"' % f(w)
            if a < 1: attrs += ' android:strokeAlpha="%s"' % f(a)
            out.append('  <path %s android:pathData="%s"/>\n' % (attrs, d))
    out.append("</vector>\n")
    return "".join(out)


def svg(els, color):
    parts = []
    for e in els:
        if e[0] == "clipfill":
            cid = "c%d" % (abs(hash(e[2] + color)) % 100000)
            parts.append('<defs><clipPath id="%s"><path d="%s"/></clipPath></defs><path clip-path="url(#%s)" fill="%s" d="%s"/>' % (cid, e[2], cid, color, e[1]))
            continue
        if e[0] == "fill":
            _, d, a, eo = e
            parts.append('<path fill="%s" fill-opacity="%s" fill-rule="%s" d="%s"/>' % (color, a, "evenodd" if eo else "nonzero", d))
        else:
            _, d, w, a = e
            parts.append('<path fill="none" stroke="%s" stroke-opacity="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round" d="%s"/>' % (color, a, w, d))
    return "".join(parts)


os.makedirs(OUT, exist_ok=True)
for k, els in icons.items():
    with open(os.path.join(OUT, "oui8_%s.xml" % k), "w") as fh:
        fh.write(vector(els))

if not os.environ.get("OUI8_SHEET"):
    print(len(icons), "icons")
    sys.exit(0)
cells = []
for k in KEYS_STATUS:
    cells.append('<div class="c"><svg viewBox="0 0 24 24" width="72" height="72">%s</svg><svg viewBox="0 0 24 24" width="24" height="24">%s</svg><span>%s</span></div>' % (svg(icons[k], "#fff"), svg(icons[k], "#fff"), k))
cells2 = []
for k in KEYS_STATUS:
    cells2.append('<div class="c l"><svg viewBox="0 0 24 24" width="72" height="72">%s</svg><svg viewBox="0 0 24 24" width="24" height="24">%s</svg><span>%s</span></div>' % (svg(icons[k], "#111"), svg(icons[k], "#111"), k))
html = """<html><body style="margin:0;background:#111;font:11px sans-serif;color:#aaa">
<style>.c{display:inline-flex;flex-direction:column;align-items:center;width:110px;margin:6px;gap:4px}.l{background:#eee;color:#333}</style>
<div>%s</div><div style="background:#eee">%s</div></body></html>""" % ("".join(cells), "".join(cells2))
with open("sheet.html", "w") as fh:
    fh.write(html)
print(len(icons), "icons")
