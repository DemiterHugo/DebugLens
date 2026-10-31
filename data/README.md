# Data

Biblioteca Android que depende de `domain`.
Namespace: `io.github.demiterhugo.debuglens.data`.

Cuando se implemente una fuente de datos, ubicar las implementaciones en
`repository`, las fuentes en `source` y los mapeos necesarios en `mapper`, bajo
`src/main/kotlin/io/github/demiterhugo/debuglens/data`.

Las implementaciones cumplirán contratos definidos por `domain`, encapsulando los
detalles de Android y transformando sus resultados a modelos de dominio. Mantener
las operaciones suspendibles seguras para invocarlas desde el hilo principal y
propagar la cancelación de coroutines.

La aplicación proporciona las dependencias concretas y los bindings de Hilt en
su punto de composición. Añadir dependencias y procesadores a este módulo solo
cuando una implementación los necesite. Las pruebas de repositorios y mapeos se
ubicarán en `src/test/kotlin`.

Todavía no hay implementaciones de Logcat, IA, A2UI, almacenamiento o red.
