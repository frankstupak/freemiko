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

## 0. Silence the watchdog first (avoids a mid-install reboot)

Until the neuter is applied, ServiceExam is still running and will reboot the unit a couple of
seconds after it sees `adbd` — which is exactly the window you install in. Disable it before you
start so the install can't be interrupted:

```bash
adb shell pm disable-user --user 0 com.example.root.serviceexam
```

This command itself needs adb, and adb is exactly what the watchdog reacts to, so on a freshly
booted unit you are racing that same ~2s timer. Run it as your first action once the device is up;
if the unit reboots before it lands, reconnect and run it again. The setting persists once it takes,
so one clean hit ends the race for good.

Leave it disabled — FreeMiko's neuter replaces the only behaviour it enforced. Re-enable with
`adb shell pm enable com.example.root.serviceexam` if you ever want the stock watchdog back.

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
adb shell su 0 sh -c 'cat /data/local/tmp/freemiko/status'       # expect: OK
adb shell su 0 sh -c 'wc -c < /system/bin/reboot'                # expect: < 100 (shadowed no-op)
adb shell su 0 sh -c 'ps -A | grep -v grep | grep neuterd'       # expect: neuterd running
adb shell su 0 sh -c 'cat /data/local/tmp/freemiko/neuter.log'   # human-readable trace
```

The `status` file is written by `neuterd` from **inside init's global mount namespace**, so `OK`
means the shadow is effective where the watchdog will see it — not merely in some app's private view.
An `adb shell` runs under `adbd`, which shares init's global namespace, so the `wc -c` check above is
also a true reading (an app's in-process check would not be, which is why FreeMiko relies on the
status file, not its own view of `/system/bin/reboot`).

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
- **`status` shows `ENOSETNS`:** neuterd could not enter init's mount namespace (it needs root and a
  permissive/root-capable context). On the stock userdebug ROM this works; if you re-locked the
  bootloader or changed the ROM it may not, and the neuter will not mount.
- **`status` shows `PENDING` and never `OK`:** the bind-mount isn't taking. Confirm `su` works
  (`adb shell su 0 id` returns uid 0) and that `/data/local/tmp/freemiko/neuterd` exists and is
  executable.
- **OEM home keeps grabbing HOME:** make sure both `pm disable-user` commands in step 3 ran.
