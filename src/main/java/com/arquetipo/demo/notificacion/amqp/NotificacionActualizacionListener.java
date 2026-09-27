package com.arquetipo.demo.notificacion.amqp;

import com.arquetipo.demo.notificacion.amqp.dto.NotificacionActualizacionEntrante;
import com.arquetipo.demo.notificacion.service.NotificacionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada de la cola de actualizaciones (exchange, cola y binding declarados en
 * {@code com.arquetipo.demo.common.config.RabbitMqConfig}) -- a diferencia de
 * {@link NotificacionListener}, que registra notificaciones nuevas, esta cola solo actualiza
 * una notificacion ya existente.
 */
@Slf4j
@Component
public class NotificacionActualizacionListener {

	private final NotificacionService service;

	public NotificacionActualizacionListener(NotificacionService service) {
		this.service = service;
	}

	@RabbitListener(queues = "${app.amqp.actualizaciones-queue:chat-notificaciones.actualizaciones}")
	public void recibir(NotificacionActualizacionEntrante mensaje) {
		log.debug(">> recibir(tipo='{}', solicitado='{}')", mensaje.tipo(), mensaje.solicitado());
		service.actualizar(mensaje);
		log.debug("<< recibir()");
	}
}
