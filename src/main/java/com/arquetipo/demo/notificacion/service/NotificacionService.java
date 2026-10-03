package com.arquetipo.demo.notificacion.service;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.notificacion.amqp.dto.NotificacionActualizacionEntrante;
import com.arquetipo.demo.notificacion.amqp.dto.NotificacionEntrante;
import com.arquetipo.demo.notificacion.domain.Notificacion;
import com.arquetipo.demo.notificacion.mapper.NotificacionMapper;
import com.arquetipo.demo.notificacion.repository.NotificacionRepository;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import java.util.Optional;
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
 * {@code leida} ({@link #actualizarLeida}), usadas por {@code NotificacionGrpcController}; y
 * la actualizacion de {@code contenido}/{@code meta} de una notificacion ya existente
 * ({@link #actualizar}), usada por {@code NotificacionActualizacionListener} -- llega por una
 * cola de RabbitMQ distinta de la de {@link #registrar} (ver
 * {@code common.config.RabbitMqConfig}), pensada solo para actualizar, no para crear.
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
		if (notificacion.getAvatarRemitente() != null
				&& notificacion.getAvatarRemitente().length() > Notificacion.LONGITUD_MAXIMA_AVATAR) {
			// chat-registro ya limita el avatar a este tamano, asi que esto no deberia pasar. Sin
			// este guardia, un avatar mas largo reventaria el insert (columna VARCHAR(500)) y,
			// como el listener no atrapa la excepcion, RabbitMQ reencolaria el mensaje para
			// siempre. Se descarta solo el avatar (nunca el recorte: una etiqueta cortada es
			// invalida), no la notificacion entera. Sin el valor en el log, a proposito.
			log.warn("Avatar del remitente descartado: supera {} caracteres. tipo='{}' receptor='{}'",
					Notificacion.LONGITUD_MAXIMA_AVATAR, mensaje.tipo(), mensaje.solicitado());
			notificacion.setAvatarRemitente(null);
		}
		Notificacion guardada = repository.save(notificacion);
		log.debug("<< registrar() -> OK, id={}", guardada.getId());
	}

	/**
	 * Actualiza {@code contenido} y/o {@code meta} de la notificacion mas reciente cuyo
	 * remitente/receptor/tipo coincidan con el mensaje (mismo vocabulario que
	 * {@link NotificacionEntrante}: {@code solicitante} es el remitente, {@code solicitado} el
	 * receptor). No crea nada: a diferencia de {@link #registrar}, esta cola es solo para
	 * actualizar. Un mensaje sin {@code tipo}/receptor, o que no coincide con ninguna
	 * notificacion existente, se descarta igual que en {@code registrar} -- se loguea y no se
	 * persiste ni se relanza nada.
	 */
	public void actualizar(NotificacionActualizacionEntrante mensaje) {
		log.debug(">> actualizar(tipo='{}', solicitado='{}')", mensaje.tipo(), mensaje.solicitado());

		if (mensaje.tipo() == null || mensaje.tipo().isBlank()) {
			log.warn("Actualizacion descartada: llego sin 'tipo'. solicitado='{}'", mensaje.solicitado());
			log.debug("<< actualizar() -> descartada (sin tipo)");
			return;
		}
		if (mensaje.solicitado() == null || mensaje.solicitado().isBlank()) {
			log.warn("Actualizacion descartada: llego sin receptor. tipo='{}'", mensaje.tipo());
			log.debug("<< actualizar() -> descartada (sin receptor)");
			return;
		}

		Optional<Notificacion> existente = buscarNotificacionParaActualizar(mensaje);
		if (existente.isEmpty()) {
			log.warn("Actualizacion descartada: no hay ninguna notificacion que coincida. "
					+ "receptor='{}' tipo='{}'", mensaje.solicitado(), mensaje.tipo());
			log.debug("<< actualizar() -> descartada (no existe ninguna que coincida)");
			return;
		}

		Notificacion notificacion = existente.get();
		if (mensaje.contenido() != null && !mensaje.contenido().isBlank()) {
			notificacion.setContenido(mensaje.contenido());
		}
		if (mensaje.meta() != null) {
			notificacion.setMeta(mensaje.meta().toString());
		}
		Notificacion guardada = repository.save(notificacion);
		log.debug("<< actualizar() -> OK, id={}", guardada.getId());
	}

	private Optional<Notificacion> buscarNotificacionParaActualizar(NotificacionActualizacionEntrante mensaje) {
		if (mensaje.solicitante() == null || mensaje.solicitante().isBlank()) {
			return repository.findFirstByRemitenteIsNullAndReceptorAndTipoOrderByCreatedAtDesc(
					mensaje.solicitado(), mensaje.tipo());
		}
		return repository.findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc(
				mensaje.solicitante(), mensaje.solicitado(), mensaje.tipo());
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
