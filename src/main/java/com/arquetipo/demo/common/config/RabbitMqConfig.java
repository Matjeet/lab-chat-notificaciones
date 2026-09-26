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
 * Exchange, cola y binding de RabbitMQ por donde chat-notificaciones recibe los eventos que
 * publica chat-conversacion (ver {@code com.arquetipo.demo.notificacion.amqp.NotificacionListener}).
 * Los tres se declaran DURABLES (sobreviven un reinicio del broker): si este servicio esta
 * caido cuando se publica un mensaje, lo encuentra en la cola al volver a levantarse -- no se
 * pierde. Mismo exchange (topic, nombre y forma identicos) que declara chat-conversacion en su
 * propio {@code RabbitMqConfig}; RabbitMQ no lo vuelve a crear si ya existe con las mismas
 * propiedades (declaracion idempotente vía {@code RabbitAdmin}, autoconfigurado por
 * {@code spring-boot-starter-amqp}).
 *
 * <p>El binding usa un routing key comodin ({@code notificacion.#}, no solo
 * {@code notificacion.solicitud}) para no tener que tocar la cola cuando chat-conversacion (u
 * otro publicador futuro) sume un tipo de notificacion nuevo al mismo exchange.
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
