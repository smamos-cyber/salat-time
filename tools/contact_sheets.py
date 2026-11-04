"""Planches d'aperçus numérotées des images candidates (pour choisir les fonds d'écran)."""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont
src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
meta = json.load(open(os.path.join(src, "meta.json")))
W, H, COLS, ROWS = 480, 270, 4, 3
font = ImageFont.load_default(size=22) if hasattr(ImageFont, "load_default") else None
for s in range(0, len(meta), COLS * ROWS):
    sheet = Image.new("RGB", (W * COLS, (H + 30) * ROWS), "black")
    d = ImageDraw.Draw(sheet)
    for i, m in enumerate(meta[s:s + COLS * ROWS]):
        im = Image.open(os.path.join(src, m["file"])).convert("RGB")
        im.thumbnail((W * 2, H * 2))
        r = max(W / im.width, H / im.height)
        im = im.resize((int(im.width * r) + 1, int(im.height * r) + 1))
        im = im.crop(((im.width - W) // 2, (im.height - H) // 2, (im.width - W) // 2 + W, (im.height - H) // 2 + H))
        x, y = (i % COLS) * W, (i // COLS) * (H + 30)
        sheet.paste(im, (x, y))
        d.text((x + 6, y + H + 3), f"{m['file']}  {m['query'][:28]}", fill="white", font=font)
    sheet.save(os.path.join(out, f"sheet_{s // (COLS * ROWS):02d}.jpg"), quality=80)
json.dump(meta, open(os.path.join(out, "meta.json"), "w"), ensure_ascii=False, indent=1)
