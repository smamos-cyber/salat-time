"""Télécharge les fonds d'écran listés dans tools/wallpapers.json (Wikimedia Commons, licences libres)
et les enregistre en 1920×1080 dans res/drawable-nodpi (étape de compilation, comme l'adhan intégré)."""
import io, json, os, sys, time, urllib.parse, urllib.request
from PIL import Image

UA = "SalatiTimeBuild/1.0 (https://github.com/smamos-cyber/salat-time)"
here = os.path.dirname(os.path.abspath(__file__))
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "app/src/main/res/drawable-nodpi")
os.makedirs(out, exist_ok=True)
W, H = 1920, 1080
ok = 0
items = json.load(open(os.path.join(here, "wallpapers.json")))
for w in items:
    dest = os.path.join(out, f"wp_{w['id']}.jpg")
    if os.path.exists(dest):
        ok += 1; continue
    url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(w["file"]) + "?width=2400"
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as r:
                im = Image.open(io.BytesIO(r.read())).convert("RGB")
            scale = max(W / im.width, H / im.height)
            im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
            left, top = (im.width - W) // 2, (im.height - H) // 2
            im.crop((left, top, left + W, top + H)).save(dest, "JPEG", quality=80, optimize=True, progressive=True)
            ok += 1
            print("ok", w["id"])
            break
        except Exception as e:
            print("erreur", w["id"], e)
            time.sleep(3)
    time.sleep(0.5)
print(f"{ok}/{len(items)} fonds d'écran")
if ok < len(items) * 0.8:
    sys.exit("Trop de fonds d'écran manquants")
