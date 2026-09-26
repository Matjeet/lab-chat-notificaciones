package com.arquetipo.demo.notificacion.amqp.dto;

/**
 * Mensaje tal como llega del exchange {@code chat.notificaciones} (ver
 * {@code com.arquetipo.demo.common.config.RabbitMqConfig}). Mismos nombres de campo que
 * {@code NotificacionAmqp} en chat-conversacion (el publicador, {@code conversacion/web/dto/})
 * para que Jackson deserialice por nombre sin configuracion extra -- si chat-conversacion
 * cambia esos nombres, hay que actualizar este record a mano (sin referencia compartida entre
 * repos, ver CLAUDE.md).
 *
 * <p>{@code contenido} no lo manda todavia ningun publicador (el unico tipo hoy,
 * "solicitud", no lo necesita: {@code NotificacionMapper} lo determina el mismo a partir de
 * {@code tipo}) -- el campo ya esta contemplado aqui para cuando algun publicador empiece a
 * mandarlo.
 */
public record NotificacionEntrante(String solicitante, String solicitado, String tipo, String contenido) {
}
