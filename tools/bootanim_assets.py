"""CI: boot-animation assets for the Boot animation page (same pinned RisingOS set as nubia-ly).

Downloads every archive of third_party/rising-bootanimations/catalog.json from its pinned commit, verifies size and
SHA-256, copies it unchanged to app/src/main/assets/bootanimations/<id>.zip and renders preview frames:
<id>/NN.webp (<= 36 frames, 270 px wide) and <id>-thumb.webp. Google Monet's RGBA colour masks are coloured with the
descriptor's default palette (like the boot player does). Needs Pillow.
"""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
from urllib.request import urlopen
import hashlib, json, math, re, shutil, sys
from PIL import Image, ImageOps, ImageChops

ROOT = Path(__file__).resolve().parents[1]
CAT = json.loads((ROOT / "third_party/rising-bootanimations/catalog.json").read_text())
CACHE = ROOT / "build/boot-cache"
OUT = ROOT / "app/src/main/assets/bootanimations"
CACHE.mkdir(parents=True, exist_ok=True)
if OUT.exists(): shutil.rmtree(OUT)
OUT.mkdir(parents=True)

def fetch(e):
    path = CACHE / e["file"]
    if not path.exists():
        url = "https://raw.githubusercontent.com/%s/%s/%s/%s" % (CAT["repository"], CAT["commit"], CAT["directory"], e["file"])
        with urlopen(url, timeout=60) as r: data = r.read(e["bytes"] + 1)
        path.write_bytes(data)
    data = path.read_bytes()
    if len(data) != e["bytes"] or hashlib.sha256(data).hexdigest() != e["sha256"]:
        path.unlink(); raise ValueError("size/hash mismatch")
    return path

def colour(im, palette):
    masks = im.split(); comps = []
    for c in range(3):
        w = [m.point(lambda v, k=col[c]: round(v * k / 255)) for m, col in zip(masks, palette)]
        comps.append(ImageChops.add(ImageChops.add(w[0], w[1]), ImageChops.add(w[2], w[3])))
    out = Image.merge("RGB", comps)
    white = Image.new("L", im.size, 255); gray = Image.new("L", im.size, 0)
    for m in masks:
        white = ImageChops.multiply(white, m.point(lambda v: 255 if v >= 13 else 0))
        gray = ImageChops.add(gray, m.point(lambda v: round(v / 4)))
    out.paste(Image.merge("RGB", (gray, gray, gray)), mask=white)
    return out.convert("RGBA")

def build(e):
    src = fetch(e)
    shutil.copyfile(src, OUT / (e["id"] + ".zip"))
    with ZipFile(src) as z:
        lines = z.read("desc.txt").decode().splitlines()
        palette = None; seq = []
        for l in lines:
            if l.startswith("dynamic_colors "): palette = [tuple(bytes.fromhex(c[1:])) for c in l.split()[2:6]]
        for l in lines[1:]:
            f = l.split()
            if not f or f[0] not in ("p", "c"): continue
            part = f[3]
            names = sorted(n for n in z.namelist() if n.startswith(part + "/") and n.lower().endswith((".png", ".jpg", ".jpeg")))
            trims = z.read(part + "/trim.txt").decode().splitlines() if part + "/trim.txt" in z.namelist() else []
            seq += [(n, trims[i] if i < len(trims) else None) for i, n in enumerate(names)]
        step = max(1, math.ceil(len(seq) / 36)); frames = []
        for name, trim in seq[::step]:
            im = Image.open(BytesIO(z.read(name))).convert("RGBA")
            if palette: im = colour(im, palette)
            canvas = Image.new("RGBA", (e["width"], e["height"]), "black")
            x = (e["width"] - im.width) // 2; y = (e["height"] - im.height) // 2
            if trim:
                m = re.fullmatch(r"(\d+)x(\d+)\+(\d+)\+(\d+)", trim.strip())
                if m: _, _, x, y = map(int, m.groups())
            canvas.alpha_composite(im, (x, y))
            frames.append(ImageOps.contain(canvas.convert("RGB"), (270, 600), Image.Resampling.LANCZOS))
    d = OUT / e["id"]; d.mkdir()
    for i, fr in enumerate(frames): fr.save(d / ("%02d.webp" % i), quality=70, method=4)
    best = max(frames, key=lambda fr: sum(fr.convert("L").point(lambda v: 255 if v > 35 else 0).histogram()[128:]))
    best.save(OUT / (e["id"] + "-thumb.webp"), quality=80)
    print(e["id"], e["bytes"], "bytes,", len(frames), "preview frames")

ok = 0
for e in CAT["animations"]:
    try:
        build(e); ok += 1
    except Exception as ex:   # a missing archive only hides that entry in the app; the build goes on
        print("WARNING: bootanimation %s skipped: %s" % (e["id"], ex))
        for p in (OUT / (e["id"] + ".zip"), OUT / (e["id"] + "-thumb.webp")):
            if p.exists(): p.unlink()
        if (OUT / e["id"]).exists(): shutil.rmtree(OUT / e["id"])
print("boot animations ready: %d of %d" % (ok, len(CAT["animations"])))
