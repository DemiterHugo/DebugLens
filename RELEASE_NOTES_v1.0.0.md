# DebugLens v1.0.0

Primera release pública de DebugLens, una aplicación Android que analiza trazas
localmente y mantiene los datos en el dispositivo.

## Incluye

- Importación de volcados de Logcat y captura puntual del Logcat de DebugLens.
- Clasificación local de excepciones y ANR con ubicación y evidencia.
- Diagnósticos interactivos con A2UI y comprobaciones persistentes.
- Historial local con Room, búsqueda, filtros y borrado individual confirmado.
- Persistencia del historial al cerrar y volver a abrir la aplicación.
- Exportación a archivo y uso compartido como texto.
- IA local opcional mediante LiteRT-LM, sin enviar trazas a un servidor.
- CI de GitHub Actions para compilación, pruebas unitarias y lint.

## Verificación

- Build debug y release correctos.
- 97 pruebas unitarias correctas.
- 40 pruebas instrumentadas correctas en un Galaxy S25 Ultra con API 37.
- `lintDebug` correcto.

## Consideraciones

- La interfaz A2UI usa una versión preliminar del SDK.
- La IA local es opcional y requiere importar un modelo compatible.
- Para instalar el APK distribuido, debe utilizarse un artefacto firmado.
