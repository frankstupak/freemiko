# Reverse Engineering Findings

## Summary

The Miko 3 is built by Emdoor (ODM) for Emotix/KlugTek. It runs Android 9 on a MediaTek MT8167A SoC with a GD32F303 MCU as a motor/sensor controller. The device was designed to require cloud connectivity for all user-facing features. When Miko shut down their cloud infrastructure, every unit became a paperweight stuck on a pairing screen.

## Architecture

```
HEAD BOARD (EMT6736A_V1.0)
├── MT8167A/8168 SoC — Android 9 (system-as-root)
│   ├── com.miko.launcher_app    — HOME, renders face on LCD
│   ├── com.miko.mikoplus        — brain app, conversation, games
│   ├── com.example.root.serviceexam — MCU UART service (AIDL)
│   └── 14 game/content APKs
├── 1280×720 touch LCD + front camera
├── MT7658BWSN WiFi/BT
├── Kimtigo KMTQ8A1032GxA 32GB eMMC
└── UART to base board (460800 8N1)

BASE BOARD (EMK3_MAINBOARD_A003_V5.4 or V3.1.6)
├── GD32F303 MCU (ARM Cortex-M4)
│   ├── 3x VL53L0X ToF sensors
│   ├── SH3001 IMU (accel + gyro)
│   ├── Motor drivers (wheels, head tilt)
│   ├── Battery charger + power sequencing
│   └── 4 buttons (power, vol+/-, mute)
└── Internal micro-USB → MT8167A debug port
```

## How ADB access was restored

### The problem

The locked unit enumerates as `0e8d:2008` (MTP mode) on USB. ADB is not enabled because Android's `persistent_properties` file in userdata overrides the ROM's default USB mode.

### The key discovery

The ROM's `default.prop` (in the boot partition) contains:

```
persist.sys.usb.config=adb
ro.adb.secure=0
ro.debuggable=1
```

This means ADB is the *default* USB mode — it's only overridden at runtime by the `persistent_properties` protobuf in `/data/property/`. Wiping userdata removes the override and restores the default.

### Approach that worked

1. mtkclient catches the MediaTek preloader via USB (device ID `0e8d:2000`)
2. The preloader has no secure boot, no DAA, no SLA — fully unprotected
3. mtkclient uploads a Download Agent and erases the userdata partition
4. On reboot, Android reformats userdata fresh
5. `persist.sys.usb.config=adb` from `default.prop` takes effect
6. ADB comes up as `0e8d:201c` with root access, no authorization needed

### Approaches that failed

- **Writing `persist.sys.usb.config=adb` to the persist partition** — the property is stored in userdata (`/data/property/persistent_properties`), not the persist partition. This is a common misconception on MTK devices.
- **Patching LK (Little Kernel) bootloader** — LK has `androidboot.usbconfig=N` strings but doesn't use them on this device. USB mode is entirely controlled by Android init properties.
- **Writing FTUE bypass files alone** — bypasses the pin screen but not the factory test screen.

## The factory test screen

### Root cause

After wiping userdata, `com.miko.mikoplus` shows a factory hardware test screen (battery, TOF, buttons, IMU, camera). This is not a flag-gated mode — it's a stuck init.

### Technical detail

Decompilation (jadx) of three APKs revealed the full chain:

1. `com.miko.mikoplus` MainActivity unconditionally loads `MCUTestFragment` on startup
2. The only exit is an AIDL callback `ClientData{type:"UI_COMMAND", message:"ROOT"}` from the MCU service
3. This callback is produced by `AndroidUnityInterface.gotoRoot()` in `com.example.root.serviceexam`
4. `gotoRoot()` is only called from the normal interaction engine — after MCU init, DSP init, network connect, and cloud login all complete
5. Cloud login requires `miko3-k8s-admin1.miko2.co.in` and `miko3-aks-ingress.miko2.co.in` — both are NXDOMAIN
6. Login never completes → `gotoRoot()` never fires → stuck on test screen

### Why file/flag changes don't work

- `TEST_FLAG` in `miko1.properties` — only read for mic test thresholds, not a mode gate
- `isAppOpenFirstTime` in SharedPrefs — overwritten by the app on startup
- `/sdcard/klug/factory/` directory — contains test results that are written but never read as a gate
- `/sdcard/FTpass.txt` — written on test pass, never read

The gate is a runtime AIDL event from the MCU service. No file or property controls it.

### Solution

Replace the HOME launcher. The dead Miko apps are disabled; a standard Android launcher becomes HOME. The device is fully usable as a regular Android tablet.

## Expression animation format

Face animations are stored as text files in `/sdcard/klug/APPS/expressions/`. Each file contains `<block>` elements with `<expression>` JSON entries. Each expression has channels:

| Channel | Purpose | Example |
|---------|---------|---------|
| `ix1` | Face display (MP4 filename) | `"frame":"Happy_Content_Free.mp4"` |
| `ax` | Audio clip | `"data":"wakeup_face_2_1.m"` |
| `mx` | Motor commands (linear/angular velocity) | `"linear":1,"angular":0,"time":10` |
| `dx` | Delay/timing (ms) | `"time":2000` |
| `tx` | Text/speech | TTS string |
| `rx` | Reserved | unused |

### Asset inventory

- 225 face animation MP4s (801MB) in `/sdcard/klug/APPS/images/`
- 360 expression script files (1.6MB) in `/sdcard/klug/APPS/expressions/`
- 2,780 audio clips (640MB) in `/sdcard/klug/audio/`
- Full emotion taxonomy: Happy, Sad, Angry, Fearful, Surprised, Contemplative, Bad, Disgusted
- Sub-emotions per category (e.g., Happy → Content, Playful, Proud, Peaceful, Powerful, Optimistic, Accepting, Interested, Trusting)

## MCU UART protocol

Communication between Android and the GD32 MCU runs at 460800 baud, 8N1, over `/dev/ttyMT*`.

The MCU service (`com.example.root.serviceexam`) handles all serial communication. Key commands observed:

- `GETER` — MCU status query (returns error flags)
- `FAC_RESULTS` — factory test results (written to MCU flash)
- `GETFV` — firmware version query (returns e.g., `6.1:4.0`)
- Motor control: linear velocity, angular velocity, head tilt
- Sensor reads: ToF distance, IMU orientation, button states, battery voltage

The full 50+ command protocol was mapped from the GD32 firmware dump (256KB, `/home/thor/projects/miko3/gd32/`).

## Device security posture

| Property | Value | Implication |
|----------|-------|-------------|
| `ro.secure` | 0 | adbd runs as root |
| `ro.debuggable` | 1 | Debug features enabled |
| `ro.adb.secure` | 0 | No USB debugging authorization |
| `ro.build.type` | user | Standard user build |
| Build keys | test-keys | Emdoor dev keys, not release |
| Preloader SBC | disabled | No secure boot chain |
| Preloader DAA | disabled | No download agent auth |
| dm-verity | enforcing | But bypassable with root |
| `/system/bin/su` | present | Full root available |

## Decompiled sources

All three APKs decompiled with jadx 1.5.1 and available at `/home/lumen/re/miko3/`:

- `mikoplus_jadx/` — com.miko.mikoplus (55MB, brain app)
- `launcher_jadx/` — com.miko.launcher_app (7.5MB, face renderer)
- `service_jadx/` — com.example.root.serviceexam (MCU UART service)
