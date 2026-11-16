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
