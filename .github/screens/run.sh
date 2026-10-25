#!/bin/bash
# Steruje UI przez adb. PR z pliku .github/screens/pr (lub $PR).
set -x
PR=${PR:-$(cat .github/screens/pr)}
O=out/screens; mkdir -p $O
A=adb
$A shell wm size 1080x2340; $A shell wm density 443   # 1080/(443/160)=390dp
$A install -r -g app/build/outputs/apk/debug/*.apk || $A install -r -g $(ls app/build/outputs/apk/debug/*.apk|head -1)
PKG=$($A shell pm list packages | grep -o 'app\.retrofilm[^ ]*' | grep -v test | head -1); echo "pkg=$PKG"
python3 - <<'P'
from PIL import Image
import numpy as np
y,x=np.mgrid[0:1600,0:1200]
a=np.stack([(x/1200*255),(y/1600*200+40),(255-x/1200*180)],-1).astype('uint8')
a[1000:,:]= (a[1000:,:]*[0.5,0.9,0.4]).astype('uint8')
Image.fromarray(a).save('/tmp/test.jpg',quality=90)
P
P=.github/screens/photo.jpg
$A push "$P" /sdcard/Pictures/test.jpg; $A shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/test.jpg
$A shell content call --uri content://media --method scan_volume --arg external_primary || true; sleep 5
n=0
shot(){ n=$((n+1)); sleep ${2:-2}; $A exec-out screencap -p > $O/$(printf %02d $n)_$1.png; $A shell uiautomator dump /sdcard/u.xml >/dev/null; $A pull /sdcard/u.xml $O/$(printf %02d $n)_$1.xml >/dev/null; }
tap(){ # tap "regex" na text|content-desc
  $A shell uiautomator dump /sdcard/u.xml >/dev/null; $A pull /sdcard/u.xml /tmp/u.xml >/dev/null
  c=$(python3 - "$1" <<'P'
import re,sys
s=open('/tmp/u.xml',encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>',s):
  n=m.group(0); t=re.search(r' text="([^"]*)"',n).group(1); d=re.search(r'content-desc="([^"]*)"',n).group(1)
  if re.search(sys.argv[1],t) or re.search(sys.argv[1],d):
    b=list(map(int,re.findall(r'\d+',re.search(r'bounds="([^"]*)"',n).group(1))));print((b[0]+b[2])//2,(b[1]+b[3])//2);break
P
)
  [ -n "$c" ] && $A shell input tap $c && sleep 2 && return 0; echo "NOT FOUND: $1"; return 1; }
start(){ $A shell am force-stop $PKG; $A shell am start -W -n $PKG/app.retrofilm.MainActivity; sleep 5; }
pick(){ tap 'Wybierz zdjęcie' && sleep 5 && shot picker 1 && (tap 'test\.jpg|Photo taken|Zdjęcie' || $A shell input tap 200 700); sleep 4; tap '^(Gotowe|Done|Dodaj|Add)$' ; sleep 5; }
theme(){ $A shell cmd uimode night $1; sleep 3; }
case $PR in
27) start; shot start; pick; shot editor_film 3; $A shell input swipe 540 1100 540 1100 2000 & sleep 1; $A exec-out screencap -p > $O/zz_before_hold.png; wait; tap '^Ramka$'; shot editor_ramka; tap '^Film$'; $A shell input swipe 900 1700 200 1700 300; shot film_strip; tap 'Ulubione'; shot ulubione;;
28) start; shot start_dark_default; tap 'Ustawienia'; shot settings_dark; tap '^Jasny$'; shot settings_light; $A shell input keyevent BACK; shot start_light 3; pick; shot editor_light 3; tap '^Ramka$'; shot ramka_light; tap 'Wróć|Wstecz' ; tap 'Ustawienia'; tap '^Ciemny$'; $A shell input keyevent BACK; pick; shot editor_dark 3;;
29) $A shell input keyevent HOME; theme no; shot home_light 3; $A shell input swipe 540 2000 540 400 300; shot drawer_light 3
    theme yes; $A shell input keyevent HOME; shot home_dark 3; $A shell input swipe 540 2000 540 400 300; shot drawer_dark 3
    $A shell settings put secure theme_customization_overlay_packages "'{\"android.theme.customization.theme_style\":\"TONAL_SPOT\"}'" ; $A shell input keyevent HOME
    # themed icons: przeciagnij ikone na ekran domowy i wlacz themed icons w Launcher3 (best effort)
    $A shell input swipe 540 2000 540 400 300; sleep 2; tap 'Retro' && true; $A shell input keyevent HOME; shot home_after 2
    start; shot app_open;;
30) start; shot start; tap 'Ustawienia|Otwórz ustawienia'; shot settings; tap 'Pobieranie i usuwanie'; shot filtry; tap '^Pobierz [^w]' ; shot downloading 2; sleep 20; shot downloaded; $A shell input keyevent BACK; $A shell input keyevent BACK; start; pick; shot editor 3; $A shell input swipe 900 1900 100 1900 300; shot editor_scrolled;;
32) start; shot start; pick; shot editor 3; tap 'Otwórz bibliotekę filmów|Filmy'; shot library 3; tap 'do pobrania|↓'; shot library_tile 2; tap 'Pobierz film'; sleep 20; shot library_downloaded; $A shell input swipe 540 1700 540 600 300; shot library_scrolled;;
esac
ls -la $O
