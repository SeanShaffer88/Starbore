#!/usr/bin/env bash
# Builds a signed, installable APK without Gradle or the Android SDK manager.
# Needs: JDK 17+, aapt2, dalvik-exchange (dx), zipalign, apksigner
#   (Debian/Ubuntu: apt install aapt apksigner zipalign dalvik-exchange)
# and an API 34 android.jar (set ANDROID_JAR, or it is downloaded once).
set -euo pipefail
cd "$(dirname "$0")"

ROOT=$PWD
SRC=app/src/main
OUT=build
ANDROID_JAR=${ANDROID_JAR:-$OUT/android.jar}

mkdir -p "$OUT"
if [ ! -f "$ANDROID_JAR" ]; then
  echo "Fetching android.jar (API 34)..."
  curl -fsSL -o "$ANDROID_JAR" https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
fi

rm -rf "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT"/*.apk
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/5] Compiling resources"
aapt2 compile --dir "$SRC/res" -o "$OUT/res/res.zip"
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.starbore.game">|' \
  "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
aapt2 link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 21 --target-sdk-version 34 \
  --version-code 5 --version-name 1.4.1 "$OUT/res/res.zip"

echo "[2/5] Compiling Java"
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find "$SRC/java" "$OUT/gen" -name '*.java')

echo "[3/5] Dexing"
dalvik-exchange --dex --min-sdk-version=21 --output="$OUT/dex/classes.dex" "$OUT/classes"
(cd "$OUT/dex" && zip -q -j "$ROOT/$OUT/unsigned.apk" classes.dex)

echo "[4/5] Aligning"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[5/5] Signing"
KS=${KEYSTORE:-$ROOT/starbore.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass starbore -keypass starbore -alias starbore \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Starbore" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:starbore --key-pass pass:starbore --ks-key-alias starbore \
  --out "$OUT/Starbore.apk" "$OUT/aligned.apk"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk" "$OUT"/*.idsig
echo "Done: $OUT/Starbore.apk ($(du -h "$OUT/Starbore.apk" | cut -f1))"
