# Snes9x Mobile (Android)

App de Android para jugar juegos de **Super Nintendo** (con el núcleo de
Snes9x de este repositorio) y de **Game Boy Advance, Game Boy y Game Boy
Color** (con [mGBA](https://mgba.io), en `third_party/mgba`).

## Qué hace

- Abre juegos `.sfc`, `.smc`, `.gba`, `.gb`, `.gbc` (también dentro de un
  `.zip`) con el selector de archivos de Android, sin pedir permisos de
  almacenamiento. La consola se detecta sola y se indica en cada juego.
- Biblioteca con tarjetas de color, y un acceso directo a "Seguir jugando"
  con el último juego (mantén presionado un juego para quitarlo).
- Controles táctiles: cruceta, A/B/X/Y, L/R, Start y Select.
- Mandos Bluetooth/USB (Xbox, PlayStation, 8BitDo...) y teclado.
- Guardado del juego (SRAM) automático, como el cartucho original.
- Guardar y cargar partida en cualquier momento (estado), desde el menú.
- Funciona en vertical y horizontal.

En el juego, el botón de **pausa** (arriba), el botón **Atrás** o el botón
**Home** del control abren el menú: continuar, guardar o cargar partida,
mostrar u ocultar los controles táctiles, reiniciar y salir. Con un control
se navega con la cruceta, **A** elige y **B** cierra.

## Jugar con un control (por ejemplo EasySMX M15)

Cuando conectas un control, la app lo detecta sola y oculta los botones
táctiles. Si lo desconectas, vuelven a aparecer.

Con el **EasySMX M15** (se conecta por el puerto USB-C del celular):

1. Pon el control en **modo HID** (mantén **FN + A**; la luz queda amarilla)
   o en **modo Xbox** (mantén **FN + TURBO**; la luz queda morada). Evita el
   modo PS, porque en algunos celulares cambia el orden de los botones.
2. Abre el control, coloca el celular y ciérralo.
3. Abre un juego en la app.

Los botones funcionan por posición, igual que en el control de SNES:

| Control | SNES |
|---|---|
| Botón de abajo (A) | B |
| Botón de la derecha (B) | A |
| Botón de la izquierda (X) | Y |
| Botón de arriba (Y) | X |
| LB / LT | L |
| RB / RT | R |
| Cruceta o palanca izquierda | Cruceta |
| Start / Menú | Start |
| Select / View | Select |
| Home | Abre el menú de la app |

## Descargar el APK

La última versión, sin necesidad de cuenta de GitHub:

**https://github.com/ramirezmonsa-ui/snes9x/releases/latest**

Descarga `snes9x-mobile.apk`, ábrelo y permite "instalar apps de origen
desconocido" cuando Android lo pida.

Cada cambio en la app se compila en GitHub Actions, se prueba en un Android
virtual y, si pasa la prueba, se publica ahí solo.

## Compilar en tu computadora

(Primero trae mGBA, ver "Para compilar" más abajo.)

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
| `app/src/main/cpp/CMakeLists.txt` | Compila los dos emuladores (Snes9x y mGBA) y el puente. |
| `app/src/main/cpp/frontend.cpp` | Puente JNI: carga el emulador que toca y lo conecta con Java (video, audio, botones, partidas). |
| `Console.java` | Las consolas, qué emulador usa cada una y cómo reconocer sus juegos. |
| `NativeBridge.java` | Las funciones nativas vistas desde Java. |
| `EmulatorThread.java` | Hilo que corre el emulador, dibuja cada cuadro y reproduce el audio. |
| `GamepadView.java` | El control táctil en pantalla. |
| `GameActivity.java` | Pantalla de juego: menú, partidas guardadas y mandos físicos. |
| `MainActivity.java` | Pantalla de inicio: "Seguir jugando" y la biblioteca. |
| `ActionSheet.java` | Los paneles de opciones (menú de pausa, opciones de un juego). |
| `Ui.java` | Colores y estilos compartidos. |
| `RomLoader.java` | Lee la ROM (y la saca del `.zip` si hace falta). |

## Para compilar

mGBA es un submódulo de git. Después de clonar el repositorio:

```sh
git submodule update --init android/third_party/mgba
```

## Aviso

- Usa solo ROMs de juegos que tengas.
- Snes9x **no permite uso comercial** (ver `LICENSE` en la raíz): puedes
  compartir la app gratis, pero no venderla.
- mGBA usa la licencia MPL 2.0 (ver `third_party/mgba/LICENSE`).
- El APK se firma con la clave de depuración para poder instalarlo directo.
  Si algún día la publicas, crea tu propia clave de firma.
