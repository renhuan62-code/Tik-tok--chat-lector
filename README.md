# TikTok Chat Lector — revisión 0.3 alfa

Proyecto Android inicial para leer en voz alta comentarios de TikTok LIVE usando la voz de texto a voz de Android.

## Qué se mejoró en esta revisión
- Separación de la pantalla y un `ForegroundService` para que la lectura pueda continuar al cambiar de aplicación.
- Notificación persistente mientras el servicio intenta leer comentarios.
- Accesos a ajustes de batería y motor de voz.
- Cola de voz limitada por memoria (máximo 250 comentarios pendientes), sin límite diario programado. Si el teléfono no alcanza a leerlos todos, descarta los más antiguos para no acumular una cola infinita.
- Reconocimiento de varias ubicaciones posibles de la clase `TikTokLive` de la biblioteca comunitaria.

## Limitaciones que siguen abiertas
- La conexión usa `TikTokLiveJava`, una biblioteca comunitaria no oficial que depende del sistema interno de TikTok y puede romperse.
- No se ha confirmado que el nombre de clase reflejado coincida con el artefacto descargado por Gradle. Hace falta una compilación real y una prueba de conexión a un LIVE.
- No se ha probado en un emulador ni en un Samsung Galaxy A57.
- El servicio está implementado, pero la continuidad de audio/conexión en dispositivos reales aún necesita prueba de uso prolongado.
- No se incluye un APK precompilado.

## Compilar
Abre la carpeta en Android Studio reciente con JDK 17 y Android SDK Platform 35. También hay un workflow de GitHub Actions para compilar `assembleDebug` en la nube cuando el proyecto se suba a un repositorio.

## Privacidad
Solo se solicita el nombre público de usuario. No se pide contraseña, cookies ni códigos de acceso.

## Biblioteca
La documentación de TikTokLiveJava indica que permite escuchar eventos como comentarios, pero también aclara que no es una API oficial:
https://github.com/jwdeveloper/TikTokLiveJava


## Revisión 4 (24-10-2026)
- Añadido el paquete principal documentado `io.github.jwdeveloper.tiktok.TikTokLive` a la resolución de la biblioteca.
- Cambiada la cola TTS a `QUEUE_ADD` para no interrumpir la frase en curso con cada nuevo comentario.
- Añadido permiso y declaración del tipo de servicio en primer plano para Android reciente.
- Validaciones locales: manifiesto XML parseable, referencias al servicio/permiso presentes, paquete principal presente, y estructura ZIP comprobada.

**No se ha compilado con Android SDK/Gradle ni probado contra un LIVE real en este entorno.** La API de TikTokLiveJava es no oficial y podría requerir cambios adicionales tras una compilación real.
