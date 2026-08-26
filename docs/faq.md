# FAQ

**Will this brick my Miko 3?**

No. The unlock erases the userdata partition only — the system partition (Android OS, all apps) is untouched. If something goes wrong, you can re-run the unlock script. The preloader exploit is reliable and the device has no secure boot.

**Do I need to solder anything?**

No. The only physical step is opening the shell (4 screws) and plugging in a micro-USB cable to the internal debug port.

**Can I undo the unlock?**

The "lock" was never a security feature — it was a cloud dependency. The device's pairing screen calls servers that no longer exist. There's nothing to undo, and the factory state is permanently broken regardless.

**Will the Miko games still work?**

No. All Miko apps depend on `com.miko.mikoplus` which requires a cloud connection to `miko2.co.in` for login. Those servers are permanently offline (NXDOMAIN). The games crash without the service binding. FreeMiko disables them.

**Can I install other Android apps?**

Yes. The device runs Android 9 with full root access. You can sideload any APK via `adb install`, or install F-Droid for an app store. The screen is 1280x720 landscape, so most apps work well.

**What about the motors and sensors?**

The GD32 MCU on the base board controls motors, ToF sensors, IMU, and buttons. Communication runs over UART at 460800 baud. The MCU service (`com.example.root.serviceexam`) is disabled by default after unlock to prevent the factory test screen. It can be re-enabled if you want MCU access, but you'll need to handle the factory test init sequence.

FreeMiko plans to include a standalone MCU bridge that communicates with the GD32 without the dead cloud dependency.

**I have a V3.1.6 base board. Will this work?**

Untested. The V5.4 base board is confirmed working. If you have a V3.1.6, please try and report back — the unlock script should work identically since the exploit targets the MT8167A SoC on the head board, not the base board.

**Can I use this as a display for another robot?**

Yes — that's one of the primary repurpose targets. The head board (screen + camera + WiFi) can be separated from the base board and mounted on another chassis. The face animation MP4s are available in the `assets/faces/` directory for playback on any video player.

**Is this legal?**

FreeMiko is a tool for hardware you own. The manufacturer has abandoned the product and shut down the cloud services it depends on. This is not circumventing active security or DRM — it's restoring functionality to orphaned hardware. See also: right to repair.
