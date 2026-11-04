"""Outil ponctuel : échantillons des données publiques Mawaqit (recherche + confData)."""
import json, os, re, sys, urllib.parse, urllib.request
UA = "Mozilla/5.0 (X11; Linux x86_64) SalatiTime/1.0"
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json,text/html"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.status, dict(r.headers), r.read().decode("utf-8", "replace")
def trunc(o, depth=0):
    if isinstance(o, list): return [trunc(x, depth+1) for x in o[:3]] + (["…%d" % len(o)] if len(o) > 3 else [])
    if isinstance(o, dict): return {k: trunc(v, depth+1) for k, v in o.items()}
    return o
report = {}
for name, url in [("search_word", "https://mawaqit.net/api/2.0/mosque/search?word=evry"),
                  ("search_cp", "https://mawaqit.net/api/2.0/mosque/search?word=91000"),
                  ("search_latlon", "https://mawaqit.net/api/2.0/mosque/search?lat=48.8566&lon=2.3522"),
                  ("search_page2", "https://mawaqit.net/api/2.0/mosque/search?word=paris&page=2")]:
    try:
        st, h, body = get(url)
        data = json.loads(body)
        report[name] = {"status": st, "count": len(data) if isinstance(data, list) else None, "sample": trunc(data)}
    except Exception as e:
        report[name] = {"error": repr(e)}
slug = None
try:
    slug = report["search_word"]["sample"][0]["slug"]
except Exception: pass
if slug:
    for lang in ["fr"]:
        try:
            st, h, html = get(f"https://mawaqit.net/{lang}/{slug}")
            m = re.search(r"(?:let|var|const)\s+confData\s*=\s*(\{.*?\});\s*\n", html, re.S)
            report["page"] = {"status": st, "len": len(html), "found_confData": bool(m)}
            if not m:
                i = html.find("confData"); report["page"]["context"] = html[max(0, i-200): i+400]
            else:
                conf = json.loads(m.group(1))
                report["confData_keys"] = sorted(conf.keys())
                small = {}
                for k, v in conf.items():
                    if k in ("calendar", "iqamaCalendar"):
                        small[k] = {"type": type(v).__name__, "len": len(v), "first": trunc(v[0]) if isinstance(v, list) and v else None}
                    else:
                        small[k] = v if not isinstance(v, (list, dict)) else trunc(v)
                report["confData"] = small
        except Exception as e:
            report["page"] = {"error": repr(e)}
json.dump(report, open(os.path.join(out, "mawaqit_probe.json"), "w"), ensure_ascii=False, indent=1, default=str)
print(json.dumps(report, ensure_ascii=False)[:3000])
