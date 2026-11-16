#!/usr/bin/env bash
# Glass look setup: dock order YouTube, Netflix, Prime Video, F1 (whichever are installed),
# the glass settings, and the wallpaper from ~/Downloads/wallpaper.jpg if it is there.
#   ./launcher/glass-setup.sh
set -euo pipefail
export MSYS2_ARG_CONV_EXCL="${MSYS2_ARG_CONV_EXCL:+$MSYS2_ARG_CONV_EXCL;}/sdcard;/data;/storage;/system"
ADB=${ADB:-adb}
FILES=/sdcard/Android/data/tv.dumbtv.home/files
pkgs=$("$ADB" shell pm list packages | tr -d '\r' | sed 's/^package://')

pick() { # first installed package matching any of the regexes, in order
  for re in "$@"; do
    hit=$(grep -E -m1 "$re" <<<"$pkgs" || true)
    [[ -n $hit ]] && { echo "$hit"; return; }
  done
}
dock=()
for want in "^com\.amazon\.firetv\.youtube$|^com\.google\.android\.youtube\.tv$" \
            "^com\.netflix\.ninja$" \
            "^com\.amazon\.avod$|^com\.amazon\.avod\.thirdpartyclient$" \
            "f1tv|formula1|\.f1\."; do
  p=$(pick "$want")
  if [[ -n $p ]]; then dock+=("$p"); else echo "not found: $want"; fi
done
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
