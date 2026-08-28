# Installing the FreeMiko launcher

The FreeMiko launcher (`app/freemiko-debug.apk`) replaces the interim KISS home with a purpose-built
home screen, draws its own persistent back/home/recents bar, and defuses the ROM's reboot watchdog so
adb stays alive. It is a debug-signed APK — install it with adb after the device is unlocked.

## Prerequisites

- Miko 3 already unlocked (`unlock/freemiko-unlock.sh` completed) and booting to Android.
- An adb session to the device (USB, or adb-over-wifi). First-session bootstrap options are in
  `docs/` — the SD-card sideload trick or the internal-OTG-keyboard method.
- Root on the device: `/system/bin/su` present (it is, on the stock userdebug ROM). Back/recents
  injection and the watchdog neuter need it; the home button and app drawer work without it.

Build the APK yourself if you don't have it: `cd app && bash build.sh` (see `app/README.md`).

## 1. Install

```bash
adb install -r -g app/freemiko-debug.apk
# if 'install' is blocked, push + install as root:
#   adb push app/freemiko-debug.apk /data/local/tmp/
#   adb shell su 0 pm install -r -g /data/local/tmp/freemiko-debug.apk
```

## 2. Grant the overlay app-op (so the nav bar draws with no on-screen toggle)

FreeMiko self-grants this on first launch as root, but do it now so the bar appears immediately:

```bash
adb shell appops set com.freemiko.launcher SYSTEM_ALERT_WINDOW allow
```

## 3. Make FreeMiko the home, retire the Miko home + factory app

```bash
adb shell cmd package set-home-activity com.freemiko.launcher/.HomeActivity
adb shell pm disable-user --user 0 com.miko.launcher_app
adb shell pm disable-user --user 0 com.miko.mikoplus
```

Verify FreeMiko is HOME:

```bash
adb shell cmd package resolve-activity -c android.intent.category.HOME \
  -a android.intent.action.MAIN | grep packageName
# expect: packageName=com.freemiko.launcher
```

(Reversible: `pm enable com.miko.launcher_app` / `pm enable com.miko.mikoplus`.)

## 4. Arm the boot agent + run first-time setup

The first launch clears Android's post-install "stopped" state (so `BOOT_COMPLETED` fires on future
boots), applies the watchdog neuter, and starts the nav bar:

```bash
adb shell am start -n com.freemiko.launcher/.HomeActivity
```

## 5. Verify the watchdog neuter

```bash
adb shell su 0 sh -c 'cat /data/local/tmp/freemiko/neuter.log'   # expect: reboot NEUTERED
adb shell su 0 sh -c 'wc -c < /system/bin/reboot'                # expect: < 100 (shadowed no-op)
adb shell su 0 sh -c 'ps -A | grep -v grep | grep neuterd'       # expect: neuterd running
```

`/system/bin/reboot` is shadowed by a no-op **bind-mount made in init's global mount namespace** — so
ServiceExam's `SecurityMonitor`, when it greps `ps` for `adbd` and execs `su -> reboot`, hits the
no-op and the device no longer reboot-loops. Nothing is written to `/system` (verity stays intact);
the mount clears on the next reboot and FreeMiko's boot receiver re-applies it, with a self-healing
daemon keeping it in place between checks.

## 6. Verify the nav bar

On the device, a bar with **back / home / recents** sits at the bottom over every app. Back and
recents are injected as key events through root; home returns to the FreeMiko drawer.

## 7. Reboot test (optional, now safe)

```bash
adb reboot
```

After boot, `BootReceiver` re-applies the neuter and restarts the nav bar. Rebooting is safe now —
the watchdog's reboot is defused.

## Uninstall / revert

```bash
adb shell pm enable com.miko.launcher_app
adb shell pm enable com.miko.mikoplus
adb uninstall com.freemiko.launcher
adb reboot   # clears the bind-mount neuter (never persisted to /system)
```

## Troubleshooting

- **Nav bar missing:** re-run step 2, then FreeMiko → gear → "Restart nav bar".
- **Back/recents do nothing:** root/`su` not granting FreeMiko. Home still works. Confirm
  `adb shell su 0 id` returns uid 0.
- **Neuter log says "NOT applied":** `su` unavailable or SELinux enforcing. On the stock userdebug
  ROM SELinux is permissive; if you re-locked it, the neuter can't mount.
- **OEM home keeps grabbing HOME:** make sure both `pm disable-user` commands in step 3 ran.
