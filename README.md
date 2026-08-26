# FreeMiko

Unlock and repurpose orphaned Miko 3 robots whose cloud services have been permanently shut down.

Miko 3 robots are stuck on a pairing screen that phones home to servers that no longer exist — `miko3-k8s-admin1.miko2.co.in` and `miko3-aks-ingress.miko2.co.in` both return NXDOMAIN. No firmware update is coming. FreeMiko turns them back into usable devices.

## What FreeMiko does

1. **Unlocks the device** using a documented MediaTek preloader exploit (mtkclient) — no soldering, no special hardware
2. **Restores ADB access** by wiping userdata, which lets the ROM's built-in `persist.sys.usb.config=adb` take effect
3. **Replaces the dead launcher** with a clean Android home screen
4. **Disables orphaned apps** that crash or freeze without Miko's cloud

## Requirements

- A Miko 3 robot (any color, any region)
- A micro-USB cable
- A PC running Linux, macOS, or Windows
- Python 3.8+ with pip
- ~15 minutes

## Quick start

```bash
# Install mtkclient
pip install mtkclient

# Clone FreeMiko
git clone https://github.com/frankstupak/freemiko.git
cd freemiko

# Open the Miko 3 shell and connect the INTERNAL micro-USB port to your PC
# (see docs/teardown.md for photos)

# Unlock
./unlock/freemiko-unlock.sh
```

The script handles everything: catching the preloader, wiping userdata, waiting for reboot, installing the launcher, disabling dead apps, and enabling the navigation bar.

## What you get

A rooted Android 9 device with:
- 1280x720 capacitive touchscreen
- Front-facing camera
- WiFi 5 + Bluetooth
- 32GB storage (16GB free after cleanup)
- Full ADB root access (`ro.secure=0`)
- GD32F303 MCU for motor/sensor control via UART

## Hardware

| Component | Detail |
|-----------|--------|
| SoC | MediaTek MT8167A (quad-core A35) |
| Display | 1280x720 capacitive touch LCD |
| Storage | Kimtigo 32GB eMMC |
| WiFi/BT | MediaTek MT7658BWSN |
| MCU | GD32F303 (motors, ToF, IMU, buttons) |
| Sensors | 3x VL53L0X ToF, SH3001 IMU |
| OS | Android 9, system-as-root, test-keys |

## Documentation

- [Teardown guide](docs/teardown.md) — opening the shell, finding the USB port
- [Hardware details](docs/hardware.md) — board revisions, pinouts, connectors
- [Reverse engineering](docs/reverse-engineering.md) — how the unlock was found
- [FAQ](docs/faq.md)

## Project status

- [x] Unlock process verified on V5.4 base board
- [x] ADB restore via userdata wipe
- [x] Launcher replacement (KISS launcher interim, FreeMiko APK planned)
- [x] Face animation assets extracted (225 MP4s, 360 expression scripts)
- [x] MCU UART protocol mapped (460800 8N1, 50+ commands)
- [x] MikoPlus / Launcher / MCU Service decompiled (jadx)
- [ ] FreeMiko APK with built-in setup wizard
- [ ] Face animation player
- [ ] MCU serial bridge for external robot control
- [ ] V3.1.6 base board testing

## Legal

This project is for devices you own. FreeMiko is a hardware liberation tool for orphaned consumer devices whose manufacturer has abandoned cloud services. It does not bypass DRM, pirate content, or circumvent security on active products.

Not affiliated with Miko / Emotix / KlugTek. Miko 3 is a trademark of its respective owner.

## License

GPL-3.0
