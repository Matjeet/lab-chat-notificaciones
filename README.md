# chat-notificaciones

Servicio que **consume por RabbitMQ** los eventos que publican otros microservicios del
sistema (hoy, solo `chat-conversacion`: solicitudes de chat nuevas), los persiste como
notificaciones para el usuario receptor en MySQL, y expone su lectura por **gRPC**
(`ListaNotificaciones`, paginada). No expone REST propio — solo gRPC y `Actuator` por HTTP.

> Estado actual: primera implementación (nace como copia del arquetipo MVC compartido, ver
> `chat-registro/` y `chat-conversacion/`). Cubre la recepción de notificaciones de tipo
> `"solicitud"` desde el exchange `chat.notificaciones` (declarado por `chat-conversacion`),
> la validación de `remitente`/`receptor` contra `chat-registro` por gRPC, la persistencia en
> la tabla `notificaciones`, y la consulta paginada de esa tabla por receptor
> (`ListaNotificaciones`, gRPC). Pendiente, a propósito: marcar una notificación como leída — no
> hay ningún rpc para eso todavía (por eso hoy `leida` siempre se queda en `false`).

## Arquitectura

Paquete por feature bajo `com.arquetipo.demo`, mismo patrón que `chat-registro/` y
`chat-conversacion/`:

- `common/` — infraestructura transversal: `config/JpaAuditingConfig` (poblado automático de
  `created_at`/`updated_at`), `config/RabbitMqConfig` (exchange, cola y binding, ver más
  abajo), `env/DotenvEnvironmentPostProcessor` (carga `.env`, mismo código que
  `chat-registro`), `exception/ServiceUnavailableException` (gRPC `UNAVAILABLE` al hablar con
  `chat-registro`) y `grpc/` (arranca/detiene el servidor gRPC embebido, mismo patrón que
  `chat-registro`/`chat-conversacion`, genérico — no sabe nada de notificaciones).
- `notificacion/` — la feature:
  - `domain/Notificacion` — entidad JPA: `remitente` (nulo si no aplica), `receptor`
    (obligatorio), `tipo` (obligatorio, sin validar contra una lista cerrada — cualquier string
    que mande un publicador), `contenido` (el texto a mostrar), `leida` (booleano) +
    `id`/`version`/auditoría, igual que `Usuario` en `chat-registro`.
  - `amqp/NotificacionListener` — `@RabbitListener` de la cola declarada en `RabbitMqConfig`;
    delega todo en `NotificacionService`.
  - `amqp/dto/NotificacionEntrante` — el mensaje tal como lo publica hoy `chat-conversacion`
    (`solicitante`/`solicitado`/`tipo`, más `contenido`, contemplado para cuando algún
    publicador empiece a mandarlo — ver el Javadoc de la clase).
  - `mapper/NotificacionMapper` — traduce el mensaje (`solicitante`/`solicitado`) a la entidad
    (`remitente`/`receptor`) y determina el `contenido` a partir del `tipo` cuando el mensaje
    no lo trae; y la entidad a `NotificacionResponse` (lectura, ver más abajo).
  - `service/NotificacionService` — `registrar`: valida el mensaje (`tipo` y `receptor`
    obligatorios, `remitente`/`receptor` deben existir en `chat-registro` vía
    `RegistroGrpcClient`) y persiste. Un mensaje inválido (falta un campo obligatorio, o el
    usuario no existe) se **descarta** (se loguea, no se persiste, no se relanza nada); si
    `chat-registro` no responde, la excepción se deja propagar para que RabbitMQ reencole el
    mensaje. `listaNotificaciones`: pagina las notificaciones de un receptor (no valida que
    exista en chat-registro, igual que el historial/lista de chats de chat-conversacion).
  - `web/dto/NotificacionResponse` + `web/dto/PageResponse` — DTO de lectura y envoltorio de
    paginación (mismo patrón que en chat-conversacion), usados por `NotificacionGrpcController`.
  - `grpc/NotificacionGrpcController` + `grpc/NotificacionGrpcMapper` — `ListaNotificaciones`
    (paginada por página/tamaño, no por cursor — este servicio es JPA/MySQL, no la agregación
    de Mongo que motivó el cursor de `ListaChats` en chat-conversacion); mismo patrón que
    `ConversacionGrpcController#historial`.
- `registro/grpc/` — `RegistroGrpcClient`: cliente gRPC de `chat-registro` (copia local y
  mínima de su `.proto`, solo `ExisteUsername`), mismo patrón que el cliente equivalente en
  `chat-conversacion`/`chat-gateway`.

### RabbitMQ: exchange, cola y binding

`chat-conversacion` declara y publica en el exchange `chat.notificaciones` (topic, durable).
Este servicio no lo posee, pero sí declara y posee:

- Una **cola durable** propia (`chat-notificaciones` por defecto) — sobrevive un reinicio del
  broker.
- Un **binding durable** de esa cola al exchange, con el routing key comodín
  `notificacion.#` — cubre `notificacion.solicitud` (el único tipo hoy) y cualquier tipo nuevo
  que se sume al mismo exchange sin tener que tocar el binding.

Ambos se declaran en `com.arquetipo.demo.common.config.RabbitMqConfig` (vía `RabbitAdmin`,
autoconfigurado por `spring-boot-starter-amqp`): si el broker se reinicia, o si este servicio
arranca antes que `chat-conversacion` haya declarado el exchange, la declaración es idempotente
y no se pierde nada que ya esté en la cola.

### gRPC: `ListaNotificaciones`

Único protocolo que expone este servicio (puerto `9092`, ver `src/main/proto/notificacion.proto`).
Devuelve las notificaciones de un `receptor`, paginadas (página/tamaño, no cursor), con:

- El **id** de la notificación.
- El nombre del **remitente** — `optional string`, ausente (no `""`) cuando la notificación no
  tiene remitente; comprobar con `hasRemitente()`, no asumir cadena vacía.
- El **tipo**.
- Si está **leída**.
- La **fecha de creación** (`created_at`, ISO-8601 UTC).

No valida que `receptor` exista en `chat-registro` — mismo criterio que `Historial`/`ListaChats`
en chat-conversacion (operaciones de lectura, a diferencia de `CrearSolicitud`, que sí valida
porque *crea* algo): un receptor que no existe, o que no tiene notificaciones, simplemente
devuelve una página vacía. Orden por defecto: más reciente primero (`createdAt` descendente);
`sort` en la petición permite cambiarlo (`"campo,direccion"`, igual que `Historial`).

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
