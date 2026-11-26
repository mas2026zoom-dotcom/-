#!/usr/bin/env bash
# Takes screenshots of the debug build on the emulator (CI only).
mkdir -p shots
PKG=com.mas.alsalah
adb wait-for-device
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
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "حول التطبيق"; sleep 2; shot 12-about
adb shell cmd uimode night no || true
sleep 3
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "الرئيسية"; sleep 3; shot 13-home-light
adb logcat -d -s AndroidRuntime:E > shots/crash.log 2>&1 || true; [ -s shots/crash.log ] || rm -f shots/crash.log
exit 0
