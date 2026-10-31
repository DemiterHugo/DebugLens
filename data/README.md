# Data

Biblioteca Android que depende de `domain`.
Namespace: `io.github.demiterhugo.debuglens.data`.

Las implementaciones están en `repository`, bajo
`src/main/kotlin/io/github/demiterhugo/debuglens/data`.

`InMemoryDiagnosticHistoryRepository` cumple el contrato de `domain` con un
StateFlow privado y snapshots de lectura. Genera identificadores únicos, emite
primero los registros más recientes y conserva un máximo de 20 entradas por
defecto. La capacidad se puede configurar al construirlo y debe ser positiva.
El borrado elimina los registros del repositorio sin modificar snapshots previos.

Las implementaciones cumplirán contratos definidos por `domain`, encapsulando los
detalles de Android y transformando sus resultados a modelos de dominio. Mantener
las operaciones suspendibles seguras para invocarlas desde el hilo principal y
propagar la cancelación de coroutines.

La aplicación proporciona las dependencias concretas y los bindings de Hilt en
su punto de composición. Añadir dependencias y procesadores a este módulo solo
cuando una implementación los necesite. Las pruebas de repositorios y mapeos se
ubicarán en `src/test/kotlin`.

La instancia vive como singleton de Hilt, definido desde `app`. Los registros
desaparecen al terminar el proceso. No hay almacenamiento en disco ni
implementaciones de lectura de Logcat, IA, A2UI o red.
