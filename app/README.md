# MikoUnchained launcher (`app/`)

The replacement home for an unlocked Miko 3. Three MVP features, one small APK, no Gradle and no
androidx — it builds against `android.jar` alone.

## Features

1. **Home replacement** — `HomeActivity` is a clean landscape launcher (matches the Miko head panel):
   an app drawer over all launchable apps, a settings menu (Android settings, re-apply neuter, restart
   nav bar), MikoUnchained branding. Set as default HOME it replaces the interim KISS launcher.

2. **Built-in persistent nav bar** — `NavBarService` is a foreground service that draws a
   `TYPE_APPLICATION_OVERLAY` back/home/recents bar. As an overlay window it floats above every app and
   survives app switches; `START_STICKY` + the boot receiver keep it alive. Back and recents are
   injected as key events (`input keyevent 4` / `187`) through a long-lived root shell — **no
   accessibility service and no manual "draw over other apps" toggle** (the overlay app-op is granted
   for us by the neuter step as root).

3. **Watchdog neuter** — ServiceExam's `SecurityMonitor` greps `ps` for `adbd` every ~2s and execs
   `su -> reboot`, freezing the unit. `Neuter` materializes the embedded `neuterd` daemon
   (`native/neuterd.c`), which `setns()`es into **init's global mount namespace** and bind-mounts a
   no-op over `/system/bin/reboot`, self-healing if the shadow is wiped. Nothing touches `/system`, so
   verity stays intact. Applied on first run and re-applied every boot by `BootReceiver`.

   The global-namespace bind-mount technique is the one documented by the OpenMiko community
   (`ne3d-4-steve/miko3-adb-boot-agent`); `neuterd.c` here is an independent from-scratch
   implementation for this GPL-3.0 project.

## Layout

```
app/
  AndroidManifest.xml        HOME (landscape) + foreground service + boot receiver + overlay perm
  build.sh                   no-Gradle build: aapt2 -> javac -> d8 -> zipalign -> apksigner
  native/neuterd.c           freestanding arm64 reboot-neuter daemon (raw syscalls, no libc)
  native/build-neuterd.sh    clang + ld.lld -> native/neuterd (embedded into the APK as base64)
  res/                       vector icons, layouts, dark theme
  src/com/mikounchained/launcher/ HomeActivity, AppListAdapter, AppEntry, NavBarService,
                             RootShell, Neuter, BootReceiver
```

## Build

Requires JDK 17, the Android SDK (`build-tools;35.0.0`, `platforms;android-28`) at
`$ANDROID_SDK_ROOT` (default `/home/lumen/android-sdk`), and `clang` + `lld` for `neuterd`.

```bash
cd app
bash native/build-neuterd.sh   # only if you edited neuterd.c (a prebuilt is embedded by build.sh)
bash build.sh                  # -> app/mikounchained-debug.apk
```

The debug keystore is generated on first build and is git-ignored — never commit signing keys.

## Install

See [`../INSTALL.md`](../INSTALL.md) for the on-device adb steps.

## Scope

MVP is these three features. Deferred (phase 2): face-display playback of the extracted expression
assets, OEM-APK restore, and the head/robot-body serial comms bridge.
