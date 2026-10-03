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

	/** Debe coincidir con {@code @Size(max = 500)} de {@code RegistroRequest#avatar} en chat-registro. */
	public static final int LONGITUD_MAXIMA_AVATAR = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	private Long version;

	@Column(length = 50)
	private String remitente;

	/**
	 * Avatar de {@code remitente} (enlace http(s) o etiqueta {@code <Blobatar .../>}, tal como lo
	 * guarda chat-registro), para que quien lista las notificaciones lo pinte sin otra consulta.
	 * Nulo si no hay remitente, si no eligio avatar, o si el mensaje de RabbitMQ no lo trae (solo
	 * viaja en una solicitud nueva). Tamano igual que {@code usuarios.avatar} en chat-registro.
	 */
	@Column(name = "avatar_remitente", length = Notificacion.LONGITUD_MAXIMA_AVATAR)
	private String avatarRemitente;

	@Column(nullable = false, length = 50)
	private String receptor;

	/** Tipo de notificacion (p. ej. "solicitud"). Siempre llega en el mensaje de RabbitMQ. */
	@Column(nullable = false, length = 50)
	private String tipo;

	/** Texto que se muestra en la notificacion. Ver NotificacionMapper#resolverContenido. */
	@Column(nullable = false, length = 255)
	private String contenido;

	/**
	 * Metadata adicional propia del tipo de notificacion, como JSON (p. ej. para "solicitud",
	 * aceptada/pendiente -- ver MetaSolicitud en chat-conversacion, el publicador). Nula si el
	 * mensaje de RabbitMQ no trae "meta" (no todos los tipos lo necesitan, y los mensajes
	 * anteriores a este campo tampoco lo traian).
	 */
	@Column(columnDefinition = "json")
	private String meta;

	@Column(nullable = false)
	private boolean leida = false;

	@CreatedDate
	@Column(name = "created_at", updatable = false, nullable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
