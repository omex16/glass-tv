# Glass build of dumbtv home

A fork of [dumbtv](https://github.com/humblemuzzu/dumbtv) home with an Apple TV style finish for
Fire TV: frosted pill buttons (Wi-Fi, Options, Settings), a greeting over a large thin clock, a
frosted dock band behind the apps, and a white glass sheen with a soft shadow on the focused
tile. Translucent fills only, no blur.

Turn it off or on with Options > Glass look, or `glass=off` / `glass=on` in home.conf.
Add your name to the greeting with `name=Om` in home.conf.

Install (Git Bash, TV connected over adb):

    ./launcher/install.sh --glass

Every push to main that touches launcher/ builds a new APK and publishes it as the latest release.
Undo: `./launcher/install.sh --undo` hands HOME back to Amazon.

## Screensaver

After 3 idle minutes on the home screen your photos fill the screen, each drifting slowly and
cross-fading into the next, with a clock in the corner. Any button brings Home back.

- Change the delay or turn it off: Settings pill > Launcher settings > Screensaver.
- Your own photos: `./launcher/screensaver-photos.sh <folder>`; without them it shows the wallpapers.
- Fire TV's own screensaver starts after 5 minutes and covers this one: set
  Settings > Display & Sounds > Screen Saver > Start Time > Never.
