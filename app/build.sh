#!/bin/bash
# Build + sign com.mikounchained.launcher WITHOUT gradle:
#   neuterd embed -> aapt2 compile/link (res + R.java) -> javac -> d8 -> add dex -> zipalign -> apksigner
# Produces ./mikounchained-debug.apk. Deterministic; only android.jar as a dependency (no androidx).
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-/home/lumen/android-sdk}"
BT="$SDK/build-tools/35.0.0"
AJAR="$SDK/platforms/android-28/android.jar"
JAVAC="$(command -v javac)"
KEYTOOL="$(command -v keytool)"

DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$DIR/build"
KS="$DIR/mikounchained-debug.keystore"
APK="$DIR/mikounchained-debug.apk"

[ -f "$AJAR" ] || { echo "!! android.jar missing at $AJAR — run the provisioner"; exit 1; }
for t in aapt2 d8 zipalign apksigner; do [ -x "$BT/$t" ] || { echo "!! $t missing in $BT"; exit 1; }; done
rm -rf "$OUT"; mkdir -p "$OUT/obj" "$OUT/gen"

echo "== 0/7 embed neuterd (arm64 self-healing global-ns reboot neuter) =="
[ -f "$DIR/native/neuterd" ] || bash "$DIR/native/build-neuterd.sh"
SRC="$OUT/src"; mkdir -p "$SRC"; cp -R "$DIR/src/." "$SRC/"
B64="$(base64 -w0 < "$DIR/native/neuterd")"
NEUTER="$SRC/com/mikounchained/launcher/Neuter.java"
python3 - "$NEUTER" "$B64" <<'PY'
import sys
path, b64 = sys.argv[1], sys.argv[2]
s = open(path).read().replace("@@NEUTERD_B64@@", b64)
open(path, "w").write(s)
PY
grep -q '@@NEUTERD_B64@@' "$NEUTER" && { echo "!! base64 injection failed"; exit 1; }
echo "   embedded native/neuterd ($(wc -c < "$DIR/native/neuterd") bytes -> ${#B64} b64 chars)"

echo "== 1/7 aapt2 compile resources =="
"$BT/aapt2" compile --dir "$DIR/res" -o "$OUT/res.zip"

echo "== 2/7 aapt2 link (manifest + res -> R.java + unsigned.apk) =="
"$BT/aapt2" link -I "$AJAR" \
  --manifest "$DIR/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 28 --target-sdk-version 28 \
  -o "$OUT/unsigned.apk" "$OUT/res.zip"

echo "== 3/7 javac =="
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -bootclasspath "$AJAR" -classpath "$AJAR" -d "$OUT/obj" \
  $(find "$SRC" "$OUT/gen" -name '*.java')

echo "== 4/7 d8 (dex) =="
"$BT/d8" --min-api 28 --lib "$AJAR" --output "$OUT" $(find "$OUT/obj" -name '*.class')
[ -f "$OUT/classes.dex" ] || { echo "!! d8 produced no classes.dex"; exit 1; }

echo "== 5/7 add classes.dex to apk =="
# Pin the dex entry to the 1980 zip epoch (matches aapt2's other entries) and strip extra fields,
# so two builds from the same sources + keystore are byte-identical.
( cd "$OUT" && cp unsigned.apk withdex.apk && TZ=UTC touch -t 198001010000.00 classes.dex && zip -X -jq withdex.apk classes.dex )

echo "== 6/7 zipalign =="
"$BT/zipalign" -f -p 4 "$OUT/withdex.apk" "$OUT/aligned.apk"

echo "== 7/7 sign =="
if [ ! -f "$KS" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KS" -alias mikounchained -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass mikounchained -keypass mikounchained -dname "CN=MikoUnchained Debug" >/dev/null 2>&1
  echo "   generated $KS (debug key)"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:mikounchained --key-pass pass:mikounchained \
  --min-sdk-version 28 --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$APK" | head -2

echo
echo "BUILT: $APK ($(wc -c < "$APK") bytes)"
