#!/usr/bin/env bash
# VYRO emulator testi: acilis -> ana sayfa -> Shorts -> arama -> video oynatma -> kalite/tam ekran.
# Ekran goruntuleri CI artifact'inde; AYRICA her adimdaki ekran metinleri is gunlugune yazilir.
PKG=com.cleantube.app
API=${1:-x}
OUT=out/api-$API
mkdir -p "$OUT"
APK=app/build/outputs/apk/debug/app-debug.apk
UI=$OUT/ui.xml

dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$UI" >/dev/null 2>&1; }
texts() { dump; echo "[$1] EKRAN: $(python3 scripts/ui_tap.py "$UI" texts | cut -c1-700)"; adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
alive() { local p; p=$(adb shell pidof $PKG | tr -d '\r'); echo "[$1] SUREC: ${p:-YOK (CRASH)}"; }
tap_text() { dump; local xy; xy=$(python3 scripts/ui_tap.py "$UI" tap "$1") || { echo "TAP '$1': bulunamadi"; return 1; }; echo "TAP '$1' -> $xy"; adb shell input tap $xy; }

adb wait-for-device
echo "ANDROID SDK: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
adb shell settings put global http_proxy :0 >/dev/null 2>&1
adb logcat -c
adb install -r "$APK" 2>&1 | tail -1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

sleep 8;  alive "t+8s"
sleep 12; alive "t+20s"; texts "01_home"

tap_text "Shorts" ; sleep 6; alive "shorts"; texts "02_shorts"
tap_text "Ara";     sleep 2
dump; xy=$(python3 scripts/ui_tap.py "$UI" edit) && adb shell input tap $xy; sleep 1
adb shell input text "music"; sleep 1; adb shell input keyevent 66
sleep 12; alive "arama"; texts "03_search"

dump; xy=$(python3 scripts/ui_tap.py "$UI" card) && { echo "ILK SONUCA TAP -> $xy"; adb shell input tap $xy; }
sleep 25; alive "video"; texts "04_player"
echo "[video] MEDIA_SESSION: $(adb shell dumpsys media_session | grep -m3 -E 'state=PlaybackState' | cut -c1-200)"

# kalite menusu
tap_text "Kalite" && { sleep 1; texts "05_quality_menu"; dump; xy=$(python3 scripts/ui_tap.py "$UI" tap "360p") && adb shell input tap $xy; sleep 8; alive "kalite"; texts "06_after_quality"; }
# tam ekran (kontroller gorunsun diye once oynaticiya dokun)
adb shell input tap 540 450; sleep 1
tap_text "ullscreen" && { sleep 3; echo "[tamekran] rotasyon: $(adb shell dumpsys window | grep -m1 -oE 'mCurrentRotation=[A-Z_0-9a-z]+')"; alive "tamekran"; adb shell input keyevent 4; sleep 2; }
alive "son"
adb shell input keyevent 4; sleep 2; alive "geri"; texts "07_after_back"

adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d -v threadtime > "$OUT/full.txt" 2>&1
grep -E "VYRO|FATAL|AndroidRuntime" "$OUT/full.txt" | cut -c1-300 | head -60
echo "=== crash buffer ==="; head -60 "$OUT/crash.txt" | cut -c1-260
exit 0
