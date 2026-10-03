-- Avatar del remitente de la notificacion (ver
-- com.arquetipo.demo.notificacion.domain.Notificacion#avatarRemitente): un enlace http(s), o una
-- etiqueta <Blobatar .../>, tal como lo guarda chat-registro y lo reenvia chat-conversacion en el
-- mensaje de RabbitMQ -- mismo VARCHAR(500) que usuarios.avatar en chat-registro. Nulo: no todos
-- los tipos tienen remitente, el remitente puede no haber elegido avatar, y las filas ya
-- existentes (anteriores a este cambio) tampoco lo tienen.
ALTER TABLE notificaciones
    ADD COLUMN avatar_remitente VARCHAR(500) NULL AFTER remitente;
