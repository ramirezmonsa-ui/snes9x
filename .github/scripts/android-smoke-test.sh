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

# A tiny Game Boy Advance ROM that also turns the screen red, to check the
# switch to the mGBA core.
python3 - <<'PY'
import struct
rom = bytearray(0x200)
rom[0:4] = struct.pack("<I", 0xEA00002E)          # b 0x080000C0
rom[0xA0:0xAC] = b"SMOKETEST\0\0\0"
rom[0xB2] = 0x96
code = [0xE3A00301, 0xE3A01B01, 0xE2811003, 0xE1C010B0,  # DISPCNT = mode 3, BG2
        0xE3A00406, 0xE3A0101F, 0xE1811801, 0xE3A02C4B,  # fill VRAM with red
        0xE4801004, 0xE2522001, 0x1AFFFFFC, 0xEAFFFFFE]
for i, word in enumerate(code):
    rom[0xC0 + 4 * i:0xC4 + 4 * i] = struct.pack("<I", word)
open("smoke.gba", "wb").write(rom)
PY
adb push smoke.gba /data/local/tmp/smoke.gba
adb shell "cp /data/local/tmp/smoke.gba $DIR/smoke.gba && chown \$(stat -c %u /data/data/$PKG):\$(stat -c %g /data/data/$PKG) $DIR/smoke.gba"
adb shell am start -W -n "$PKG/.GameActivity" -d "file://$DIR/smoke.gba"
sleep 6
shot gba
alive "abrir un juego de GBA"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1

# Back to the SNES ROM, to check switching cores again. It was left
# mid-game, so it should offer to continue from the automatic save.
adb shell am start -W -n "$PKG/.GameActivity" -d "file://$DIR/smoke.sfc"
sleep 5
shot continue
alive "volver a un juego de SNES"
adb shell input keyevent KEYCODE_BACK
sleep 2

# Fast-forward (R2 turns it on and off) and rewind (hold L2).
adb shell input keyevent KEYCODE_BUTTON_R2
sleep 2
shot fast_forward
adb shell input keyevent KEYCODE_BUTTON_R2
adb shell input keyevent --longpress KEYCODE_BUTTON_L2
sleep 1
alive "avance rápido y rebobinar"

# Pause menu, then the save slots (second item).
adb shell input keyevent KEYCODE_BACK
sleep 2
shot menu
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_ENTER
sleep 2
shot slots
alive "abrir las partidas guardadas"
adb shell input keyevent KEYCODE_BACK
sleep 1

# Controller setup, the fifth item of the pause menu.
adb shell input keyevent KEYCODE_BACK
sleep 2
for i in 1 2 3 4; do adb shell input keyevent KEYCODE_DPAD_DOWN; done
adb shell input keyevent KEYCODE_ENTER
sleep 2
shot controls
alive "abrir la configuración del control"
adb shell input keyevent KEYCODE_BACK
sleep 1

# Rotate to landscape and back.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
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
