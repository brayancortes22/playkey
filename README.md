# PlayKey

App Android personal para controlar Spotify con las teclas físicas de volumen, incluso con la pantalla bloqueada cuando el fabricante permite el filtro global de teclas.

## Cómo funciona

- **Pantalla encendida:** Utiliza `AccessibilityService` con `FLAG_REQUEST_FILTER_KEY_EVENTS` para calcular el tiempo de presión (umbral configurable de 350 a 1150 ms) y permitir pulsaciones cortas de volumen normal.
- **Pantalla apagada (Modo Bolsillo):** En Android 14+ los eventos de accesibilidad se pausan con la pantalla apagada. PlayKey implementa `MediaSessionManager.setOnVolumeKeyLongPressListener` mediante reflexión y Dynamic Proxy. Al mantener presionado volumen arriba o abajo con la pantalla bloqueada, el sistema despacha el evento directamente a PlayKey, el cual emite `skipToNext()` o `skipToPrevious()` sobre Spotify y vibra 50 ms como confirmación háptica.
- Mantener volumen arriba: canción siguiente.
- Mantener volumen abajo: canción anterior.
- Pulsar brevemente: sube o baja el volumen multimedia como de costumbre.
- Puedes invertir las direcciones y ajustar el umbral de pulsación.
- Incluye iconos adaptativos de alta resolución y banner temático en modo oscuro.

## Permisos requeridos

1. **Acceso multimedia (`NotificationListenerService`):** Permite a PlayKey descubrir la sesión activa de Spotify y enviar comandos de transporte.
2. **Servicio de accesibilidad:** Intercepta pulsaciones cuando la pantalla está encendida.
3. **Control con pantalla apagada (`SET_VOLUME_KEY_LONG_PRESS_LISTENER`):** Permiso de desarrollo/sistema concedido vía ADB:
   ```bash
   adb shell pm grant com.bscl.spotifyvolumekeys android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER
   ```
4. **Exención de ahorro de batería (Doze Whitelist):**
   ```bash
   adb shell dumpsys deviceidle whitelist +com.bscl.spotifyvolumekeys
   ```

## Compilar e instalar

1. Abre `spotify-volume-keys` en Android Studio o compila desde terminal:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
2. Instala en el dispositivo:
   ```bash
   adb install -r "app/build/outputs/apk/debug/app-debug.apk"
   adb shell pm grant com.bscl.spotifyvolumekeys android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER
   adb shell dumpsys deviceidle whitelist +com.bscl.spotifyvolumekeys
   ```
3. Inicia la app y reproduce tu música favorita en Spotify. ¡Disfruta saltando canciones directamente desde tu bolsillo!
