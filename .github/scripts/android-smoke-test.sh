#!/bin/bash
# Installs the APK on the emulator, opens the start screen and a game, and
# fails if the app crashes. Screenshots go to android-screens/ and are also
# printed to the log as small base64 JPEGs.
set -u

APK=$(ls android/app/build/outputs/apk/release/*.apk | head -1)
PKG=com.snes9x.mobile
OUT=android-screens
mkdir -p "$OUT"
failed=0

shot() {
    adb exec-out screencap -p > "$OUT/$1.png"
    python3 - "$OUT/$1.png" "$1" <<'PY' || true
import base64, io, sys
from PIL import Image
img = Image.open(sys.argv[1]).convert("RGB")
img.thumbnail((360, 800))
buf = io.BytesIO()
img.save(buf, "JPEG", quality=55)
print("SCREENSHOT %s %s" % (sys.argv[2], base64.b64encode(buf.getvalue()).decode()))
PY
}

alive() {
    if ! adb shell pidof "$PKG" > /dev/null; then
        echo "::error::La app no está corriendo después de: $1"
        failed=1
    fi
}

adb logcat -c
adb install -r "$APK" || exit 1

# Start screen (empty library).
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
shot main
alive "abrir la pantalla de inicio"

# A tiny test ROM that turns the screen red, opened directly in the game
# screen. GameActivity isn't exported, so this needs a root shell.
python3 - <<'PY'
rom = bytearray(0x8000)
code = bytes([0x78, 0x18, 0xFB, 0x9C, 0x21, 0x21, 0xA9, 0x1F, 0x8D, 0x22, 0x21,
              0x9C, 0x22, 0x21, 0xA9, 0x0F, 0x8D, 0x00, 0x21, 0x80, 0xFE])
rom[0:len(code)] = code
h = 0x7FC0
rom[h:h + 21] = b"SMOKE TEST           "
rom[h + 0x15] = 0x20
rom[h + 0x17] = 0x05
rom[h + 0x19] = 0x01
rom[0x7FFC] = 0x00
rom[0x7FFD] = 0x80
rom[0x7FDC:0x7FE0] = bytes([0xFF, 0xFF, 0, 0])
ck = sum(rom) & 0xFFFF
rom[0x7FDE] = ck & 0xFF
rom[0x7FDF] = ck >> 8
rom[0x7FDC] = (ck ^ 0xFFFF) & 0xFF
rom[0x7FDD] = (ck ^ 0xFFFF) >> 8
open("smoke.sfc", "wb").write(rom)
PY
adb root > /dev/null
sleep 3
DIR=/data/data/$PKG/files
adb push smoke.sfc /data/local/tmp/smoke.sfc
adb shell "mkdir -p $DIR && cp /data/local/tmp/smoke.sfc $DIR/smoke.sfc && chown -R \$(stat -c %u /data/data/$PKG):\$(stat -c %g /data/data/$PKG) $DIR"
adb shell am start -W -n "$PKG/.GameActivity" -d "file://$DIR/smoke.sfc"
sleep 6
shot game
alive "abrir un juego"

# Pause menu.
adb shell input keyevent KEYCODE_BACK
sleep 2
shot menu
alive "abrir el menú de pausa"

# Rotate to landscape and back.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
adb shell input keyevent KEYCODE_BACK
sleep 2
shot game_landscape
alive "girar a horizontal"
adb shell settings put system user_rotation 0

echo "---- Errores en logcat ----"
adb logcat -d -b crash
adb logcat -d '*:E' | grep -iE "snes9x|AndroidRuntime|FATAL|DEBUG" | tail -80

if adb logcat -d -b crash | grep -q "$PKG"; then
    echo "::error::La app se cerró (ver el log de arriba)"
    failed=1
fi
exit $failed
