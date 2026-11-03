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

1. Introducir o pegar un fragmento, o cargar el **Ejemplo** incorporado, hasta
   65.536 unidades UTF-16 (el límite de
   `String.length`, contado antes de normalizar).
2. Pulsar **Analizar** para validar el texto y procesarlo en `Dispatchers.Default`.
3. Consultar una interfaz A2UI adaptada al patrón detectado, con la última
   excepción de la cadena principal, el mensaje y la ubicación disponibles.
   Marcar pasos de revisión y desplegar las causas y el fragmento original.
4. Reabrir o borrar análisis recientes desde el historial guardado. Al abrir un
   resultado, la pantalla desplaza su cabecera al área visible.

La entrada normaliza CRLF y CR a LF y elimina el espacio exterior. Se rechazan
las entradas vacías y demasiado largas. Cambiar la entrada cancela el análisis
en curso y retira el resultado anterior.

El clasificador reconoce referencias nulas, estados y argumentos inválidos,
índices fuera de rango, conversiones de tipo, restricciones de seguridad,
`OutOfMemoryError` y marcadores explícitos de ANR. Los tipos no reconocidos
conservan su evidencia y se muestran como **Patrón no reconocido**.

Se procesa la primera cadena de excepciones, se siguen sus líneas `Caused by`
y se excluyen las ramas `Suppressed`. Se aceptan trazas simples y varios prefijos
habituales de texto copiado de Android Studio o Logcat. Esto procesa texto pegado
manualmente; la aplicación no lee Logcat. No reconstruye trazas ofuscadas ni
confirma por sí solo la causa de un ANR. La ubicación destacada prioriza marcos
con línea fuera de los prefijos habituales del framework; es una heurística.

El historial guarda las últimas 20 entradas, de más reciente a más antigua, en
una base de datos privada del dispositivo. Se conserva al cerrar el proceso y se
elimina al borrar el historial, los datos de la aplicación o la propia aplicación.
El borrador, el resultado abierto y las casillas de revisión se conservan al
recrear la Activity, pero no al terminar el proceso. Al volver a abrir la app se
pueden recuperar los análisis desde el historial. No hay acceso a la red ni IA.

La pantalla distingue la carga del historial de un historial vacío y ofrece
reintentar su lectura. Si falla el guardado, el diagnóstico sigue disponible
durante esa sesión y se indica que no quedó guardado. El borrado conserva el
resultado que ya estaba abierto. Las bases de datos, incluidos los archivos
auxiliares de SQLite, se excluyen de las copias de seguridad y las transferencias
entre dispositivos mediante las reglas de Android.

Las orientaciones de la aplicación se apoyan en la documentación de Android sobre
[trazas](https://developer.android.com/studio/debug/stacktraces),
[ANR](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs) y
[volcados de memoria](https://developer.android.com/studio/profile/capture-heap-dump).

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

## Alcance de esta base

Integración con IA, lectura automática de Logcat y red quedan
para las siguientes funcionalidades. La aplicación no solicita permisos de
diagnóstico ni añade clientes de red. El historial se conserva localmente con Room.
