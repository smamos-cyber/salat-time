"""Fichier de préférences de test pour les captures d'écran sur émulateur."""
import sys, time
lock = sys.argv[1] == "lock"
now = int(time.time() * 1000)
items = {
    "string": {"lat": "48.8566", "lng": "2.3522", "place": "Paris, France", "theme": sys.argv[2], "wallpaper": sys.argv[3],
               "lockPrayer": "ASR", "unlockCode": "1234"},
    "boolean": {"autoLoc": "false", "overlayHint": "true", "showHadith": "true"},
    "long": {"autoLocDay": str(now // 86400000), "lockUntil": str(now + 25 * 60000 if lock else 0)},
    "int": {"lockDuration": "30", "lockHadith": sys.argv[4]},
}
print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>")
for t, kv in items.items():
    for k, v in kv.items():
        print(f'    <string name="{k}">{v}</string>' if t == "string" else f'    <{t} name="{k}" value="{v}" />')
print("</map>")
