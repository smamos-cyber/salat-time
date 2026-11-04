#!/bin/bash
# Captures d'écran sur émulateur : accueil puis écran de blocage. $1 = dossier de sortie, $2 = nom de l'appareil
set -x
OUT=$1; DEV=$2; PKG=com.salatitime.tv
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell settings put system screen_off_timeout 600000
put() { python3 tools/salati_prefs.py "$@" > /tmp/p.xml; adb shell am force-stop $PKG; adb shell "run-as $PKG mkdir -p shared_prefs"; adb shell "run-as $PKG sh -c 'cat > shared_prefs/salati.xml'" < /tmp/p.xml; }
shot() { sleep "$2"; adb exec-out screencap -p > "$OUT/${DEV}_$1.png"; }
put normal medine registan 5
adb shell am start -n $PKG/.ui.MainActivity; shot accueil 10
put normal emeraude "" 5
adb shell am start -n $PKG/.ui.MainActivity; shot accueil_sans_fond 8
put lock fes alhambra 7
adb shell am start -n $PKG/.ui.MainActivity; shot blocage 12
put lock lapis nasir_vitraux 40
adb shell am start -n $PKG/.ui.MainActivity; shot blocage2 12
adb logcat -d | grep -iE "salati|AndroidRuntime|FATAL" | tail -80 > "$OUT/${DEV}_logcat.txt"
