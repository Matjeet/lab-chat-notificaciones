package com.arquetipo.demo.notificacion.mapper;

import com.arquetipo.demo.notificacion.amqp.dto.NotificacionEntrante;
import com.arquetipo.demo.notificacion.domain.Notificacion;
import org.springframework.stereotype.Component;

/**
 * Traduce el mensaje entrante de RabbitMQ ({@code solicitante}/{@code solicitado}, vocabulario
 * del publicador) a la entidad {@link Notificacion} ({@code remitente}/{@code receptor},
 * vocabulario generico de este servicio).
 */
@Component
public class NotificacionMapper {

	// Debe coincidir con NotificadorAmqp.TIPO_SOLICITUD en chat-conversacion -- sin
	// referencia compartida entre repos, hay que mantenerlo sincronizado a mano si cambia.
	private static final String TIPO_SOLICITUD = "solicitud";

	public Notificacion toEntity(NotificacionEntrante mensaje) {
		Notificacion notificacion = new Notificacion();
		notificacion.setRemitente(mensaje.solicitante());
		notificacion.setReceptor(mensaje.solicitado());
		notificacion.setTipo(mensaje.tipo());
		notificacion.setContenido(resolverContenido(mensaje));
		notificacion.setLeida(false);
		return notificacion;
	}

	/**
	 * El contenido a mostrar todavia no llega en el mensaje (ver {@link NotificacionEntrante}),
	 * asi que hoy lo determina el propio codigo a partir del tipo; en cuanto un publicador
	 * empiece a mandarlo, se respeta el que llegue.
	 */
	private String resolverContenido(NotificacionEntrante mensaje) {
		if (mensaje.contenido() != null && !mensaje.contenido().isBlank()) {
			return mensaje.contenido();
		}
		if (TIPO_SOLICITUD.equals(mensaje.tipo())) {
			return "%s te ha enviado una solicitud de chat".formatted(mensaje.solicitante());
		}
		return "Tienes una notificacion nueva";
	}
}
