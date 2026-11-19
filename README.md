# Nextel App

Aplicación Android Push-to-Talk (PTT), inspirada en el uso de radios: mantener presionado para transmitir y soltar para dejar de hablar.

## Primera etapa: conexión LAN

- Android nativo con Kotlin y Jetpack Compose.
- Interfaz oscura con acentos azul eléctrico y rojo.
- Crear sala en un teléfono y conectar el segundo mediante la IP local.
- Transporte de voz PCM mono a 16 kHz mediante TCP en la red Wi-Fi local.
- Botón PTT de mantener presionado.
- Servicio en primer plano para mantener el ciclo de vida de la conexión.
- Navegación visual inicial para Inicio, Grupos, Canales y Perfil.

## Estado de desarrollo

La conexión LAN y el flujo básico de captura/envío y reproducción de audio están implementados en el código. La compilación automática está en proceso de corrección y todavía falta verificar el APK y probar el audio entre dos teléfonos reales.

Esta primera versión está limitada a una conexión directa entre dos dispositivos en la misma red local. No utiliza Firebase ni un servidor externo. Los grupos y canales múltiples quedan para una etapa posterior.

## Identidad visual

El logo/ícono oficial aprobado por el propietario debe conservarse exactamente como fue generado: fondo negro y azul eléctrico, radios laterales, micrófono central, texto “Nextel App” y franja roja “Push-to-Talk”. No redibujarlo ni modificar colores, formas o texto sin autorización explícita.

## Requisitos

- Android Studio reciente
- JDK 17
- Android SDK 35
