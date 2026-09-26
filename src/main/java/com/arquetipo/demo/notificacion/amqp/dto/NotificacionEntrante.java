package com.arquetipo.demo.notificacion.amqp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: chat-conversacion manda ademas un
 * campo {@code meta} (informacion adicional propia del tipo, p. ej. si la solicitud ya fue
 * aceptada) que este servicio todavia no consume -- sin esta anotacion, Jackson rechazaria el
 * mensaje entero por traer un campo que este record no declara (falla por defecto ante
 * propiedades desconocidas), y {@code NotificacionListener} lo reencolaria indefinidamente en
 * vez de simplemente ignorarlo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificacionEntrante(String solicitante, String solicitado, String tipo, String contenido) {
}
