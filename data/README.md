# Data

Biblioteca Android que depende de `domain`.
Namespace: `io.github.demiterhugo.debuglens.data`.

Las implementaciones están en `repository`, bajo
`src/main/kotlin/io/github/demiterhugo/debuglens/data`.

`DiagnosticHistoryStorage` crea y posee la base Room, y expone el repositorio
mediante el contrato de `domain`. La aplicación mantiene este recurso como
singleton de Hilt. Los tests cierran explícitamente el recurso. Room, su driver,
las entidades, el DAO y los mapeos son detalles internos de `data`.

`RoomDiagnosticHistoryRepository` genera identificadores únicos y conserva las
últimas 20 sesiones en orden de inserción descendente. Guarda en una transacción
la sesión, su cadena de causas y la retención. Las causas se borran en cascada al
eliminar una sesión. Los snapshots se convierten a modelos de dominio sin volver
a ejecutar el analizador; el orden de las causas se reconstruye por posición.
Room ejecuta las consultas suspendibles fuera del hilo principal y el mapeo del
Flow utiliza `Dispatchers.Default`.

Las implementaciones cumplirán contratos definidos por `domain`, encapsulando los
detalles de Android y transformando sus resultados a modelos de dominio. Mantener
las operaciones suspendibles seguras para invocarlas desde el hilo principal y
propagar la cancelación de coroutines.

La aplicación proporciona las dependencias concretas y los bindings de Hilt en
su punto de composición. Añadir dependencias y procesadores a este módulo solo
cuando una implementación los necesite. Las pruebas de repositorios y mapeos se
ubican en `src/test/kotlin` y las de SQL real en `src/androidTest/kotlin`.

Los registros sobreviven al cierre del proceso. Las reglas de backup de `app`
excluyen las bases de datos de la copia en la nube y la transferencia a otro
dispositivo. No hay implementaciones de lectura de Logcat, IA, A2UI ni red.

Room 3.0.3 usa `AndroidSQLiteDriver` de SQLite 2.7.1, disponible en el sistema
Android; no se empaqueta otra copia de SQLite. KSP genera el DAO y el esquema
versionado en `schemas`. La versión inicial es 1 y no se activa ningún fallback
de migración destructiva. Los cambios de esquema futuros deben añadir y probar
una migración explícita antes de incrementar la versión.
