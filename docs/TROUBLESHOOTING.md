# Troubleshooting

Things that bit us or contributors, so they don't bite you. Commands use `tools/tv.py`; see the
[README](../README.md#doing-it-by-hand) for setting it up.

## Connecting

- **macOS: `adb connect` says "No route to host"** although the port is open. Recent macOS treats
  the Google-signed `adb` as its own app under Local Network privacy and silently blocks it.
  `tools/tv.py` avoids the binary entirely.
- **No "wireless debugging" toggle** on some TVs, Xiaomi among them. USB debugging alone opens
  network ADB on port 5555.
- **USB debugging is greyed out and there is no Wireless debugging entry either**, seen on a carrier
  box ([Airtel Xstream, Android 11](https://github.com/Gathin23/cleanyourtv/issues/227)). Some ISP
  firmware removes both switches, and then no shell can be reached: `tools/tv.py` fails,
  `adb connect <ip>:5555` times out, and so does a port check. That timeout is the firmware, not
  Wi-Fi, the router or the cable, so don't go rebuilding the network for it. Greyed-out rather than
  missing points at a device-owner policy or a provisioning lock; the toggle comes back after a
  firmware update from the ISP, or not at all. Without one of the two switches this repo has nothing
  to talk to.
- **USB debugging never opens port 5555** on some Google TV sets (a Skyworth-built Philips among
  them). Pair once through Wireless debugging with the stock `adb` (`adb pair <ip>:<port> <code>`,
  then `adb connect`), run `adb tcpip 5555`, and `tools/tv.py` works from then on. `dns-sd -B
  _adb-tls-pairing._tcp` (or `adb mdns services`) shows the pairing port while the code is on screen.
  Android switches Wireless debugging off whenever Wi-Fi drops; turn it back on and `adb` reconnects
  without a new code. Port 5555 from `adb tcpip` usually lasts only until the next reboot.
- **USB devices go dead while USB debugging is on**, seen on the Xiaomi Mi Box S: its firmware
  switches the USB port to ADB device mode, which cuts the power to a USB speaker or drive. Switch
  USB debugging off when you're done; the launcher doesn't need it.
- **Close port 5555 when you're done**: plaintext, and a shell for anyone on the LAN when `ro.adb.secure=0`. Steps in the README FAQ.
- **Windows PowerShell 5.1 adds a byte-order mark** to text piped into a program, which turned the
  first batch command into gibberish. `tv.py` now strips it, and the `.ps1` scripts pipe clean UTF-8.
- **"Allow USB debugging?" comes back** although `adb devices` already works: `tv.py` used its own
  key next to the one platform-tools had registered. It now reuses `~/.android/adbkey` when that
  exists. Every attempt with an unaccepted key queues one more prompt on the TV, so dismiss them all
  (Back) before testing with key presses.
- **Git Bash rewrites paths** like `/data/local/tmp` into Windows paths. The scripts set
  `MSYS_NO_PATHCONV=1` for you. Set `JAVA_HOME` and `ANDROID_HOME` before building there.

## Working on the TV

- **Chained `pm` commands** (`pm disable a; pm disable b`) may silently stop after the first. Run
  one per shell call; `--batch` does exactly that.
- **Large pushes hang** through the Python client. Use `tools/b64push.py` for APKs.
- **`adb exec-out screencap -p` gives a broken PNG** on some MediaTek Xiaomi sets: the binary prints
  `Init wrapper sys mutex successful` on stdout first. Save on the TV and pull the file instead:
  `screencap -p /data/local/tmp/s.png`.
- **Play Protect blocked the launcher** ("unsafe app blocked, built for an older version of Android")
  and `pm install` hung, then failed with `INSTALL_FAILED_VERIFICATION_FAILURE`, while it targeted
  SDK 29. It targets 34 now; if you built an old checkout, rebuild.
- **Screenshots come out empty** while Live TV, an HDMI input or Netflix is on screen, seen on TCL
  and a [Sony KDL-49W800G](https://github.com/Gathin23/cleanyourtv/issues/92). Press Home before
  `screencap`. An empty capture means nothing about the ADB connection or playback; check those
  separately.
- **Disable the stock launcher only after the new one is installed**, or the Home button lands on
  a factory test app. Launch the replacement by hand first. `set-home-activity` can report
  `Success` while HOME still resolves to the stock launcher (priority 2) until that one is disabled.
  And check which packages actually handle HOME before disabling anything by name: on the Sony
  above, `com.google.android.leanbacklauncher` has a boot receiver but no HOME activity.
- **The new launcher still doesn't own Home** after the stock one is disabled, seen on Google TV
  (Xiaomi MiTV-MOOR2). Another system app answers HOME: `com.google.android.tungsten.setupwraith`,
  with `.MainActivity` at intent-filter priority 4 and `.RecoveryActivity` at priority 1. You
  cannot outrank it, and the obvious workarounds don't help: Android ignores `android:priority` on
  HOME filters for apps that aren't on the system image, `cmd package set-home-activity` prints
  `Success` without changing anything (not even after a reboot), and `pm disable-user` on one of
  setupwraith's components is refused with a SecurityException. Disabling the whole
  `com.google.android.tungsten.setupwraith` package is what frees the slot. It is the first-run
  setup wizard, so everyday use is unaffected, but re-enable it before a factory reset - list it in
  your `debloat.txt` and `tools/restore.sh` does that for you.
- **The TV drops off Wi-Fi for good the moment setupwraith is disabled**, seen on Google TV 14 (Sony
  BRAVIA 9 II, Philips R8G, Skyworth SWTV-24NA-4K NF, Xiaomi TV Box S 3rd gen MiTV-AFMU0). A Wi-Fi network saved during the first-run
  wizard belongs to setupwraith, and Android deletes an app's saved networks when the app is disabled.
  The TV is fine but offline, so ADB and the phone remote stop working, and a restart doesn't bring
  the network back. Check before disabling it: `tools/tv.py "dumpsys wifi" | grep -o 'cname=[^ ]*'`.
  If it names setupwraith, forget the network and join it again in Settings > Network & Internet (it
  then belongs to `android.uid.system`), or use Ethernet. `tools/disable.sh` skips setupwraith while it
  owns the network (`FORCE_SETUPWRAITH=1` overrides). Already offline? Rejoin from the TV with its own
  remote or a USB keyboard, then turn Wireless debugging back on if it was in use.
- **The TV drops off Wi-Fi every minute right after the install**, seen on Google TV 14 (Xiaomi
  MiTV-MZTU0): the same setupwraith `.RecoveryActivity` took Home and system_server crash-looped
  on it. The install script now starts the new launcher before disabling the stock one, sets the
  home preference last (Android drops it whenever the set of HOME apps changes), then checks where
  Home resolves and re-enables the stock launcher if it is not the new one. Disable setupwraith as
  above. To confirm it is this bug: `logcat -b crash` shows `FATAL EXCEPTION IN SYSTEM PROCESS:
  android.display`, and the main log shows `setupwraith/.RecoveryActivity` being started every
  200 ms or so. If you're already in the loop:
  `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.
- **No on-screen keyboard** after switching launchers, seen on Xiaomi: the default input method
  pointed at a Baidu keyboard that isn't installed. Fix:
  `tools/tv.py 'ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'`
- **Third-party launchers** often charge for basics like a custom wallpaper. That's why this repo
  has its own.
- **Wrong time zone, clock off by 30 or 60 minutes**, seen on Xiaomi in Iran: the TV picked a
  neighbouring zone, and older firmware ships time zone data from before Iran dropped daylight
  saving in 2022 (on Android 10 and newer, check with
  `tools/tv.py 'cat /apex/com.android.tzdata/etc/tz/tz_version'`). Set the zone yourself:
  `tools/tv.py 'settings put global auto_time_zone 0; cmd alarm set-timezone Asia/Tehran'`.
  If the clock then jumps an hour ahead in spring, a fixed offset keeps it right:
  `tools/tv.py 'cmd alarm set-timezone GMT+03:30'`. The same applies to any country whose rules
  changed after the firmware was built.
- **The network pill says "Wi-Fi" instead of the network name** after `pm clear com.gathin.tvhome`
  or a reinstall from scratch: clearing the launcher's data also drops the location permission
  Android 10 needs to read the SSID. `pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION`
  (the install script does this).
- **Home is "Select a Home app"** after `tools/restore.sh` followed by re-applying a list: disabling a
  package that also claims HOME (PatchWall on Xiaomi) makes Android drop the default. The install
  script now picks the known stock launcher among the candidates by itself; otherwise choose one on
  the TV or pass `STOCK_LAUNCHER=<package>`.

## Accessibility helper enabled but the remote button does nothing

The optional `HomeKeyService` and `InputKeyService` only filter remote keys once Android has bound to
them. The installer's `enabled` message means the setting was written, not that the service is bound.
Read the state without changing anything:

```sh
tools/tv.py 'am get-current-user'
tools/tv.py 'settings --user current get secure enabled_accessibility_services'
tools/tv.py 'dumpsys accessibility'
```

| Helper | Component in the enabled-services list | Key it filters |
| --- | --- | --- |
| Home button | `com.gathin.tvhome/.HomeKeyService` | `KEYCODE_ALL_APPS` (not an ordinary `KEYCODE_HOME`) |
| Input button | `com.gathin.tvhome/.InputKeyService` | `KEYCODE_TV` |

- **Enabled** means the component is in that user's colon-separated list (the class may be written in
  full, `com.gathin.tvhome/com.gathin.tvhome.HomeKeyService`). Match the whole component.
- **Bound** means it appears under that user's `Bound services` section of the dump (`services` on older
  builds). Give it a few seconds after enabling and read again. Labels in the dump can be translated or
  shared with other services; a line containing "Home" is not proof.
- **Working** means the physical button does the right thing from inside another app. An injected key
  over ADB proves navigation, not that the remote sends the code this helper filters.

Enabled but never bound points at a firmware restriction on auto-starting sideloaded services (some TCL
builds log `OP_AUTO_START` denials). Look at `logcat` for the binding failure before changing settings,
and do not rewrite the enabled-services list or grant app-ops as a blanket fix. When reporting, give the
model, build, current user and the relevant redacted lines, not the raw dump.
