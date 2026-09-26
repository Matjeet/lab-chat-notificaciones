package com.arquetipo.demo;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio chat-notificaciones.
 *
 * <p>Infraestructura transversal (auditoria JPA, carga de .env, excepciones de dominio) en
 * {@code com.arquetipo.demo.common}; el flujo de notificaciones en
 * {@code com.arquetipo.demo.notificacion} -- consume eventos de RabbitMQ (
 * {@code notificacion/amqp/}), los valida contra {@code chat-registro} por gRPC (
 * {@code com.arquetipo.demo.registro.grpc}, cliente, este servicio no expone gRPC propio) y
 * los persiste en MySQL con el esquema versionado por Flyway.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
		title = "chat-notificaciones",
		version = "v1",
		description = "Notificaciones para los usuarios del sistema (hoy, solicitudes de chat entrantes), consumidas por RabbitMQ"))
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
