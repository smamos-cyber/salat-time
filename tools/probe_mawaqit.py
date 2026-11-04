"""Outil ponctuel : échantillons des données publiques Mawaqit."""
import json, os, re, sys, urllib.request, gzip
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
def get(url, extra=None):
    h = {"User-Agent": UA, "Accept": "*/*", "Accept-Language": "fr-FR,fr"}
    h.update(extra or {})
    req = urllib.request.Request(url, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            b = r.read()
            if r.headers.get("Content-Encoding") == "gzip": b = gzip.decompress(b)
            return r.status, r.headers.get("Content-Type"), b.decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type"), e.read().decode("utf-8", "replace")[:500]
    except Exception as e:
        return None, None, repr(e)
def trunc(o):
    if isinstance(o, list): return [trunc(x) for x in o[:3]] + (["…%d" % len(o)] if len(o) > 3 else [])
    if isinstance(o, dict): return {k: trunc(v) for k, v in list(o.items())[:60]}
    if isinstance(o, str): return o[:300]
    return o
rep = {}
cands = [
 "https://mawaqit.net/api/2.0/mosque/search?word=evry",
 "https://mawaqit.net/api/3.0/mosque/search?word=evry",
 "https://mawaqit.net/api/2.0/mosque/map/FR",
 "https://mawaqit.net/api/2.0/mosque/map",
 "https://mawaqit.net/fr/m/search?word=evry",
 "https://mawaqit.net/fr/search?word=evry",
 "https://mawaqit.net/fr/mosques?search=evry",
 "https://mawaqit.net/sitemap.xml",
 "https://mawaqit.net/robots.txt",
 "https://mawaqit.net/fr/",
]
for u in cands:
    st, ct, body = get(u, {"X-Requested-With": "XMLHttpRequest", "Referer": "https://mawaqit.net/fr/"})
    rep[u] = {"status": st, "type": ct, "len": len(body), "head": body[:1500]}
    try: rep[u]["json"] = trunc(json.loads(body))
    except Exception: pass
# Page d'accueil : chercher des URL d'API dans le HTML/JS
st, ct, home = get("https://mawaqit.net/fr/")
rep["home_api_urls"] = sorted(set(re.findall(r"""["'](/api/[^"'\s]+|https://[a-z.]*mawaqit\.net/api/[^"'\s]+)""", home)))[:80]
rep["home_scripts"] = sorted(set(re.findall(r'src="([^"]+\.js[^"]*)"', home)))[:40]
for s in rep["home_scripts"][:15]:
    url = s if s.startswith("http") else "https://mawaqit.net" + s
    st, ct, js = get(url)
    found = sorted(set(re.findall(r"""["'`](/api/[^"'`\s]+|[a-z/]*search[^"'`\s]{0,60})["'`]""", js)))[:60]
    if found: rep.setdefault("js_api", {})[s] = found
# Page mosquée
for slug in ["zawiya-tidjaniya-trappes", "mosquee-devry-courcouronnes"]:
    st, ct, html = get(f"https://mawaqit.net/fr/{slug}")
    m = re.search(r"(?:let|var|const)\s+confData\s*=\s*(\{.*?\});\s*\n", html, re.S)
    d = {"status": st, "len": len(html), "found": bool(m)}
    if m:
        conf = json.loads(m.group(1))
        d["keys"] = sorted(conf.keys())
        d["data"] = {k: (trunc(v) if k not in ("calendar", "iqamaCalendar") else {"len": len(v), "m0": trunc(v[0]) if v else None}) for k, v in conf.items()}
    rep["page_" + slug] = d
json.dump(rep, open(os.path.join(out, "mawaqit_probe.json"), "w"), ensure_ascii=False, indent=1, default=str)
