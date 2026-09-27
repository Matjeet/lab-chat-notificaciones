package com.arquetipo.demo.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchange y colas/bindings de RabbitMQ por donde chat-notificaciones recibe los eventos que
 * publica chat-conversacion. Todo se declara DURABLE (sobrevive un reinicio del broker): si
 * este servicio esta caido cuando se publica un mensaje, lo encuentra en la cola al volver a
 * levantarse -- no se pierde. Mismo exchange (topic, nombre y forma identicos) que declara
 * chat-conversacion en su propio {@code RabbitMqConfig}; RabbitMQ no lo vuelve a crear si ya
 * existe con las mismas propiedades (declaracion idempotente vía {@code RabbitAdmin},
 * autoconfigurado por {@code spring-boot-starter-amqp}).
 *
 * <p>Dos colas sobre el mismo exchange, cada una con su propio proposito, distinguidas por
 * routing key (topic exchange: un mensaje se entrega a cada binding cuyo patron haga match, no
 * solo al primero):
 * <ul>
 *   <li>{@code notificacionesQueue} ({@code notificacion.#}, ver
 *       {@code notificacion.amqp.NotificacionListener}) -- registra notificaciones nuevas.
 *   <li>{@code notificacionesActualizacionesQueue} ({@code actualizacion.#}, ver
 *       {@code notificacion.amqp.NotificacionActualizacionListener}) -- actualiza una
 *       notificacion ya existente. Un prefijo de routing key distinto de {@code notificacion.}
 *       a proposito: si usara {@code notificacion.algo}, tambien haria match con el comodin
 *       {@code notificacion.#} de la cola de arriba y el mensaje llegaria (y se procesaria) por
 *       las dos colas a la vez.
 * </ul>
 *
 * <p>No se declara aqui un {@code RabbitTemplate}: este servicio solo consume, nunca publica.
 * El {@link MessageConverter} de abajo si hace falta -- Spring Boot lo usa automaticamente
 * tanto para el listener por defecto como para publicar, sin necesidad de declarar la fabrica
 * del contenedor a mano.
 */
@Configuration
public class RabbitMqConfig {

	@Value("${app.amqp.notificaciones-exchange:chat.notificaciones}")
	private String nombreExchange;

	@Value("${app.amqp.notificaciones-queue:chat-notificaciones}")
	private String nombreQueue;

	@Value("${app.amqp.notificaciones-routing-key:notificacion.#}")
	private String routingKey;

	@Value("${app.amqp.actualizaciones-queue:chat-notificaciones.actualizaciones}")
	private String nombreQueueActualizaciones;

	@Value("${app.amqp.actualizaciones-routing-key:actualizacion.#}")
	private String routingKeyActualizaciones;

	@Bean
	public TopicExchange notificacionesExchange() {
		return new TopicExchange(nombreExchange, true, false);
	}

	@Bean
	public Queue notificacionesQueue() {
		return QueueBuilder.durable(nombreQueue).build();
	}

	@Bean
	public Binding notificacionesBinding(Queue notificacionesQueue, TopicExchange notificacionesExchange) {
		return BindingBuilder.bind(notificacionesQueue).to(notificacionesExchange).with(routingKey);
	}

	@Bean
	public Queue notificacionesActualizacionesQueue() {
		return QueueBuilder.durable(nombreQueueActualizaciones).build();
	}

	@Bean
	public Binding notificacionesActualizacionesBinding(
			Queue notificacionesActualizacionesQueue, TopicExchange notificacionesExchange) {
		return BindingBuilder.bind(notificacionesActualizacionesQueue)
				.to(notificacionesExchange)
				.with(routingKeyActualizaciones);
	}

	@Bean
	public MessageConverter jackson2JsonMessageConverter() {
		// Sin ObjectMapper inyectado a proposito: Spring Boot 4 autoconfigura Jackson 3
		// (tools.jackson.databind.ObjectMapper) para el propio framework, pero
		// Jackson2JsonMessageConverter sigue siendo Jackson 2.x (com.fasterxml) -- son tipos
		// distintos, no hay bean de ese tipo que inyectar. El constructor sin argumentos crea
		// su propio ObjectMapper interno, suficiente para el DTO simple que se deserializa
		// aqui. Mismo criterio que chat-conversacion (el publicador).
		return new Jackson2JsonMessageConverter();
	}
}
