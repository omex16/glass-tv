#!/usr/bin/env bash
# Glass look setup: dock order YouTube, Netflix, Prime Video, F1 (whichever are installed),
# the glass settings, and the wallpaper from ~/Downloads/wallpaper.jpg if it is there.
#   ./launcher/glass-setup.sh
set -euo pipefail
export MSYS2_ARG_CONV_EXCL="${MSYS2_ARG_CONV_EXCL:+$MSYS2_ARG_CONV_EXCL;}/sdcard;/data;/storage;/system"
ADB=${ADB:-adb}
FILES=/sdcard/Android/data/tv.dumbtv.home/files
pkgs=$("$ADB" shell pm list packages | tr -d '\r' | sed 's/^package://')

pick() { # first installed package matching each regex in turn; prints nothing when none does
  for re in "$@"; do
    hit=$(grep -E -i -m1 "$re" <<<"$pkgs" || true)
    if [[ -n $hit ]]; then echo "$hit"; return 0; fi
  done
  return 0
}
dock=()
missing=0
names=(YouTube Netflix "Prime Video" F1)
wants=("^com\.amazon\.firetv\.youtube$|^com\.google\.android\.youtube\.tv$|youtube\.tv$|firetv\.youtube|youtube"
       "^com\.netflix\.ninja$|netflix"
       "^com\.amazon\.avod$|^com\.amazon\.avod\.thirdpartyclient$|avod"
       "f1tv|formula1|formula|\.f1")
for i in 0 1 2 3; do
  p=$(pick "${wants[$i]}")
  if [[ -n $p ]]; then
    dock+=("$p"); echo "${names[$i]}: $p"
  else
    echo "${names[$i]}: not found"; missing=1
  fi
done
if [[ $missing == 1 ]]; then
  echo "your installed apps (send this list if one of the four is missing):"
  "$ADB" shell pm list packages -3 | tr -d '\r' | sed 's/^package:/  /'
fi
order=$(IFS=,; echo "${dock[*]}")
echo "dock: $order"

work=$(mktemp -d)
"$ADB" pull "$FILES/home.conf" "$work/home.conf" >/dev/null 2>&1 || touch "$work/home.conf"
grep -v -E '^(glass|background|dim|labels|position|inputs|library|chips|apps\.order|apps\.hide)=' \
  "$work/home.conf" > "$work/new.conf" || true
cat >> "$work/new.conf" <<CONF
glass=on
background=image
dim=20
labels=focus
position=top
inputs=off
library=off
chips=icons
apps.order=$order
CONF
"$ADB" shell mkdir -p "$FILES"
"$ADB" push "$work/new.conf" "$FILES/home.conf" >/dev/null && echo "settings saved"
if [[ -f $HOME/Downloads/wallpaper.jpg ]]; then
  "$ADB" push "$HOME/Downloads/wallpaper.jpg" "$FILES/wallpaper.jpg" >/dev/null && echo "wallpaper restored"
fi
"$ADB" shell input keyevent KEYCODE_HOME
echo "done: press Home once more if the screen has not changed"
