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

tap_text "Shorts" ; sleep 25; alive "shorts"; texts "02_shorts"
adb shell input swipe 540 1700 540 500 300; sleep 6; alive "shorts-kaydir"; texts "02b_shorts_swipe"
adb shell input tap 540 1000; sleep 1; texts "02c_shorts_tap"
tap_text "Ara";     sleep 2
dump; xy=$(python3 scripts/ui_tap.py "$UI" edit) && adb shell input tap $xy; sleep 1
adb shell input text "music"; sleep 1; adb shell input keyevent 66
sleep 12; alive "arama"; texts "03_search"

dump; xy=$(python3 scripts/ui_tap.py "$UI" card) && { echo "ILK SONUCA TAP -> $xy"; adb shell input tap $xy; }
sleep 25; alive "video"; texts "04_player"
echo "[video] MEDIA_SESSION: $(adb shell dumpsys media_session | grep -m3 -E 'state=PlaybackState' | cut -c1-200)"

# ayar/kalite menusu (oynatici ust basligindaki disli)
adb shell input tap 540 450; sleep 1
tap_text "Ayarlar" && { sleep 2; texts "05_settings_sheet"; dump; xy=$(python3 scripts/ui_tap.py "$UI" tap "360p") && adb shell input tap $xy; sleep 3; alive "kalite-secildi"; texts "06_after_quality"; }
# eski kalite dugmesi
tap_text "Kalite" && { sleep 1; texts "05_quality_menu"; dump; xy=$(python3 scripts/ui_tap.py "$UI" tap "360p") && adb shell input tap $xy; sleep 8; alive "kalite"; texts "06_after_quality"; }
# tam ekran (kontroller gorunsun diye once oynaticiya dokun)
adb shell input tap 540 450; sleep 1
tap_text "Tam ekran" && { sleep 3; echo "[tamekran] rotasyon: $(adb shell dumpsys window | grep -m1 -oE 'mCurrentRotation=[A-Z_0-9a-z]+')"; alive "tamekran"; adb shell input keyevent 4; sleep 2; }
alive "son"
adb shell input keyevent 4; sleep 2; alive "geri"; texts "07_after_back"

# ---- Medya hatti testi (YouTube'dan bagimsiz): CI derlemesi + ornek akislar ----
CIAPK=app/build/outputs/apk/ci/app-ci.apk
if [ -f "$CIAPK" ]; then
  adb shell am force-stop $PKG; adb install -r "$CIAPK" 2>&1 | tail -1; adb logcat -c
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 6
  play() { adb shell am broadcast -n $PKG/.ci.CiPlayReceiver --es kind "$1" --es url "$2" >/dev/null 2>&1; sleep 28; alive "medya-$1"; }
  play progressive "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
  play hls "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
  play dash "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
  echo "[medya] MEDIA_SESSION: $(adb shell dumpsys media_session | grep -m2 -E 'state=PlaybackState' | cut -c1-160)"
  adb logcat -d -v threadtime | grep -E "VYROCI" | cut -c1-260
fi

adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d -v threadtime > "$OUT/full.txt" 2>&1
echo "--- VYRO uygulama loglari ---"; grep -E " (VYRO|VYRO\.[A-Za-z]+) *:| FATAL " "$OUT/full.txt" | cut -c1-300 | head -40
echo "=== crash buffer ==="; head -60 "$OUT/crash.txt" | cut -c1-260
exit 0
