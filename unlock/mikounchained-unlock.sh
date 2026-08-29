#!/bin/bash
# MikoUnchained unlock script
# Unlocks a Miko 3 robot stuck on the dead cloud pairing screen
# Requires: mtkclient (pip install mtkclient), adb
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="${SCRIPT_DIR}/../app/MikoUnchained.apk"

info()  { echo -e "${CYAN}[*]${NC} $1"; }
ok()    { echo -e "${GREEN}[✓]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
fail()  { echo -e "${RED}[✗]${NC} $1"; exit 1; }

echo ""
echo -e "${CYAN}╔═══════════════════════════════════════╗${NC}"
echo -e "${CYAN}║          MikoUnchained Unlock Tool          ║${NC}"
echo -e "${CYAN}╚═══════════════════════════════════════╝${NC}"
echo ""

# Check dependencies
command -v python3 >/dev/null 2>&1 || fail "python3 not found"
python3 -c "import mtkclient" 2>/dev/null || fail "mtkclient not installed (pip install mtkclient)"
command -v adb >/dev/null 2>&1 || fail "adb not found (install Android SDK Platform Tools)"

ok "Dependencies verified"

# Phase 1: Erase userdata via mtkclient
echo ""
info "Phase 1: Erase userdata partition"
echo ""
warn "Make sure the Miko 3 is:"
warn "  1. Connected via the INTERNAL micro-USB port (not the charging port)"
warn "  2. Powered OFF"
echo ""
read -p "Press Enter when ready, then power on the Miko 3..."

info "Waiting for MediaTek preloader..."

# Find mtk.py location
MTK_PY=$(python3 -c "import mtkclient; import os; print(os.path.join(os.path.dirname(mtkclient.__file__), '..', 'mtk.py'))" 2>/dev/null)
if [ ! -f "$MTK_PY" ]; then
    # Try common locations
    for candidate in \
        "$(pip3 show mtkclient 2>/dev/null | grep Location | cut -d' ' -f2)/mtk.py" \
        "$HOME/.local/lib/python*/site-packages/mtk.py" \
        "/usr/local/lib/python*/site-packages/mtk.py"; do
        expanded=$(ls $candidate 2>/dev/null | head -1)
        [ -n "$expanded" ] && MTK_PY="$expanded" && break
    done
fi

if [ -f "$MTK_PY" ]; then
    python3 "$MTK_PY" e userdata
else
    # Fall back to mtk command if installed globally
    mtk e userdata
fi

ok "Userdata erased"
echo ""

# Phase 2: Wait for reboot and ADB
info "Phase 2: Waiting for device to reboot with ADB..."
info "The first boot takes 1-2 minutes (reformatting userdata)"
echo ""

for i in $(seq 1 120); do
    if adb devices 2>/dev/null | grep -q "device$"; then
        ok "ADB connected"
        break
    fi
    [ $((i % 10)) -eq 0 ] && info "Still waiting... (${i}s)"
    sleep 2
done

adb devices | grep -q "device$" || fail "ADB did not come up after 4 minutes. Check USB connection."

SERIAL=$(adb shell getprop ro.serialno 2>/dev/null | tr -d '\r')
info "Device serial: $SERIAL"

# Phase 3: Install MikoUnchained / KISS launcher
echo ""
info "Phase 3: Installing launcher"

if [ -f "$APK_PATH" ]; then
    adb install "$APK_PATH"
    HOME_PKG="com.mikounchained.launcher"
    HOME_ACT=".MainActivity"
    ok "MikoUnchained APK installed"
else
    warn "MikoUnchained.apk not found at $APK_PATH"
    info "Downloading KISS Launcher as interim replacement..."
    KISS_URL="https://f-droid.org/repo/fr.neamar.kiss_186.apk"
    KISS_TMP="/tmp/kiss-launcher.apk"
    curl -sL -o "$KISS_TMP" "$KISS_URL" -H "Accept: application/vnd.android.package-archive"
    file "$KISS_TMP" | grep -q "Android" || fail "Failed to download KISS Launcher"
    adb install "$KISS_TMP"
    HOME_PKG="fr.neamar.kiss"
    HOME_ACT="/.MainActivity"
    ok "KISS Launcher installed"
fi

# Phase 4: Configure device
echo ""
info "Phase 4: Configuring device"

# Disable dead Miko apps
DEAD_APPS=(
    com.miko.mikoplus
    com.miko.launcher_app
    com.example.root.serviceexam
    com.miko.update_app
    com.miko.st
    com.miko.puzzle
    com.miko.atriptoameria
    com.miko.fightViruses
    com.miko.flagtrivia
    com.miko.tictactoe
    com.miko.dancedroove
    com.miko.storytime
    com.miko.animalsound
    com.miko.lingokids
    tv.kidoodle.android.miko
)

for pkg in "${DEAD_APPS[@]}"; do
    adb shell "pm disable $pkg" >/dev/null 2>&1 && echo "  disabled $pkg"
done
ok "Dead apps disabled"

# Set new launcher as HOME
adb shell "cmd package set-home-activity ${HOME_PKG}${HOME_ACT}" 2>/dev/null
adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME" >/dev/null 2>&1
ok "Launcher set as HOME"

# Enable navigation bar
adb shell "settings put global policy_control null"
adb shell "settings put global force_show_navbar 1"
adb shell "wm overscan 0,0,0,0"
ok "Navigation bar enabled"

# Write FTUE bypass
adb shell "mkdir -p /sdcard/klug/ftue"
adb shell "echo success > /sdcard/klug/ftue/ftueStatus.txt"
adb shell "echo '0>>>en' > /sdcard/klug/ftue/ftueLanguage.txt"
adb shell "echo 'en_US>>>' > /sdcard/klug/ftue/Miko3_locale.txt"
adb shell "echo en_US > /sdcard/klug/ftue/talentLocale.txt"
adb shell "touch /sdcard/klug/first_usage"
ok "FTUE bypass written"

# Prevent factory test on mikoplus re-enable
adb shell "mkdir -p /data/data/com.miko.mikoplus/shared_prefs"
adb shell "chown 10042:10042 /data/data/com.miko.mikoplus/shared_prefs"
adb shell "echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><string name=\"isAppOpenFirstTime\">false</string></map>' > /data/data/com.miko.mikoplus/shared_prefs/MIKO_PLUS.xml"
adb shell "chown 10042:10042 /data/data/com.miko.mikoplus/shared_prefs/MIKO_PLUS.xml"
ok "Factory test prevention set"

# Force stop any stragglers
for pkg in com.miko.mikoplus com.example.root.serviceexam com.miko.launcher_app; do
    adb shell "am force-stop $pkg" 2>/dev/null
done

echo ""
echo -e "${GREEN}╔═══════════════════════════════════════╗${NC}"
echo -e "${GREEN}║        Miko 3 unlocked!               ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════╝${NC}"
echo ""
info "Serial: $SERIAL"
info "ADB:    $(adb shell getprop persist.sys.usb.config 2>/dev/null | tr -d '\r')"
info "Root:   $(adb shell whoami 2>/dev/null | tr -d '\r')"
info "Free:   $(adb shell df -h /data 2>/dev/null | tail -1 | awk '{print $4}')"
echo ""
ok "Your Miko 3 is now a usable Android device."
ok "Connect to WiFi via Settings to get online."
echo ""
