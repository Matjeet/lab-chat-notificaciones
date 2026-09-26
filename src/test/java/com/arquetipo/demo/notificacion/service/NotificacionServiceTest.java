package com.arquetipo.demo.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.eq;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.notificacion.amqp.dto.NotificacionEntrante;
import com.arquetipo.demo.notificacion.domain.Notificacion;
import com.arquetipo.demo.notificacion.mapper.NotificacionMapper;
import com.arquetipo.demo.notificacion.repository.NotificacionRepository;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Prueba las reglas de negocio de {@link NotificacionService} de forma aislada, con
 * {@link NotificacionRepository} y {@link RegistroGrpcClient} mockeados (mismo patron que
 * {@code SolicitudChatServiceTest} en chat-conversacion) y el mapper real (es puro, sin
 * dependencias).
 */
class NotificacionServiceTest {

	private NotificacionRepository repository;
	private RegistroGrpcClient registroClient;
	private NotificacionService service;

	@BeforeEach
	void iniciar() {
		repository = mock(NotificacionRepository.class);
		registroClient = mock(RegistroGrpcClient.class);
		service = new NotificacionService(repository, new NotificacionMapper(), registroClient);
	}

	@Test
	void registrar_solicitudConUsuariosValidos_persisteConContenidoPredeterminado() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> {
			Notificacion notificacion = invocacion.getArgument(0);
			notificacion.setId(1L);
			return notificacion;
		});

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, null));

		verify(repository).save(argThat(notificacion -> {
			assertThat(notificacion.getRemitente()).isEqualTo("mateo");
			assertThat(notificacion.getReceptor()).isEqualTo("ana");
			assertThat(notificacion.getTipo()).isEqualTo("solicitud");
			assertThat(notificacion.getContenido()).isEqualTo("mateo te ha enviado una solicitud de chat");
			assertThat(notificacion.isLeida()).isFalse();
			return true;
		}));
	}

	@Test
	void registrar_conContenidoYaEnElMensaje_respetaEseContenido() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", "contenido a medida", null));

		verify(repository).save(argThat(notificacion -> {
			assertThat(notificacion.getContenido()).isEqualTo("contenido a medida");
			return true;
		}));
	}

	@Test
	void registrar_conMeta_persisteElJsonComoTexto() throws Exception {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		var meta = new ObjectMapper().readTree("{\"aceptada\":false,\"pendiente\":true}");

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, meta));

		verify(repository).save(argThat(notificacion -> {
			assertThat(notificacion.getMeta()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
			return true;
		}));
	}

	@Test
	void registrar_sinMeta_persisteMetaNulo() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, null));

		verify(repository).save(argThat(notificacion -> {
			assertThat(notificacion.getMeta()).isNull();
			return true;
		}));
	}

	@Test
	void registrar_sinTipo_seDescartaSinPersistirNiConsultarRegistro() {
		service.registrar(new NotificacionEntrante("mateo", "ana", null, null, null));

		verify(registroClient, never()).existeUsername(any());
		verify(repository, never()).save(any());
	}

	@Test
	void registrar_sinReceptor_seDescartaSinPersistirNiConsultarRegistro() {
		service.registrar(new NotificacionEntrante("mateo", null, "solicitud", null, null));

		verify(registroClient, never()).existeUsername(any());
		verify(repository, never()).save(any());
	}

	@Test
	void registrar_conReceptorInexistente_seDescartaSinPersistir() {
		when(registroClient.existeUsername("fantasma")).thenReturn(false);

		service.registrar(new NotificacionEntrante("mateo", "fantasma", "solicitud", null, null));

		verify(repository, never()).save(any());
	}

	@Test
	void registrar_conRemitenteInexistente_seDescartaSinPersistir() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("fantasma")).thenReturn(false);

		service.registrar(new NotificacionEntrante("fantasma", "ana", "solicitud", null, null));

		verify(repository, never()).save(any());
	}

	@Test
	void registrar_sinRemitente_noConsultaRegistroParaElRemitenteYPersiste() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.registrar(new NotificacionEntrante(null, "ana", "solicitud", null, null));

		verify(repository).save(any());
	}

	@Test
	void listaNotificaciones_delegaEnElRepositorioYMapeaLaPagina() {
		Notificacion notificacion = new Notificacion();
		notificacion.setId(1L);
		notificacion.setRemitente("mateo");
		notificacion.setReceptor("ana");
		notificacion.setTipo("solicitud");
		notificacion.setContenido("mateo te ha enviado una solicitud de chat");
		notificacion.setMeta("{\"aceptada\":false,\"pendiente\":true}");
		notificacion.setLeida(false);
		notificacion.setCreatedAt(Instant.parse("2026-09-25T20:00:00Z"));
		Pageable pageable = PageRequest.of(0, 20);
		when(repository.findByReceptor(eq("ana"), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(notificacion), pageable, 1));

		var pagina = service.listaNotificaciones("ana", pageable);

		assertThat(pagina.content()).hasSize(1);
		assertThat(pagina.content().get(0).meta()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
		assertThat(pagina.totalElements()).isEqualTo(1);
	}

	@Test
	void listaNotificaciones_sinNotificaciones_devuelveVacia() {
		Pageable pageable = PageRequest.of(0, 20);
		when(repository.findByReceptor(eq("fantasma"), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		PageResponse<?> pagina = service.listaNotificaciones("fantasma", pageable);

		assertThat(pagina.content()).isEmpty();
		assertThat(pagina.empty()).isTrue();
		verify(registroClient, never()).existeUsername(any());
	}

	@Test
	void actualizarLeida_conIdExistente_actualizaYPersiste() {
		Notificacion notificacion = new Notificacion();
		notificacion.setId(1L);
		notificacion.setRemitente("mateo");
		notificacion.setReceptor("ana");
		notificacion.setTipo("solicitud");
		notificacion.setContenido("mateo te ha enviado una solicitud de chat");
		notificacion.setLeida(false);
		notificacion.setCreatedAt(Instant.parse("2026-09-25T20:00:00Z"));
		when(repository.findById(1L)).thenReturn(Optional.of(notificacion));
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		var respuesta = service.actualizarLeida(1L, true);

		assertThat(respuesta.leida()).isTrue();
		verify(repository).save(argThat(guardada -> {
			assertThat(guardada.isLeida()).isTrue();
			return true;
		}));
	}

	@Test
	void actualizarLeida_conIdInexistente_lanzaResourceNotFoundExceptionSinPersistir() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.actualizarLeida(99L, true))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(repository, never()).save(any());
	}
}
