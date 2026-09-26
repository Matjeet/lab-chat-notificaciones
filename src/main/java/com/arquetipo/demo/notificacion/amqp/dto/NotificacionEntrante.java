package com.arquetipo.demo.notificacion.amqp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

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
 * <p>{@code meta} lleva informacion adicional propia de {@code tipo} (p. ej. para
 * "solicitud", {@code MetaSolicitud} en chat-conversacion: {@code aceptada}/{@code pendiente},
 * hoy siempre {@code { aceptada: false, pendiente: true }}). Se captura como {@link JsonNode}
 * en bruto, no como una clase por tipo, porque su forma varia segun {@code tipo} y este
 * servicio solo la persiste tal cual (ver {@code NotificacionMapper#toEntity}) -- no la
 * interpreta ni la valida, asi que un campo nuevo dentro de {@code meta} (como {@code
 * pendiente}, sumado despues de {@code aceptada}) no necesita ningun cambio aqui.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: protege contra un campo nuevo que
 * algun publicador empiece a mandar y que este record todavia no declare -- sin esta
 * anotacion, Jackson rechazaria el mensaje entero (falla por defecto ante propiedades
 * desconocidas) y {@code NotificacionListener} lo reencolaria indefinidamente en vez de
 * simplemente ignorarlo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificacionEntrante(String solicitante, String solicitado, String tipo, String contenido,
		JsonNode meta) {
}
