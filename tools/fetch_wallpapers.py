"""Outil ponctuel : cherche des photos de qualité (Wikimedia Commons, licences libres)
et télécharge des aperçus 1920 px avec leurs crédits, pour choisir les fonds d'écran."""
import json, os, re, sys, time, urllib.parse, urllib.request

UA = "SalatiTimeWallpaperFetch/1.0 (https://github.com/smamos-cyber/salat-time)"
API = "https://commons.wikimedia.org/w/api.php"
OUT = sys.argv[1] if len(sys.argv) > 1 else "wallpapers"
QUERIES = [
    "Masjid al-Haram", "Kaaba", "Al-Masjid an-Nabawi", "Sheikh Zayed Grand Mosque", "Sultan Ahmed Mosque",
    "Hassan II Mosque", "Alhambra", "Dome of the Rock", "Shah Mosque Isfahan", "Nasir al-Mulk Mosque",
    "Badshahi Mosque", "Great Mosque of Kairouan", "Great Mosque of Djenné", "Süleymaniye Mosque",
    "Mosque–Cathedral of Córdoba", "Sultan Qaboos Grand Mosque", "Faisal Mosque", "Registan",
    "Wazir Khan Mosque", "Muhammad Ali Mosque", "Putra Mosque", "Taj Mahal", "Blue Mosque Mazar-i-Sharif",
    "Grande Mosquée de Paris", "Islamic geometric patterns tiles", "zellige", "muqarnas", "Sahara dunes",
    "Wadi Rum", "Bou Inania Madrasa", "Selimiye Mosque Edirne", "Jameh Mosque of Yazd", "Imam Reza shrine",
    "Umayyad Mosque", "Al-Azhar Mosque", "Kul Sharif Mosque", "Crystal Mosque", "Istiqlal Mosque",
]
ALLOWED = re.compile(r"^(cc0|public domain|pd|cc by(-sa)? ?[0-9.]*)", re.I)

def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)

def strip(html):
    return re.sub(r"<[^>]+>", "", html or "").strip()

os.makedirs(OUT, exist_ok=True)
meta = []
for qi, q in enumerate(QUERIES):
    for scope in ('incategory:"Quality_images" ', ''):
        data = api({
            "action": "query", "generator": "search", "gsrnamespace": 6, "gsrlimit": 12,
            "gsrsearch": f'{scope}{q} filetype:bitmap', "prop": "imageinfo",
            "iiprop": "url|extmetadata|size", "iiurlwidth": 1920,
        })
        pages = sorted((data.get("query") or {}).get("pages", {}).values(), key=lambda p: p.get("index", 99))
        got = 0
        for p in pages:
            ii = (p.get("imageinfo") or [{}])[0]
            w, h = ii.get("width", 0), ii.get("height", 0)
            em = ii.get("extmetadata", {})
            lic = strip(em.get("LicenseShortName", {}).get("value"))
            if not (w >= 1600 and w > h * 1.3 and ALLOWED.match(lic)):
                continue
            name = f"q{qi:02d}_{got}.jpg"
            try:
                req = urllib.request.Request(ii["thumburl"], headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=120) as r, open(os.path.join(OUT, name), "wb") as f:
                    f.write(r.read())
            except Exception as e:
                print("skip", p["title"], e); continue
            meta.append({"file": name, "query": q, "title": p["title"], "license": lic,
                         "artist": strip(em.get("Artist", {}).get("value"))[:120],
                         "page": ii.get("descriptionurl"), "desc": strip(em.get("ImageDescription", {}).get("value"))[:200]})
            got += 1
            time.sleep(0.5)
            if got >= 3: break
        if got: break
    print(q, "->", sum(1 for m in meta if m["query"] == q))
json.dump(meta, open(os.path.join(OUT, "meta.json"), "w"), ensure_ascii=False, indent=1)
