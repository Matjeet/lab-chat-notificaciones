package com.arquetipo.demo.notificacion.web.dto;

import java.time.Instant;

/**
 * Notificacion tal como la ve su receptor, para {@code ListaNotificaciones} (ver
 * {@code notificacion/grpc/NotificacionGrpcController}). {@code remitente} puede ser nulo, ver
 * {@code Notificacion#remitente}.
 */
public record NotificacionResponse(Long id, String remitente, String tipo, boolean leida, Instant createdAt) {
}
