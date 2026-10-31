# Domain

Núcleo de negocio Kotlin/JVM independiente de Android y de la inyección de
dependencias. Package raíz: `io.github.demiterhugo.debuglens.domain`.

Los modelos están en `model`, los contratos en `repository` y el análisis local
en `usecase`, bajo `src/main/kotlin/io/github/demiterhugo/debuglens/domain`.

- `DiagnosticInput` valida el límite y normaliza el texto.
- `AnalyzeDiagnosticUseCase` coordina validación, análisis de la primera cadena
  de excepciones y clasificación a partir de su causa más profunda.
- `StackTraceParser` es un detalle interno del caso de uso.
- `DiagnosticHistoryRepository` expone snapshots mediante Flow y operaciones
  suspendibles para guardar y borrar sesiones.

Los contratos expresan necesidades de negocio y emplean tipos del dominio. Los
detalles de Android, los DTO y las implementaciones pertenecen a las capas
externas. Coroutines Core se declara con `api` porque Flow aparece en el contrato
público. El módulo no depende de Coroutines Android.

Los constructores son explícitos. Hilt puede proporcionar las instancias desde
`app` sin introducir anotaciones del framework en el dominio. Las pruebas de
reglas de negocio se ubicarán en `src/test/kotlin`.
