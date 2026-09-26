package com.arquetipo.demo.notificacion.service;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.notificacion.amqp.dto.NotificacionEntrante;
import com.arquetipo.demo.notificacion.domain.Notificacion;
import com.arquetipo.demo.notificacion.mapper.NotificacionMapper;
import com.arquetipo.demo.notificacion.repository.NotificacionRepository;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida y persiste las notificaciones que llegan por RabbitMQ (ver
 * {@code com.arquetipo.demo.notificacion.amqp.NotificacionListener}).
 *
 * <p>Un mensaje sin {@code tipo} o sin receptor, o cuyo remitente/receptor no exista en
 * chat-registro, se descarta -- se registra en el log y no se persiste ni se relanza nada, no
 * es un fallo transitorio y reintentarlo no lo arreglaria. Si en cambio chat-registro no
 * responde ({@link RegistroGrpcClient} lanza {@code ServiceUnavailableException}), este
 * metodo la deja propagar sin atraparla: el listener no la atrapa tampoco, asi que RabbitMQ
 * reencola el mensaje para reintentarlo mas tarde (comportamiento por defecto del contenedor
 * ante una excepcion no manejada).
 *
 * <p>Tambien expone la lectura ({@link #listaNotificaciones}) y la actualizacion del booleano
 * {@code leida} ({@link #actualizarLeida}), usadas por {@code NotificacionGrpcController}.
 */
@Slf4j
@Service
@Transactional
public class NotificacionService {

	private final NotificacionRepository repository;
	private final NotificacionMapper mapper;
	private final RegistroGrpcClient registroClient;

	public NotificacionService(NotificacionRepository repository, NotificacionMapper mapper,
			RegistroGrpcClient registroClient) {
		this.repository = repository;
		this.mapper = mapper;
		this.registroClient = registroClient;
	}

	public void registrar(NotificacionEntrante mensaje) {
		log.debug(">> registrar(tipo='{}', solicitado='{}')", mensaje.tipo(), mensaje.solicitado());

		if (mensaje.tipo() == null || mensaje.tipo().isBlank()) {
			log.warn("Notificacion descartada: llego sin 'tipo'. solicitado='{}'", mensaje.solicitado());
			log.debug("<< registrar() -> descartada (sin tipo)");
			return;
		}
		if (mensaje.solicitado() == null || mensaje.solicitado().isBlank()) {
			log.warn("Notificacion descartada: llego sin receptor. tipo='{}'", mensaje.tipo());
			log.debug("<< registrar() -> descartada (sin receptor)");
			return;
		}
		if (!registroClient.existeUsername(mensaje.solicitado())) {
			log.warn("Notificacion descartada: el receptor '{}' no existe en chat-registro", mensaje.solicitado());
			log.debug("<< registrar() -> descartada (receptor inexistente)");
			return;
		}
		if (mensaje.solicitante() != null && !mensaje.solicitante().isBlank()
				&& !registroClient.existeUsername(mensaje.solicitante())) {
			log.warn("Notificacion descartada: el remitente '{}' no existe en chat-registro", mensaje.solicitante());
			log.debug("<< registrar() -> descartada (remitente inexistente)");
			return;
		}

		Notificacion notificacion = mapper.toEntity(mensaje);
		Notificacion guardada = repository.save(notificacion);
		log.debug("<< registrar() -> OK, id={}", guardada.getId());
	}

	/**
	 * Notificaciones de {@code receptor}, paginadas. No valida que {@code receptor} exista en
	 * chat-registro (igual que el historial/lista de chats de chat-conversacion): un receptor
	 * que no existe, o que no tiene notificaciones, simplemente devuelve una pagina vacia.
	 */
	@Transactional(readOnly = true)
	public PageResponse<NotificacionResponse> listaNotificaciones(String receptor, Pageable pageable) {
		log.debug(">> listaNotificaciones(receptor='{}')", receptor);
		Page<NotificacionResponse> pagina = repository.findByReceptor(receptor, pageable)
				.map(mapper::toResponse);
		PageResponse<NotificacionResponse> respuesta = PageResponse.from(pagina);
		log.debug("<< listaNotificaciones() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}

	/**
	 * Actualiza el booleano {@code leida} de una notificacion existente.
	 *
	 * @throws ResourceNotFoundException si no existe una notificacion con ese id
	 */
	public NotificacionResponse actualizarLeida(Long id, boolean leida) {
		log.debug(">> actualizarLeida(id={}, leida={})", id, leida);
		Notificacion notificacion = repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
		notificacion.setLeida(leida);
		Notificacion guardada = repository.save(notificacion);
		NotificacionResponse respuesta = mapper.toResponse(guardada);
		log.debug("<< actualizarLeida() -> OK, leida={}", respuesta.leida());
		return respuesta;
	}
}
