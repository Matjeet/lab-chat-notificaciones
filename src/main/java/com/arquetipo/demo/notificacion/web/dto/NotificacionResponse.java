package com.arquetipo.demo.notificacion.web.dto;

import java.time.Instant;

/**
 * Notificacion tal como la ve su receptor, para {@code ListaNotificaciones} (ver
 * {@code notificacion/grpc/NotificacionGrpcController}). {@code remitente} puede ser nulo, ver
 * {@code Notificacion#remitente}. {@code meta} tambien puede ser nulo (ver
 * {@code Notificacion#meta}); se propaga tal cual como texto JSON, sin interpretarlo.
 * {@code avatarRemitente} es el avatar de {@code remitente} (ver
 * {@code Notificacion#avatarRemitente}), nulo si no tiene o no aplica.
 */
public record NotificacionResponse(Long id, String remitente, String tipo, boolean leida, Instant createdAt,
		String meta, String avatarRemitente) {
}
