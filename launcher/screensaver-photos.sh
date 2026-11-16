#!/usr/bin/env bash
# Puts your photos or aerial video clips on the TV for the screensaver.
#   ./launcher/screensaver-photos.sh ~/Pictures/TV        every .jpg/.jpeg/.png/.webp in that folder
#   ./launcher/screensaver-photos.sh --clear              removes the screensaver photos from the TV
# Without photos of its own the screensaver shows your wallpapers.
set -euo pipefail
export MSYS2_ARG_CONV_EXCL="${MSYS2_ARG_CONV_EXCL:+$MSYS2_ARG_CONV_EXCL;}/sdcard;/data;/storage;/system"
ADB=${ADB:-adb}
DEST=/sdcard/Android/data/tv.dumbtv.home/files/screensaver
if [[ ${1:-} == --clear ]]; then
  "$ADB" shell rm -rf "$DEST" && echo "screensaver photos removed"
  exit 0
fi
src=${1:?usage: $0 <folder of photos> | --clear}
[[ -d $src ]] || { echo "error: $src is not a folder" >&2; exit 1; }
"$ADB" shell mkdir -p "$DEST"
n=0
shopt -s nullglob nocaseglob
for f in "$src"/*.jpg "$src"/*.jpeg "$src"/*.png "$src"/*.webp "$src"/*.mp4 "$src"/*.mkv "$src"/*.webm "$src"/*.mov; do
  "$ADB" push "$f" "$DEST/" >/dev/null && n=$((n + 1)) && echo "  $(basename "$f")"
done
echo "$n files on the TV (video clips play first; photos only when there are no clips)"
