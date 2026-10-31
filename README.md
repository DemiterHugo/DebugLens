# DebugLens

Base Android modular para desarrollar experiencias de diagnóstico interactivas.
Package y application ID: `io.github.demiterhugo.debuglens`.

## Módulos y dependencias

```text
app ───────► domain
 │             ▲
 └────► data ──┘
```

| Módulo | Responsabilidad | Dependencias de proyecto |
| --- | --- | --- |
| `app` | Compose, Material 3, ViewModels y composición del grafo de Hilt | `domain`, `data` |
| `domain` | Modelos de negocio, contratos de repositorio y casos de uso cuando sean necesarios | Ninguna |
| `data` | Implementaciones de repositorio y fuentes de datos | `domain` |

`domain` es Kotlin/JVM puro: no depende de Android, Compose ni Hilt.
`data` es una biblioteca Android para alojar las futuras fuentes de datos de la
plataforma. Los tipos expuestos a presentación se definirán en `domain`.
La dependencia de `app` sobre `data` permite ensamblar implementaciones en el
punto de composición; los ViewModels dependerán de contratos de `domain`.

Los módulos `domain` y `data` quedan preparados, sin lógica de diagnóstico todavía.
Las instrucciones específicas de cada módulo están en [domain/README.md](domain/README.md)
y [data/README.md](data/README.md).

## Presentación e inyección

- `DebugLensApplication` inicia Hilt mediante `@HiltAndroidApp`.
- `MainActivity` es un `@AndroidEntryPoint` y aloja el tema y la ruta inicial.
- `ui/home/HomeRoute` obtiene el ViewModel con `hiltViewModel()` y observa su
  `StateFlow` con `collectAsStateWithLifecycle()`.
- `HomeViewModel` conserva el estado inmutable y recibe eventos explícitos de UI.
- `HomeScreen` recibe estado y callbacks; se puede previsualizar sin Hilt.

Cada nueva pantalla tendrá su propio estado y ViewModel. Las operaciones
asíncronas de presentación usarán `viewModelScope`; los contratos reactivos
usarán Flow cuando exista una fuente observable. Los casos de uso se añadirán
para lógica de negocio con responsabilidad propia. Evitar clases base y
abstracciones que no resuelvan una necesidad concreta.

La pantalla inicial muestra el estado vacío y un diálogo informativo. El diálogo
se conserva durante los cambios de configuración mediante el ViewModel; su estado
es transitorio y no se restaura después de la muerte del proceso.

## Herramientas

Se mantienen AGP 9.5.0, Gradle Wrapper 9.6.0, Kotlin 2.2.10 y el BOM de Compose
2026.02.01. Hilt usa KSP para la generación de código, siguiendo su
[configuración oficial](https://dagger.dev/hilt/gradle-setup.html) y la
[guía de Dagger con KSP](https://dagger.dev/dev-guide/ksp.html).
Las versiones de plugins y
bibliotecas están centralizadas en `gradle/libs.versions.toml`.

Los módulos Android usan el Kotlin integrado de AGP 9. `domain` aplica
`org.jetbrains.kotlin.jvm`. El bytecode Java/Kotlin apunta a JVM 17 en los tres
módulos. El daemon de Gradle conserva el criterio JDK 25 existente en
`gradle/gradle-daemon-jvm.properties`.

SDK mínimo: 24. SDK de compilación y destino de la aplicación: 37.
Configurar el SDK local en `local.properties`, que está excluido de Git.

## Verificación

Desde Windows, con un JDK compatible disponible para el wrapper:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest test lint --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

La segunda orden necesita un dispositivo o emulador conectado. `MainActivityTest`
comprueba el arranque real con Hilt, el package name y la pantalla inicial, además
de la conservación y el cierre del diálogo tras recrear la Activity.

Se ha retirado la prueba aritmética de la plantilla. Actualmente no hay lógica de
negocio que necesite pruebas unitarias; las tareas de pruebas locales no ejecutan
casos, y `domain` y `data` informan `NO-SOURCE`. Al introducir esa lógica, añadir
pruebas en `src/test` del módulo que la contiene, usando fakes de los contratos y
`kotlinx-coroutines-test` cuando
sea necesario controlar el tiempo o los dispatchers.

## Alcance de esta base

A2UI, integración con IA, lectura de Logcat, red y persistencia quedan para las
siguientes funcionalidades. Esta base no solicita permisos de diagnóstico ni añade
clientes de red, bases de datos o navegación.
