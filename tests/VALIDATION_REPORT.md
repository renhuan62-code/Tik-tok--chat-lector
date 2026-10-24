# Revisión 0.3 alfa — informe honesto de pruebas

Fecha: 2026-10-24

## Pruebas locales ejecutadas
1. Compilación de los archivos Java con `javac -Xlint:all -source 17 -target 17` contra stubs mínimos de Android. Resultado: PASS (solo advertencia de configuración de módulos de javac).
2. Smoke test con una biblioteca falsa de TikTok: el servicio descubrió la clase por reflexión, llamó `onConnected` y recibió un evento de comentario. Resultado: PASS en simulación.
3. Parseo de `AndroidManifest.xml`. Resultado: PASS.
4. Comprobación de que el manifiesto declara la Activity, el servicio de primer plano, tipo `mediaPlayback`, permisos y que existe el workflow de compilación. Resultado: PASS.
5. Inspección del flujo de inicio/detención, cola de voz y acceso a ajustes. Resultado: revisión estática; no es una prueba de ejecución real.

## Lo que no se pudo probar
- No se generó un APK: no hay Android SDK ni Gradle instalados en este entorno.
- No se descargó ni enlazó el artefacto real de TikTokLiveJava.
- No hay emulador Android disponible en este entorno.
- No se conectó a un LIVE real ni se probó en el Samsung Galaxy A57.
- El motor TTS real, la estabilidad durante varias horas y el comportamiento de ahorro de batería no se han probado en dispositivo.

## Riesgos abiertos
- La conexión usa reflexión y busca varias ubicaciones posibles de `TikTokLive`. Hace falta comprobar la clase real del artefacto descargado.
- TikTokLiveJava es una biblioteca comunitaria de ingeniería inversa, no una API oficial; puede romperse si TikTok cambia el protocolo.
- La cola mantiene como máximo 250 comentarios pendientes. No hay límite diario, pero si llegan más comentarios de los que el teléfono puede leer, se eliminan los más antiguos para evitar consumo de memoria sin límite.
- La notificación de primer plano y el servicio están implementados, pero la continuidad en segundo plano debe probarse en un teléfono real.

## Conclusión
Es una versión de desarrollo más completa, no una aplicación lista para producción. No anunciar como probado en Samsung ni como conectado a TikTok hasta superar una compilación real y una prueba en LIVE.
