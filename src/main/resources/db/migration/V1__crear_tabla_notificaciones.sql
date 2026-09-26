-- Tabla de notificaciones para un usuario del sistema. Se corresponde con la entidad
-- com.arquetipo.demo.notificacion.domain.Notificacion.
CREATE TABLE notificaciones (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    remitente    VARCHAR(50)  NULL,
    receptor     VARCHAR(50)  NOT NULL,
    tipo         VARCHAR(50)  NOT NULL,
    contenido    VARCHAR(255) NOT NULL,
    leida        BIT(1)       NOT NULL,
    version      BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_notificaciones PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Acceso mas comun: notificaciones de un receptor (p. ej. las no leidas de un usuario).
CREATE INDEX idx_notificaciones_receptor ON notificaciones (receptor);
