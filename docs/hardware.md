# Hardware Reference

## Board revisions

| Board | Revision | Status |
|-------|----------|--------|
| Head | EMT6736A_V1.0 | All units observed use this revision |
| Base | EMK3_MAINBOARD_A003_V5.4 | Verified working with mtkclient |
| Base | EMK3_MAINBOARD_V3.1.6 | Untested, different layout |

## SoC

MediaTek MT8167A (quad-core ARM Cortex-A35, detected as MT8168 by mtkclient). 64-bit capable, running 32/64 mixed mode (`bootopt=64S3,32N2,64N2`).

## eMMC

Kimtigo KMTQ8A1032GxA, 32GB. CID: `04010149534f434f4d10dde12be09901` (reports as "ISOCOM").

Partition layout (from GPT dump):

| Partition | Size | Notes |
|-----------|------|-------|
| boot | 16 MB | Android boot.img, no ramdisk (system-as-root) |
| system | 2.7 GB | Root filesystem (mounted at /) |
| vendor | 256 MB | Vendor HAL |
| userdata | ~20 GB | User data, app data, persist properties |
| persist | 48 MB | Vendor persistent data |
| lk / lk2 | 1 MB each | Little Kernel bootloader + backup |
| vbmeta | small | AVB metadata (green state = locked) |
| proinfo | small | Device serial, model info |
| seccfg | small | Security config (bootloader lock state) |

## MCU

GD32F303 (ARM Cortex-M4), 256KB flash. Handles motor control, sensor polling, power management, and button input. Communicates with the MT8167A over UART at 460800 8N1.

Firmware version observed: 6.1

## Sensors

- 3x VL53L0X time-of-flight distance sensors (cliff/obstacle detection)
- SH3001 6-axis IMU (accelerometer + gyroscope) — tilt/pickup detection
- Front-facing camera (head board)
- 4 physical buttons: power, volume up, volume down, mute (base board)

## USB

The internal micro-USB port on the base board routes to the MT8167A SoC (Android). The external barrel jack is power-only (charging).

USB device IDs:
- `0e8d:2000` — MediaTek preloader (boot mode, first 500ms)
- `0e8d:2008` — MTP mode (locked/stock)
- `0e8d:201c` — ADB mode (unlocked)
- `0e8d:2005` — ADB + device mode

## Test pads (head board backside)

| Pad group | Signals | Location |
|-----------|---------|----------|
| USB OTG | VBUS, DP, DM, ID, GND | Bottom edge |
| MSDC1 SD card | DAT0-3, CLK, CMD, VDD | Top-left |
| UART | TX, RX | Right edge |

The UART pads carry GD32-to-MT8167 inter-board traffic (460800 8N1), not a debug console. All baud rates tested produced zero output on these pads when probed externally.
