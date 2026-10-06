# Snes9x Mobile (Android)

App de Android que usa el núcleo de Snes9x de este repositorio para jugar
juegos de Super Nintendo en el celular.

## Qué hace

- Abre ROMs `.sfc`, `.smc`, `.swc`, `.fig` y `.zip` con el selector de
  archivos de Android (no pide permisos de almacenamiento).
- Lista de juegos recientes (mantén presionado un juego para quitarlo).
- Controles táctiles: cruceta, A/B/X/Y, L/R, Start y Select.
- Mandos Bluetooth/USB (Xbox, PlayStation, 8BitDo...) y teclado.
- Guardado del juego (SRAM) automático, como el cartucho original.
- Guardar y cargar partida en cualquier momento (estado), desde el menú.
- Funciona en vertical y horizontal.

En el juego, el botón **Atrás** abre el menú (continuar, guardar o cargar
partida, reiniciar, ocultar los controles y salir).

## Descargar el APK

Cada push que cambie la app la compila automáticamente en GitHub Actions:

1. En GitHub, ve a la pestaña **Actions** del repositorio y abre la última
   ejecución de **Android APK** que esté en verde.
2. Abajo, en **Artifacts**, descarga `snes9x-mobile-apk` (es un `.zip` con el
   APK dentro).
3. Pasa el APK al celular, ábrelo y permite "instalar apps de origen
   desconocido" cuando Android lo pida.

## Compilar en tu computadora

Necesitas Android Studio (que trae el SDK, el NDK y CMake). Abre la carpeta
`android/` como proyecto y dale a *Run*, o desde la terminal:

```sh
cd android
gradle assembleRelease
```

El APK queda en `android/app/build/outputs/apk/release/`.

## Cómo está hecha

| Archivo | Qué hace |
|---|---|
| `app/src/main/cpp/CMakeLists.txt` | Compila el núcleo de Snes9x (`libretro/`) junto con el puente. |
| `app/src/main/cpp/snes_bridge.cpp` | Puente JNI: conecta el núcleo libretro con Java (video, audio, botones, partidas). |
| `NativeBridge.java` | Las funciones nativas vistas desde Java. |
| `EmulatorThread.java` | Hilo que corre el emulador, dibuja cada cuadro y reproduce el audio. |
| `GamepadView.java` | El control táctil en pantalla. |
| `GameActivity.java` | Pantalla de juego: menú, partidas guardadas y mandos físicos. |
| `MainActivity.java` | Pantalla de inicio con la lista de juegos. |
| `RomLoader.java` | Lee la ROM (y la saca del `.zip` si hace falta). |

## Aviso

- Usa solo ROMs de juegos que tengas.
- Snes9x **no permite uso comercial** (ver `LICENSE` en la raíz): puedes
  compartir la app gratis, pero no venderla.
- El APK se firma con la clave de depuración para poder instalarlo directo.
  Si algún día la publicas, crea tu propia clave de firma.
