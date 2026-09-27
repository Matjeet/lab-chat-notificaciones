package com.arquetipo.demo.notificacion.amqp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mensaje tal como llega por la cola de actualizaciones ({@code notificacion.amqp.
 * NotificacionActualizacionListener}, routing key {@code actualizacion.#}) -- a diferencia de
 * {@link NotificacionEntrante} (que crea una notificacion nueva), este no crea nada: identifica
 * la notificacion mas reciente cuyo {@code remitente}/{@code solicitado}/{@code tipo} coincidan
 * (mismo vocabulario que {@code NotificacionEntrante}, ver
 * {@code notificacion.service.NotificacionService#actualizar}) y le actualiza {@code contenido}
 * y/o {@code meta}. Un campo ausente (nulo) en el mensaje deja ese valor sin tocar en la fila
 * existente -- no es una foto completa de la notificacion, solo lo que cambia.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: mismo motivo que en
 * {@code NotificacionEntrante} -- protege contra un campo nuevo que algun publicador empiece a
 * mandar y que este record todavia no declare.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificacionActualizacionEntrante(String solicitante, String solicitado, String tipo,
		String contenido, JsonNode meta) {
}
