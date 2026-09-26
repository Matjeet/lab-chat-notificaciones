package com.arquetipo.demo.notificacion.amqp;

import com.arquetipo.demo.notificacion.amqp.dto.NotificacionEntrante;
import com.arquetipo.demo.notificacion.service.NotificacionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada de las notificaciones que llegan por RabbitMQ (exchange, cola y binding
 * declarados en {@code com.arquetipo.demo.common.config.RabbitMqConfig}). Hoy solo llegan
 * notificaciones de tipo "solicitud" (publicadas por chat-conversacion al crear una solicitud
 * de chat), pero la cola esta ligada con un routing key comodin ({@code notificacion.#}) para
 * no tener que tocar el binding cuando se sume un tipo nuevo.
 */
@Slf4j
@Component
public class NotificacionListener {

	private final NotificacionService service;

	public NotificacionListener(NotificacionService service) {
		this.service = service;
	}

	@RabbitListener(queues = "${app.amqp.notificaciones-queue:chat-notificaciones}")
	public void recibir(NotificacionEntrante mensaje) {
		log.debug(">> recibir(tipo='{}', solicitado='{}')", mensaje.tipo(), mensaje.solicitado());
		service.registrar(mensaje);
		log.debug("<< recibir()");
	}
}
