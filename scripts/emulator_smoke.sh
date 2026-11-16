#!/usr/bin/env bash
# Emulator uzerinde VYRO acilis testi. Cikti: out/ klasorune log + ekran goruntusu.
PKG=com.cleantube.app
OUT=out/api-${1:-x}
mkdir -p "$OUT"
APK=app/build/outputs/apk/debug/app-debug.apk

adb wait-for-device
adb shell getprop ro.build.version.sdk > "$OUT/sdk.txt"
adb logcat -c
adb install -r "$APK" 2>&1 | tee "$OUT/install.txt"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 2>&1 | tee "$OUT/monkey.txt"

sleep 12
adb shell pidof $PKG > "$OUT/pid_12s.txt" || echo "NO PROCESS" > "$OUT/pid_12s.txt"
adb exec-out screencap -p > "$OUT/01_after12s.png" || true
sleep 15
adb shell pidof $PKG > "$OUT/pid_27s.txt" || echo "NO PROCESS" > "$OUT/pid_27s.txt"
adb exec-out screencap -p > "$OUT/02_after27s.png" || true

adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d -v threadtime > "$OUT/full.txt" 2>&1
grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|Process: $PKG|has died" "$OUT/full.txt" > "$OUT/summary.txt" || true
echo "=== pid ==="; cat "$OUT/pid_12s.txt" "$OUT/pid_27s.txt"
echo "=== crash buffer ==="; head -80 "$OUT/crash.txt"
exit 0
