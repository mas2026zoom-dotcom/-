#!/usr/bin/env bash
# Takes screenshots of the debug build on the emulator (CI only).
mkdir -p shots
exec > >(tee shots/log.txt) 2>&1
set -x
ls -la apk || true
PKG=com.mas.alsalah
adb wait-for-device
adb root > /dev/null 2>&1 || true
sleep 3
adb wait-for-device
adb shell "setprop persist.sys.locale ar-EG" || true
adb shell "setprop persist.sys.language ar" || true
adb shell "setprop persist.sys.country EG" || true
adb shell stop || true
sleep 2
adb shell start || true
sleep 45
adb wait-for-device
adb shell getprop persist.sys.locale
adb shell getprop persist.sys.language
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb install -r apk/app-debug.apk || exit 1
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done
adb shell cmd uimode night yes || true
adb emu geo fix 29.9187 31.2001 || true
shot() { adb exec-out screencap -p > "shots/$1.png"; }
tap_text() {
  adb shell uiautomator dump /sdcard/u.xml > /dev/null 2>&1
  adb pull /sdcard/u.xml /tmp/u.xml > /dev/null 2>&1
  python3 - "$1" <<'PY' > /tmp/xy.txt
import sys,re
import xml.etree.ElementTree as ET
want=sys.argv[1]
best=None
for n in ET.parse('/tmp/u.xml').iter('node'):
    t=(n.get('text') or '')+' '+(n.get('content-desc') or '')
    if want in t:
        m=re.findall(r'\d+',n.get('bounds'))
        x1,y1,x2,y2=map(int,m)
        c=((x1+x2)//2,(y1+y2)//2)
        if best is None or c[1]>best[1]: best=c
if best: print(best[0],best[1])
PY
  read -r X Y < /tmp/xy.txt
  if [ -n "$X" ]; then adb shell input tap "$X" "$Y"; echo "tapped $1 at $X $Y"; else echo "not found: $1"; fi
}
adb shell am start -n $PKG/.MainActivity
sleep 8
shot 00-splash-or-home
tap_text "حدد موقعك"
sleep 8
shot 01-home
adb shell input swipe 540 1800 540 700 400; sleep 1
shot 02-home-scrolled
adb shell input swipe 540 700 540 1800 400; sleep 1
tap_text "القبلة"; sleep 2; shot 03-qibla
tap_text "الأذكار"; sleep 2; shot 04-adhkar
tap_text "المسبحة"; sleep 2; shot 05-tasbeeh
tap_text "المزيد"; sleep 2; shot 06-more
tap_text "مواقيت الصلاة"; sleep 2; shot 07-prayer-times
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "الأدعية"; sleep 2; shot 08-duas
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "التقويم الهجري"; sleep 2; shot 09-calendar
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "الإعدادات"; sleep 2; shot 10-settings
tap_text "تنبيهات الصلاة"; sleep 2; shot 11-settings-alerts
adb logcat -c || true
tap_text "تجربة التنبيه"; sleep 12
adb logcat -d | grep -E "AlSalahVoice|PrayerSpeech|TextToSpeech" > shots/voice.log 2>&1 || true
shot 11b-voice-test
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "حول التطبيق"; sleep 2; shot 12-about
adb shell settings put global hide_error_dialogs 1 || true
adb shell am start -S -n $PKG/.MainActivity --es open quran; sleep 8; shot 14-quran-list
adb shell am start -S -n $PKG/.MainActivity --es open quran --es tab juz; sleep 8; shot 15-quran-juz
adb shell am start -S -n $PKG/.MainActivity --es open quran --es tab bookmarks; sleep 8; shot 16-quran-bookmarks
adb shell am start -S -n $PKG/.MainActivity --es open quran --es download 1; sleep 6; shot 25-quran-download-progress
ready=0
for try in 1 2 3 4 5 6 7 8 9 10; do
  sleep 20
  adb shell am start -S -n $PKG/.MainActivity --es open mushaf:1 > /dev/null 2>&1
  sleep 6
  adb shell uiautomator dump /sdcard/u.xml > /dev/null 2>&1
  adb pull /sdcard/u.xml /tmp/u.xml > /dev/null 2>&1
  if grep -q 'تنزيل المصحف' /tmp/u.xml; then echo "download still pending $try"; else ready=1; echo "download ready after $try"; break; fi
done
shot 26-mushaf-after-download
adb shell am start -S -n $PKG/.MainActivity --es open mushaf:2; sleep 8; shot 17-mushaf-baqarah; page_label
adb shell input swipe 1000 1200 60 1200 100; sleep 3; shot 18-swipe-right-to-left; page_label
adb shell am start -S -n $PKG/.MainActivity --es open mushaf:305; sleep 9; shot 22-mushaf-maryam-305; page_label
adb shell am start -S -n $PKG/.MainActivity --es open mushaf:604; sleep 9; shot 23-mushaf-604; page_label
adb shell am start -S -n $PKG/.MainActivity --es open mushaf:100; sleep 9; shot 24-mushaf-100; page_label
adb shell am start -S -n $PKG/.MainActivity --es open mushaf:1; sleep 8; shot 21-mushaf-fatiha
adb shell logcat -d | grep -i QuranPages > shots/quran.log 2>&1 || true
: > shots/pages.log
for pg in 1 22 42 62 82 102 121 142 162 182 202 222 242 262 282 305 322 342 362 382 402 422 442 462 482 502 522 542 562 582 604; do
  adb shell am start -S -n $PKG/.MainActivity --es open mushaf:$pg > /dev/null 2>&1
  sleep 6
  adb shell uiautomator dump /sdcard/u.xml > /dev/null 2>&1
  adb pull /sdcard/u.xml /tmp/u.xml > /dev/null 2>&1
  if grep -q 'تعذر تحميل\|تنزيل المصحف' /tmp/u.xml; then echo "PAGE FAIL $pg" >> shots/pages.log; else echo "PAGE OK $pg" >> shots/pages.log; fi
done
grep -c 'PAGE OK' shots/pages.log || true
grep 'PAGE FAIL' shots/pages.log || true
adb shell cmd uimode night no || true
sleep 3
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "الرئيسية"; sleep 3; shot 13-home-light
adb logcat -d -s AndroidRuntime:E > shots/crash.log 2>&1 || true; [ -s shots/crash.log ] || rm -f shots/crash.log
exit 0
