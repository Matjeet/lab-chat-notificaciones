-- Metadata adicional de la notificacion, propia de su tipo (ver
-- com.arquetipo.demo.notificacion.domain.Notificacion#meta). Nula: no todos los tipos la
-- traen, y las filas ya existentes (anteriores a este cambio) tampoco la tienen.
ALTER TABLE notificaciones
    ADD COLUMN meta JSON NULL AFTER contenido;
