# chat-notificaciones

Servicio que **consume por RabbitMQ** los eventos que publican otros microservicios del
sistema (hoy, solo `chat-conversacion`: solicitudes de chat nuevas), los persiste como
notificaciones para el usuario receptor en MySQL, y expone su lectura y actualización por
**gRPC** (`ListaNotificaciones`, paginada, y `ActualizarLeida`). No expone REST propio — solo
gRPC y `Actuator` por HTTP.

> Estado actual: primera implementación (nace como copia del arquetipo MVC compartido, ver
> `chat-registro/` y `chat-conversacion/`). Cubre la recepción de notificaciones de tipo
> `"solicitud"` desde el exchange `chat.notificaciones` (declarado por `chat-conversacion`),
> la validación de `remitente`/`receptor` contra `chat-registro` por gRPC, la persistencia en
> la tabla `notificaciones`, la consulta paginada de esa tabla por receptor
> (`ListaNotificaciones`, gRPC) y marcar una notificación como leída/no leída
> (`ActualizarLeida`, gRPC). Pendiente, a propósito: la autenticación — ningún rpc de este
> servicio valida quién hace la llamada (ver CLAUDE.md).

## Arquitectura

Paquete por feature bajo `com.arquetipo.demo`, mismo patrón que `chat-registro/` y
`chat-conversacion/`:

- `common/` — infraestructura transversal: `config/JpaAuditingConfig` (poblado automático de
  `created_at`/`updated_at`), `config/RabbitMqConfig` (exchange y dos colas/bindings, ver más
  abajo), `env/DotenvEnvironmentPostProcessor` (carga `.env`, mismo código que
  `chat-registro`), `exception/{ServiceUnavailableException,ResourceNotFoundException}` (gRPC
  `UNAVAILABLE` al hablar con `chat-registro`; `NOT_FOUND` cuando `ActualizarLeida` recibe un
  `id` que no existe) y `grpc/` (arranca/detiene el servidor gRPC embebido, mismo patrón que
  `chat-registro`/`chat-conversacion`, genérico — no sabe nada de notificaciones).
- `notificacion/` — la feature:
  - `domain/Notificacion` — entidad JPA: `remitente` (nulo si no aplica), `avatarRemitente`
    (avatar de `remitente`, columna `avatar_remitente` `VARCHAR(500)`, nulo si no hay remitente,
    si no eligió avatar, o si el mensaje no lo trae — ver "Avatar del remitente" más abajo),
    `receptor` (obligatorio), `tipo` (obligatorio, sin validar contra una lista cerrada —
    cualquier string que mande un publicador), `contenido` (el texto a mostrar), `meta` (JSON,
    nulo si el mensaje no lo trae — información adicional propia del `tipo`, ver más abajo),
    `leida` (booleano) + `id`/`version`/auditoría, igual que `Usuario` en `chat-registro`.
  - `amqp/NotificacionListener` — `@RabbitListener` de la cola de **registro** declarada en
    `RabbitMqConfig`; delega todo en `NotificacionService#registrar`.
  - `amqp/NotificacionActualizacionListener` — `@RabbitListener` de la cola de
    **actualización** (distinta de la de arriba, ver más abajo); delega todo en
    `NotificacionService#actualizar`.
  - `amqp/dto/NotificacionEntrante` — el mensaje tal como lo publica hoy `chat-conversacion`
    (`solicitante`/`solicitado`/`tipo`, más `meta` y `avatar` —el del `solicitante`, solo en una
    solicitud nueva—, y `contenido`, contemplado para cuando algún publicador empiece a
    mandarlo — ver el Javadoc de la clase). `meta` se captura como
    `JsonNode` en bruto (no una clase por tipo): su forma varía según `tipo` y este servicio
    solo la persiste tal cual, no la interpreta. `@JsonIgnoreProperties(ignoreUnknown = true)`
    protege contra cualquier otro campo nuevo que un publicador empiece a mandar.
  - `amqp/dto/NotificacionActualizacionEntrante` — mismo vocabulario y mismo tratamiento de
    `meta` que `NotificacionEntrante`, pero para la cola de actualización: no crea nada, ver
    más abajo.
  - `mapper/NotificacionMapper` — traduce el mensaje (`solicitante`/`solicitado`) a la entidad
    (`remitente`/`receptor`), determina el `contenido` a partir del `tipo` cuando el mensaje no
    lo trae, y convierte `meta` (`JsonNode`) a texto (`JsonNode#toString()`) para la columna
    JSON; y la entidad a `NotificacionResponse` (lectura, ver más abajo).
  - `service/NotificacionService` — `registrar`: valida el mensaje (`tipo` y `receptor`
    obligatorios, `remitente`/`receptor` deben existir en `chat-registro` vía
    `RegistroGrpcClient`) y persiste. Un mensaje inválido (falta un campo obligatorio, o el
    usuario no existe) se **descarta** (se loguea, no se persiste, no se relanza nada); si
    `chat-registro` no responde, la excepción se deja propagar para que RabbitMQ reencole el
    mensaje. `listaNotificaciones`: pagina las notificaciones de un receptor (no valida que
    exista en chat-registro, igual que el historial/lista de chats de chat-conversacion).
    `actualizarLeida`: cambia el booleano `leida` de una notificacion existente por su `id`
    (`ResourceNotFoundException` si no existe). `actualizar`: cambia `contenido`/`meta` de la
    notificación más reciente cuyo `remitente`/`receptor`/`tipo` coincidan con el mensaje de la
    cola de actualización — no crea nada; si no encuentra ninguna, se descarta igual que un
    mensaje de `registrar` inválido.
  - `web/dto/NotificacionResponse` + `web/dto/PageResponse` — DTO de lectura y envoltorio de
    paginación (mismo patrón que en chat-conversacion), usados por `NotificacionGrpcController`.
  - `grpc/NotificacionGrpcController` + `grpc/NotificacionGrpcMapper` — `ListaNotificaciones`
    (paginada por página/tamaño, no por cursor — este servicio es JPA/MySQL, no la agregación
    de Mongo que motivó el cursor de `ListaChats` en chat-conversacion; mismo patrón que
    `ConversacionGrpcController#historial`) y `ActualizarLeida` (`NOT_FOUND` si el `id` no
    existe, mismo patrón que `CrearSolicitud`).
- `registro/grpc/` — `RegistroGrpcClient`: cliente gRPC de `chat-registro` (copia local y
  mínima de su `.proto`, solo `ExisteUsername`), mismo patrón que el cliente equivalente en
  `chat-conversacion`/`chat-gateway`.

### RabbitMQ: exchange, dos colas y dos bindings

`chat-conversacion` declara y publica en el exchange `chat.notificaciones` (topic, durable).
Este servicio no lo posee, pero sí declara y posee **dos colas**, cada una con su propio
propósito, sobre ese mismo exchange:

- **`chat-notificaciones`** (por defecto) — **registra notificaciones nuevas**
  (`notificacion.amqp.NotificacionListener` → `NotificacionService#registrar`). Binding con
  routing key comodín `notificacion.#` — cubre `notificacion.solicitud` (el único tipo hoy) y
  cualquier tipo nuevo que se sume al mismo exchange sin tener que tocar el binding.
- **`chat-notificaciones.actualizaciones`** (por defecto) — **actualiza una notificación ya
  existente** (`notificacion.amqp.NotificacionActualizacionListener` →
  `NotificacionService#actualizar`); nunca crea una fila nueva. Identifica la notificación a
  actualizar por `remitente`/`receptor`/`tipo` (la más reciente que coincida — no por `id`,
  que ningún publicador externo conoce hoy) y solo puede cambiar `contenido`/`meta`; un campo
  ausente en el mensaje deja ese valor sin tocar. Binding con routing key comodín
  `actualizacion.#` — **a propósito con un prefijo distinto** de `notificacion.` (el de la cola
  de arriba): un topic exchange entrega un mensaje a **todos** los bindings cuyo patrón haga
  match, así que si esta routing key empezara igual, el mensaje también llegaría (y se
  procesaría) por la cola de registrar.

Ambas colas y bindings se declaran en `com.arquetipo.demo.common.config.RabbitMqConfig` (vía
`RabbitAdmin`, autoconfigurado por `spring-boot-starter-amqp`): si el broker se reinicia, o si
este servicio arranca antes que `chat-conversacion` haya declarado el exchange, la declaración
es idempotente y no se pierde nada que ya esté en las colas.

### gRPC: `ListaNotificaciones` y `ActualizarLeida`

Los dos únicos protocolos que expone este servicio (puerto `9092`, ver
`src/main/proto/notificacion.proto`).

**`ListaNotificaciones`** devuelve las notificaciones de un `receptor`, paginadas
(página/tamaño, no cursor), con:

- El **id** de la notificación.
- El nombre del **remitente** — `optional string`, ausente (no `""`) cuando la notificación no
  tiene remitente; comprobar con `hasRemitente()`, no asumir cadena vacía.
- El **tipo**.
- Si está **leída**.
- La **fecha de creación** (`created_at`, ISO-8601 UTC).
- La **metadata adicional** (`meta`) — `optional string`, el JSON tal cual se persistió (ver
  `domain/Notificacion#meta` más arriba), sin interpretar; ausente (no `""`) cuando la
  notificación no tiene meta — comprobar con `hasMeta()`, no asumir cadena vacía.
- El **avatar del remitente** (`avatar_remitente`) — `optional string`: un enlace http(s) o una
  etiqueta `<Blobatar .../>`, tal como lo guarda `chat-registro` y el cliente lo renderiza tal
  cual; ausente (no `""`) cuando no hay remitente, no eligió avatar o la notificación no es una
  solicitud nueva — comprobar con `hasAvatarRemitente()`.

**Avatar del remitente**: `chat-conversacion` manda `avatar` (raíz del JSON) solo en el mensaje
de una solicitud **nueva** (`notificacion.solicitud`), y solo el del `solicitante` — nunca el del
`solicitado`; se omite si no tiene avatar o aún no tiene perfil guardado. Lo recibe
`NotificacionEntrante#avatar` y se persiste en `avatar_remitente` (migración `V3`). No viaja en
la cola de actualización (`actualizacion.solicitud`): `actualizar` no lo toca, así que una
notificación conserva el avatar con el que se creó. Guardia defensiva: un avatar de más de 500
caracteres (no debería pasar, `chat-registro` ya lo limita) se descarta con un `WARN` en vez de
reventar el insert — sin ella, la excepción no atrapada haría que RabbitMQ reencolara el mensaje
para siempre. Nunca se escribe el avatar en un log.

No valida que `receptor` exista en `chat-registro` — mismo criterio que `Historial`/`ListaChats`
en chat-conversacion (operaciones de lectura, a diferencia de `CrearSolicitud`, que sí valida
porque *crea* algo): un receptor que no existe, o que no tiene notificaciones, simplemente
devuelve una página vacía. Orden por defecto: más reciente primero (`createdAt` descendente);
`sort` en la petición permite cambiarlo (`"campo,direccion"`, igual que `Historial`).

**`ActualizarLeida`** marca una notificación (por `id`) como leída o no leída (`leida`, booleano
— `0`/`false` = no leída, `1`/`true` = leída) y devuelve el `NotificacionItem` actualizado.
`NOT_FOUND` si `id` no corresponde a ninguna notificación existente. No valida quién hace la
llamada — igual que el resto de rpc de este servicio, no hay autenticación todavía (ver
CLAUDE.md).

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, solo para `Actuator`) |
| Mensajería | RabbitMQ (`spring-boot-starter-amqp`) — consumo, no publica nada |
| gRPC | `io.grpc` a mano (sin starter de terceros), servidor embebido (`ListaNotificaciones`) + cliente de `chat-registro` |
| Lenguaje | Java 25 (toolchain de Gradle) |
| Build | Gradle (wrapper incluido) |
| Persistencia | Spring Data JPA + Hibernate; MySQL (runtime), H2 en memoria (tests) |
| Migraciones | Flyway (`spring-boot-starter-flyway` + `flyway-mysql`) |
| Observabilidad | Spring Boot Actuator |
| Utilidades | Lombok, DevTools |

## Base de datos

Comparte la instancia MySQL centralizada del sistema (`localhost:3306`) con su propio esquema
y usuario:

| Servicio | Esquema | Usuario |
|---|---|---|
| chat-notificaciones | `chat_notificaciones` | `chat_notificaciones_svc` |

Antes del primer arranque, provisiona el esquema y el usuario (una sola vez por entorno, como
`root`):

```bash
mysql -u root -p < src/main/resources/db/bootstrap.sql
```

Crea el esquema `chat_notificaciones` y el usuario `chat_notificaciones_svc` /
`chat_notificaciones_pw`. Las tablas las crea **Flyway** al arrancar la aplicación
(`src/main/resources/db/migration`); Hibernate solo valida (`ddl-auto=validate`).

## Arrancar

```bash
cp .env.example .env
./gradlew bootRun
```

> Gradle necesita un JDK 17+ para ejecutarse y la toolchain compila con Java 25. Si tu
> `JAVA_HOME` apunta a un JDK antiguo, ajústalo o descomenta `org.gradle.java.home` en
> `gradle.properties`.

Requiere **RabbitMQ** alcanzable (por defecto `localhost:5672`, ver `spring.rabbitmq.*` /
variables `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`) — la
conexión es perezosa (no bloquea el arranque), pero sin broker no llega ningún mensaje. Para
levantar uno local con Podman:

```bash
podman run -d --name rabbitmq-dev --hostname rabbitmq-dev \
  -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

Y, para validar `remitente`/`receptor`, una instancia de `chat-registro` alcanzable por gRPC
(por defecto `localhost:9090`, ver `servicios.registro.*` / variables
`REGISTRO_GRPC_HOST`/`REGISTRO_GRPC_PORT`) — ver `chat-registro/README.md`. Y, para que llegue
algún mensaje real, una instancia de `chat-conversacion` publicando solicitudes de chat — ver
`chat-conversacion/README.md`.

| Recurso | URL |
|---------|-----|
| gRPC (`ListaNotificaciones`) | localhost:9092 — ver `src/main/proto/notificacion.proto` |
| Swagger UI | http://localhost:8083/swagger-ui.html |
| OpenAPI JSON | http://localhost:8083/v3/api-docs |
| Actuator health | http://localhost:8083/actuator/health |
| Panel de administración de RabbitMQ | http://localhost:15672 (usuario/clave por defecto: `guest`/`guest`) |

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar`

## Imagen de contenedor

`Dockerfile` es multi-stage (build con `eclipse-temurin:25-jdk` + Gradle, runtime con
`eclipse-temurin:25-jre`, corre como usuario no root) — funciona igual con Docker o con
[Podman](https://podman.io/). Expone `8083` (HTTP, solo `Actuator`) y `9092` (gRPC).

```bash
podman build -t chat-notificaciones .
```

Para correrla necesita llegar a MySQL, RabbitMQ y `chat-registro` por gRPC — si esos corren en
tu máquina (no en otro contenedor), `localhost` **dentro** del contenedor no es tu máquina: con
Podman Machine (Windows/Mac) usa el host especial `host.containers.internal` (con Docker
Desktop es `host.docker.internal`):

```bash
podman run -d --name chat-notificaciones \
  -p 8083:8083 -p 9092:9092 \
  -e DB_URL="jdbc:mysql://host.containers.internal:3306/chat_notificaciones?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8" \
  -e RABBITMQ_HOST="host.containers.internal" \
  -e REGISTRO_GRPC_HOST="host.containers.internal" \
  chat-notificaciones
```

Para levantar el sistema completo (chat-notificaciones + chat-conversacion + chat-registro +
RabbitMQ + MySQL + MongoDB + chat-gateway) de una vez, ver `docker-compose.yml` en la raíz de
`Proyectos/Chat/`.
