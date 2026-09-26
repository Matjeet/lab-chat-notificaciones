-- ============================================================================
--  Bootstrap de la base de datos centralizada para el microservicio chat-notificaciones
-- ============================================================================
--  La instancia MySQL es COMPARTIDA por todos los microservicios del sistema.
--  Cada servicio tiene:
--    - su propio esquema (aqui: chat_notificaciones)
--    - su propio usuario con permisos SOLO sobre ese esquema
--
--  Este script crea el ESQUEMA y el USUARIO. Se ejecuta UNA sola vez por entorno,
--  con un usuario administrador (root):
--
--    mysql -u root -p < src/main/resources/db/bootstrap.sql
--
--  Las TABLAS (notificaciones) las crea Flyway al arrancar la aplicacion, con las
--  migraciones versionadas de src/main/resources/db/migration. Hibernate solo valida
--  (spring.jpa.hibernate.ddl-auto=validate), no modifica el esquema.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS chat_notificaciones
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Usuario propio del servicio (accesible desde localhost y desde contenedores).
CREATE USER IF NOT EXISTS 'chat_notificaciones_svc'@'%'
  IDENTIFIED BY 'chat_notificaciones_pw';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
  ON chat_notificaciones.*
  TO 'chat_notificaciones_svc'@'%';

FLUSH PRIVILEGES;
