package com.arquetipo.demo.notificacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Notificacion para un usuario del sistema (hoy, una solicitud de chat entrante -- ver
 * {@code com.arquetipo.demo.notificacion.amqp.NotificacionListener}). {@code remitente} puede
 * ser nulo: no todos los tipos de notificacion tienen necesariamente un usuario de origen (p.
 * ej. un aviso del propio sistema); {@code receptor} siempre existe.
 */
@Getter
@Setter
@Entity
@Table(name = "notificaciones")
@EntityListeners(AuditingEntityListener.class)
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	private Long version;

	@Column(length = 50)
	private String remitente;

	@Column(nullable = false, length = 50)
	private String receptor;

	/** Tipo de notificacion (p. ej. "solicitud"). Siempre llega en el mensaje de RabbitMQ. */
	@Column(nullable = false, length = 50)
	private String tipo;

	/** Texto que se muestra en la notificacion. Ver NotificacionMapper#resolverContenido. */
	@Column(nullable = false, length = 255)
	private String contenido;

	@Column(nullable = false)
	private boolean leida = false;

	@CreatedDate
	@Column(name = "created_at", updatable = false, nullable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
