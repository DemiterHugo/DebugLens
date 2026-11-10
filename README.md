# DebugLens

Aplicación Android modular para explorar fragmentos de errores y trazas de excepciones.
Package y application ID: `io.github.demiterhugo.debuglens`.

## Módulos y dependencias

```text
app ───────► domain
 │             ▲
 └────► data ──┘
```

| Módulo | Responsabilidad | Dependencias de proyecto |
| --- | --- | --- |
| `app` | Compose, Material 3, presentación A2UI, ViewModels y composición del grafo de Hilt | `domain`, `data` |
| `domain` | Modelos de negocio, contratos de repositorio y casos de uso cuando sean necesarios | Ninguna |
| `data` | Implementaciones de repositorio y fuentes de datos | `domain` |

`domain` es Kotlin/JVM puro: no depende de Android, Compose ni Hilt.
`data` es una biblioteca Android para alojar las futuras fuentes de datos de la
plataforma. Los tipos expuestos a presentación se definirán en `domain`.
La dependencia de `app` sobre `data` permite ensamblar implementaciones en el
punto de composición; los ViewModels dependerán de contratos de `domain`.

`domain` implementa la validación y el análisis local de trazas, y declara el
contrato del historial. `data` proporciona un historial local con Room, conectado
desde `app/di/DiagnosticModule`. Las instrucciones específicas de cada módulo
están en [domain/README.md](domain/README.md) y [data/README.md](data/README.md).

## Flujo disponible

1. Introducir o pegar un fragmento, cargar el **Ejemplo** o usar **Importar Logcat**.
   La entrada admite hasta 65.536 unidades UTF-16 (el límite de `String.length`,
   contado antes de normalizar).
2. Pulsar **Analizar** para validar el texto y procesarlo en `Dispatchers.Default`.
3. Consultar una interfaz A2UI adaptada al patrón detectado, con la última
   excepción de la cadena principal, el mensaje y la ubicación disponibles.
   Marcar pasos de revisión y desplegar las causas y el fragmento original.
4. Reabrir o borrar análisis recientes desde el historial guardado. Al abrir un
   resultado, la pantalla desplaza su cabecera al área visible.

**Leer Logcat** reúne la captura y el análisis en una sola acción para fallos
de DebugLens registrados en el dispositivo.

La entrada normaliza CRLF y CR a LF y elimina el espacio exterior. Se rechazan
las entradas vacías y demasiado largas. Cambiar la entrada cancela la lectura
de Logcat o el análisis en curso y retira el resultado anterior.

El clasificador reconoce referencias nulas, estados y argumentos inválidos,
índices fuera de rango, conversiones de tipo, restricciones de seguridad,
`OutOfMemoryError` y marcadores explícitos de ANR. Los tipos no reconocidos
conservan su evidencia y se muestran como **Patrón no reconocido**.

Se procesa la primera cadena de excepciones, se siguen sus líneas `Caused by`
y se excluyen las ramas `Suppressed`. Se aceptan trazas simples y varios prefijos
habituales de texto copiado de Android Studio o Logcat. No reconstruye trazas ofuscadas ni
confirma por sí solo la causa de un ANR. La ubicación destacada prioriza marcos
con línea fuera de los prefijos habituales del framework; es una heurística.

El historial guarda las últimas 20 entradas, de más reciente a más antigua, en
una base de datos privada del dispositivo. Se conserva al cerrar el proceso y se
elimina al borrar el historial, los datos de la aplicación o la propia aplicación.
El borrador, el resultado abierto y las casillas de revisión se conservan al
recrear la Activity, pero no al terminar el proceso. Al volver a abrir la app se
pueden recuperar los análisis desde el historial. La app funciona sin conexión.

La pantalla distingue la carga del historial de un historial vacío y ofrece
reintentar su lectura. Si falla el guardado, el diagnóstico sigue disponible
durante esa sesión y se indica que no quedó guardado. El borrado conserva el
resultado que ya estaba abierto. Las bases de datos, incluidos los archivos
auxiliares de SQLite, se excluyen de las copias de seguridad y las transferencias
entre dispositivos mediante las reglas de Android.

En **Análisis recientes**, busca mensajes, tipos de excepción o archivos en el
fragmento completo de cada diagnóstico guardado. La búsqueda admite varios
términos e ignora mayúsculas y tildes. Puedes combinarla con una categoría.
En cada entrada, **Eliminar del historial** pide confirmación y retira solo
ese diagnóstico. Si el resultado sigue abierto, puedes consultarlo hasta salir
de él.
**Restablecer filtros** recupera la lista completa, desde la que puedes borrar
el historial.

Las orientaciones de la aplicación se apoyan en la documentación de Android sobre
[trazas](https://developer.android.com/studio/debug/stacktraces),
[ANR](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs) y
[volcados de memoria](https://developer.android.com/studio/profile/capture-heap-dump).

## Leer Logcat desde DebugLens

Pulsa **Leer Logcat** para buscar el último fallo Java de DebugLens que siga
disponible en el búfer `crash`. La app captura hasta 500 entradas recientes y
analiza el incidente en la misma acción. Muestra el diagnóstico en A2UI y lo
guarda en el historial. Si no encuentra un fallo, conserva el borrador y el
diagnóstico que estaban abiertos.

Si el historial ya contiene la misma traza normalizada, abre la sesión más
reciente que coincide. Durante la captura, sustituye los bytes inválidos por
el carácter de reemplazo y sigue buscando el incidente.

La captura es puntual, dura como máximo ocho segundos y se puede cancelar.
Android limita la lectura directa de una app a los registros de su propio UID.
Para analizar fallos de otras aplicaciones, del sistema o un ANR, usa un
volcado. [Restricciones de acceso a Logcat en Android](https://source.android.com/docs/core/tests/debug/understanding-logging#device-logs).

## Importar un volcado de Logcat

Desde PowerShell, exporta el búfer de fallos Java de un dispositivo con ADB y
copia el archivo al dispositivo donde está instalada DebugLens. La redirección
se ejecuta en `cmd.exe` para guardar los bytes de `adb exec-out` directamente,
sin que la página de códigos de PowerShell altere los mensajes:

```powershell
cmd.exe /d /c 'adb exec-out logcat -d -b crash -v threadtime > logcat.txt'
adb push logcat.txt /sdcard/Download/logcat.txt
```

Para exportar ANR, sustituye la primera orden por esta:

```powershell
cmd.exe /d /c 'adb exec-out logcat -d -b system -v threadtime ActivityManager:E *:S > logcat.txt'
```

En DebugLens, pulsa **Importar Logcat** y elige el archivo. La app carga el
último fallo Java o ANR según el orden del volcado. En un fallo Java de una app
o del sistema conserva las líneas de `AndroidRuntime` del mismo proceso e hilo. En un ANR
conserva el encabezado de `ActivityManager`. Revisa el fragmento y pulsa
**Analizar** para mostrar el diagnóstico A2UI y guardarlo en el historial.

La importación admite archivos de hasta 2 MiB en UTF-8 o UTF-16 con BOM y el
formato `threadtime`. Si el fallo no aparece en el volcado o el fragmento supera
el límite de la entrada, exporta un intervalo más corto o pega la traza relevante.
El [selector de Android](https://developer.android.com/training/data-storage/shared/documents-files)
concede acceso al archivo elegido. La lectura global de Logcat está
[restringida por Android](https://developer.android.com/reference/android/Manifest.permission#READ_LOGS).

## Diagnósticos con A2UI

La integración utiliza el [SDK oficial de AndroidX A2UI para Compose](https://developer.android.com/develop/ui/compose/agentic).
`DiagnosticSurfaceFactory` convierte el resultado del dominio en componentes
tipados del protocolo. `DiagnosticSurfaceController` envía la creación de la
superficie, el modelo inicial y las actualizaciones de componentes al procesador
del SDK. `DiagnosticSurfaceRenderer` observa la raíz y utiliza su enrutador Compose.
Todo este flujo pertenece a `app`; `domain` y `data` no dependen de A2UI.

El catálogo local `urn:debuglens:catalog:diagnostics:1` registra únicamente texto,
columnas, botones y casillas, representados con Material 3. No registra funciones
remotas, WebView ni recursos externos. Los errores pegados son valores de texto;
no se interpretan como documentos, acciones o expresiones A2UI.

Las acciones locales despliegan y ocultan las causas y el texto original. Las
casillas usan enlaces bidireccionales al modelo de datos del SDK, con una primera
comprobación específica del patrón detectado. Su estado se conserva durante las
actualizaciones y la recreación de la Activity. Un nuevo análisis, el cambio de
sesión, la reapertura de un resultado retirado o un reintento de la superficie
inician una revisión nueva.
Los eventos de superficies retiradas se ignoran.

La presentación limita cada texto de evidencia a 2.048 unidades UTF-16, la vista
del original a 8.192 y la cadena desplegada a sus últimas ocho excepciones; indica
los recortes de la cadena y del original. Estos límites no modifican el resultado
ni el fragmento completo guardados en el historial. La interfaz ofrece estados
de carga y error y permite reconstruir una superficie fallida sin repetir el análisis.
El procesador se cancela y libera sus superficies al finalizar el ViewModel.

## Ampliar un diagnóstico con IA local

1. Consigue un modelo de texto compatible en formato `.litertlm` y copia el
   archivo a Descargas del dispositivo. Se ha probado
   [Qwen3 0.6B INT4 sin razonamiento previo](https://huggingface.co/litert-community/Qwen3-0.6B-int4/blob/56621f0/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm)
   (347 MB) con LiteRT-LM 0.16.1. Para una prueba más ligera está
   [SmolLM2 135M Instruct](https://huggingface.co/litert-community/SmolLM2-135M-Instruct/tree/8111e0a)
   (143 MB), que puede responder en inglés y con menos detalle.
2. Importa un volcado de Logcat y pulsa **Analizar**. En el resultado, pulsa
   **Cargar modelo de IA** y elige el archivo. DebugLens admite un modelo a la
   vez, de hasta 1 GiB, y guarda su copia en almacenamiento privado excluido de
   las copias de seguridad. Antes de sustituirlo, DebugLens comprueba que
   LiteRT-LM puede abrir el nuevo modelo. Si falla, conserva el modelo anterior.
   Puedes cambiarlo o quitarlo desde el diagnóstico.
3. Pulsa **Ampliar con IA**. La hipótesis aparece en la superficie A2UI junto
   a la evidencia y a las comprobaciones del análisis local.

[LiteRT-LM](https://developers.google.com/edge/litert-lm/android) ejecuta el
modelo en la CPU del dispositivo en procesos compatibles arm64 o x86-64.
La generación usa los datos del análisis local y, si el analizador no reconoce
la excepción ni el ANR, un extracto acotado de la traza. La calidad, el idioma
y el tiempo de respuesta dependen del modelo.
La sugerencia se conserva mientras el diagnóstico sigue seleccionado. El
historial guarda el resultado local y permite volver a solicitar una sugerencia.

La app incorpora la respuesta como texto al catálogo A2UI. La traza, los datos
de diagnóstico y la inferencia permanecen en el dispositivo.

## Exportar un diagnóstico

Abre un diagnóstico nuevo o recupera uno del historial y pulsa **Exportar
diagnóstico**. El informe de texto UTF-8 incluye la clasificación, la evidencia
del fallo, la ubicación disponible y las comprobaciones recomendadas.

La traza completa y la sugerencia de IA se incluyen solo si marcas sus
opciones. La opción de IA aparece cuando el diagnóstico abierto tiene una
sugerencia disponible. Los mensajes de excepción y las ubicaciones forman
parte del informe, así que conviene revisar el archivo antes de adjuntarlo.

Pulsa **Compartir texto** para abrir el selector de Android con el informe y
elegir la aplicación de destino. Las mismas opciones controlan qué se incluye.

Pulsa **Elegir carpeta** en el selector de Android. DebugLens pide al proveedor
que cree un documento nuevo con el nombre sugerido `debuglens-diagnostico.txt`.
En Android 11 o superior, elige una carpeta compatible, como Documentos.
La raíz de Descargas queda fuera del selector de carpetas.

El archivo guarda una copia del resultado mostrado cuando confirmaste las
opciones. Mientras se guarda, puedes pulsar **Cancelar** y volver a exportar.
Si falla o se cancela, DebugLens intenta eliminar el documento incompleto
que creó para esa exportación.

## Presentación e inyección

- `DebugLensApplication` inicia Hilt mediante `@HiltAndroidApp`.
- `MainActivity` es un `@AndroidEntryPoint` y aloja el tema y la ruta inicial.
- `ui/home/HomeRoute` obtiene el ViewModel con `hiltViewModel()` y observa su
  `StateFlow` con `collectAsStateWithLifecycle()`.
- `HomeViewModel` conserva el estado inmutable y recibe eventos explícitos de UI.
- `DiagnosticSurfaceViewModel` conserva el procesador y la superficie A2UI de la
  sesión seleccionada, con un ciclo de vida independiente de la composición.
- `HomeScreen` recibe estado y callbacks; se puede previsualizar sin Hilt.

Cada nueva pantalla tendrá su propio estado y ViewModel. Las operaciones
asíncronas de presentación usarán `viewModelScope`; los contratos reactivos
usarán Flow cuando exista una fuente observable. Los casos de uso se añadirán
para lógica de negocio con responsabilidad propia. Evitar clases base y
abstracciones que no resuelvan una necesidad concreta.

El ViewModel depende de `AnalyzeDiagnosticUseCase` y del contrato
`DiagnosticHistoryRepository`, y recibe el dispatcher de análisis por inyección.
La creación del repositorio concreto se limita a `app/di`. `domain` permanece
libre de Android y Hilt; exporta Coroutines Core porque Flow forma parte del
contrato público. `data` no necesita procesadores de inyección.

`DiagnosticHistoryStorage` encapsula Room dentro de `data` y se proporciona como
singleton de Hilt. Su API pública expone el repositorio y el cierre del recurso,
manteniendo los tipos de Room y sus entidades dentro del módulo. El guardado de
la sesión, sus causas y la retención de las 20 más recientes son una transacción.
El borrado y la retención eliminan las causas asociadas mediante claves foráneas.
La secuencia de inserción establece el orden sin depender del reloj del dispositivo.

## Herramientas

Se mantienen AGP 9.5.0, Gradle Wrapper 9.6.0, Kotlin 2.2.10 y el BOM de Compose
2026.02.01. Hilt usa KSP para la generación de código, siguiendo su
[configuración oficial](https://dagger.dev/hilt/gradle-setup.html) y la
[guía de Dagger con KSP](https://dagger.dev/dev-guide/ksp.html).
Las versiones de plugins y
bibliotecas están centralizadas en `gradle/libs.versions.toml`.

A2UI usa `1.0.0-alpha02` (protocolo 0.9.1), una versión preliminar del SDK.
Sus dependencias requieren Compose UI y Runtime `1.13.0-alpha03` y prevalecen
sobre las versiones de esos artefactos en el BOM. Se mantiene Material 3 del BOM
existente mediante el catálogo propio, sin añadir el catálogo Material 3 opcional.
Consultar las [notas oficiales de A2UI Compose](https://developer.android.com/jetpack/androidx/releases/a2ui-compose)
antes de actualizar estas dependencias.

La persistencia utiliza [Room 3.0.3](https://developer.android.com/jetpack/androidx/releases/room3)
y el controlador `AndroidSQLiteDriver` de SQLite 2.7.1. El runtime, el compilador
KSP y el controlador pertenecen a `data`; no se añaden artefactos de RxJava,
Paging, KTX ni una copia nativa de SQLite. El plugin de Room exporta el esquema
inicial en `data/schemas`, que se versiona para preparar futuras migraciones.
No se configura un borrado automático de los datos ante un cambio de esquema.

Los módulos Android usan el Kotlin integrado de AGP 9. `domain` aplica
`org.jetbrains.kotlin.jvm`. El bytecode Java/Kotlin apunta a JVM 17 en los tres
módulos. El daemon de Gradle conserva el criterio JDK 25 existente en
`gradle/gradle-daemon-jvm.properties`.

SDK mínimo: 24. SDK de compilación de `app`: 37.1, requerido por A2UI.
SDK de destino: 37. `data` conserva su SDK de compilación 37.
Configurar el SDK local en `local.properties`, que está excluido de Git.

## Verificación

Desde Windows, con un JDK compatible disponible para el wrapper:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest test lint --max-workers=1 --console=plain
.\gradlew.bat :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --max-workers=1 --console=plain
```

La segunda orden necesita un dispositivo o emulador conectado. Las pruebas
unitarias cubren normalización, límites, cadenas de causas, ramas suprimidas,
formatos de entrada, clasificación, mapeo del historial y su coordinación
desde el ViewModel, incluyendo cancelación y reintentos después de un fallo.
También cubren la selección de incidentes de Logcat y la coordinación de la
importación y la captura directa con el borrador, el análisis y el guardado.
Las pruebas de coroutines usan `kotlinx-coroutines-test` y un fake del contrato.
También se comprueban los componentes generados, los límites de presentación,
las acciones permitidas, el reemplazo de superficies, los eventos obsoletos y la
recuperación del procesador A2UI mediante un fake de su interfaz oficial.

`MainActivityTest` comprueba el arranque real con Hilt, validación, análisis de
texto, conservación del estado al recrear la Activity, recuperación y borrado del
historial, el caso de un patrón desconocido y el ejemplo incorporado. Las pruebas
A2UI comprueban las acciones, los enlaces de las casillas y su ciclo de vida.
`DiagnosticA2uiRecoveryTest` verifica con el SDK real el rechazo de un componente
no registrado y la reconstrucción correcta al reintentar.

Las pruebas Android de `data` usan Room y el controlador del sistema para comprobar
persistencia al cerrar y reabrir la base, borrado duradero, retención, eliminación
de causas huérfanas, rollback, emisiones de Flow e inserciones concurrentes.
Las pruebas de captura comprueban la lectura del subproceso, el fallo del
comando y su terminación al cancelar.
