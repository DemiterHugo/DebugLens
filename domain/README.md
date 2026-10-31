# Domain

Núcleo de negocio Kotlin/JVM independiente de Android y de la inyección de
dependencias. Package raíz: `io.github.demiterhugo.debuglens.domain`.

Cuando se implemente la primera funcionalidad, ubicar los modelos en `model`, los
contratos en `repository` y los casos de uso con lógica propia en `usecase`, bajo
`src/main/kotlin/io/github/demiterhugo/debuglens/domain`.

Los contratos expresan necesidades de negocio y emplean tipos del dominio. Los
detalles de Android, los DTO y las implementaciones pertenecen a las capas
externas. Añadir Coroutines Core al definir el primer contrato con Flow o una
necesidad concreta de coroutines; actualmente el módulo solo necesita Kotlin.

Los constructores son explícitos. Hilt puede proporcionar las instancias desde
`app` sin introducir anotaciones del framework en el dominio. Las pruebas de
reglas de negocio se ubicarán en `src/test/kotlin`.
