# OwnBridge

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Türkçe](README.tr.md) · [فارسی](README.fa.md)

**¿Tienes un VPS? Monta tu propia VPN privada.**

OwnBridge es una herramienta de despliegue local para Android. Solo necesitas un VPS Ubuntu con una dirección IPv4 pública: introduce los datos de acceso, elige el protocolo y toca «Iniciar despliegue». OwnBridge se encarga de configurar el servidor, comprobar los nodos y generar la configuración.

Al terminar, puedes solicitar una URL de suscripción para **Clash/Mihomo** y obtener enlaces de nodos listos para importar en **v2rayN/v2rayNG**. Sin comandos en el servidor ni archivos de configuración escritos a mano.

**Tu servidor. Tus nodos. Del despliegue a la entrega en unos pocos toques.**

**[⬇ Descargar APK para Android (arm64-v8a)](https://github.com/cupp-debug/OwnBridge/releases/download/v0.1.0/OwnBridge-v0.1.0-arm64.apk)**

## Estado actual

v0.1.0 es la primera versión de Android que no es preliminar. OwnBridge no es un proveedor de VPN ni ofrece servidores o cuentas. El APK sin depuración admite el despliegue combinado de SS, VLESS Reality e Hysteria 2. No se han validado todas las combinaciones de VPS, red y cliente; puedes informar de problemas en GitHub Issues. El APK se distribuye como archivo adjunto de GitHub Release, no dentro del repositorio.

## Funciones

- Inspecciona por SSH, en modo de solo lectura, el sistema, las direcciones, los puertos, los servicios y el cortafuegos del VPS.
- Fija la clave de host SSH, verifica con SHA-256 un paquete de despliegue versionado y registra el progreso remoto para permitir reintentos seguros.
- Genera YAML para Clash/Mihomo y enlaces para compartir con v2rayN/v2rayNG a partir de resultados verificados. También permite solicitar manualmente un enlace de suscripción de Clash.
- Prueba cada nodo mediante una petición HTTP de Mihomo desde el dispositivo Android. No es un ping ICMP ni inicia una VPN de Android.
- Guarda los resultados verificados y los registros en un historial local cifrado. No almacena la contraseña root del VPS.

## Requisitos y compilación

- Android 8.0 o posterior con compatibilidad `arm64-v8a`.
- Un VPS Ubuntu 22.04, 24.04 o 26.04 x86_64 que estés autorizado a administrar y al que puedas acceder mediante SSH como root. Las combinaciones admitidas dependen de las restricciones de la versión actual.
- JDK 21 y Android SDK API 36 para compilar.

Para desarrollo local, ejecuta `./gradlew.bat :app:assembleDebug` en Windows o `./gradlew :app:assembleDebug` en macOS/Linux. El APK público se compila aparte sin depuración y se firma con una clave de publicación; consulta la [guía de compilación y firma](BUILDING.md). La compilación descarga un puente Android de Mihomo de versión fija y comprueba su SHA-256. El despliegue en el VPS descarga una versión fija de sing-box del proyecto original y verifica su resumen criptográfico.

## Seguridad

Introduce únicamente credenciales de un VPS propio o que tengas permiso para administrar. La clave de host SSH se acepta en el primer uso; si cambia, la conexión se detiene hasta que la revises expresamente. Los archivos de configuración y enlaces de suscripción contienen credenciales del proxy. El enlace de suscripción alojado en el VPS usa actualmente HTTP, no HTTPS: quien obtenga el enlace o pueda observar el tráfico podría leer esas credenciales. Trata el enlace como un secreto y cambia las credenciales afectadas si se divulga.

## Licencia y proyectos externos

El código fuente de OwnBridge se publica bajo [GPL-3.0-only](../LICENSE). Consulta los [avisos de terceros](../THIRD_PARTY_NOTICES.md) para conocer las licencias y fuentes de Mihomo, sing-box y los iconos. OwnBridge es independiente de esos proyectos.
