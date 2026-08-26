# Teardown Guide

## What you need

- Phillips #1 screwdriver
- Plastic pry tool or guitar pick (optional, for shell clips)
- Micro-USB cable

## Opening the shell

1. Place the Miko 3 face-down on a soft surface
2. Remove the 4 Phillips screws on the bottom plate
3. Lift the bottom plate off
4. The two yellow shell halves are held together by plastic clips at the waist — gently pry apart or flex to separate
5. The base board (green PCB) is now visible inside the lower shell

## Finding the USB port

The micro-USB port is on the base board PCB. It is NOT the external charging port on the back of the robot.

Look for a standard micro-USB connector on the PCB — it's the only micro-USB footprint on the base board. On the V5.4 revision, it's near the center of the board.

**This port routes to the MT8167A Android SoC on the head board via the inter-board connector.** It's the debug/ADB port.

## Connecting for unlock

1. Plug a micro-USB cable from this internal port to your PC
2. The Miko 3 should be powered OFF before connecting
3. Run the FreeMiko unlock script, then power on the robot
4. The script will catch the MediaTek preloader in the first 500ms of boot

## Reassembly

Reverse the disassembly. The shell clips snap back together. No components need to be disconnected — the USB cable can be routed through the gap between shell halves during development.

## Photos

*TODO: Annotated teardown photos showing screw locations, USB port, board layout*

If you have photos of your teardown, please submit a PR to help others.
