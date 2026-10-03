package com.arquetipo.demo.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.eq;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.notificacion.amqp.dto.NotificacionActualizacionEntrante;
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

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, null, null));

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

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", "contenido a medida", null, null));

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

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, meta, null));

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

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, null, null));

		verify(repository).save(argThat(notificacion -> {
			assertThat(notificacion.getMeta()).isNull();
			return true;
		}));
	}

	@Test
	void registrar_conAvatar_loPersisteComoAvatarDelRemitente() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.registrar(new NotificacionEntrante(
				"mateo", "ana", "solicitud", null, null, "<Blobatar name=\"mateo\" />"));

		verify(repository).save(argThat(notificacion -> {
			assertThat(notificacion.getAvatarRemitente()).isEqualTo("<Blobatar name=\"mateo\" />");
			return true;
		}));
	}

	@Test
	void registrar_sinAvatarOConAvatarVacio_persisteAvatarNulo() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, null, null));
		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, null, "   "));

		verify(repository, times(2)).save(argThat(notificacion -> {
			assertThat(notificacion.getAvatarRemitente()).isNull();
			return true;
		}));
	}

	@Test
	void registrar_conAvatarMasLargoQueLaColumna_guardaLaNotificacionSinAvatar() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		String demasiadoLargo = "https://cdn.example/" + "a".repeat(Notificacion.LONGITUD_MAXIMA_AVATAR);

		service.registrar(new NotificacionEntrante("mateo", "ana", "solicitud", null, null, demasiadoLargo));

		verify(repository).save(argThat(notificacion -> {
			assertThat(notificacion.getAvatarRemitente()).isNull();
			assertThat(notificacion.getRemitente()).isEqualTo("mateo");
			assertThat(notificacion.getContenido()).isEqualTo("mateo te ha enviado una solicitud de chat");
			return true;
		}));
	}

	@Test
	void registrar_sinTipo_seDescartaSinPersistirNiConsultarRegistro() {
		service.registrar(new NotificacionEntrante("mateo", "ana", null, null, null, null));

		verify(registroClient, never()).existeUsername(any());
		verify(repository, never()).save(any());
	}

	@Test
	void registrar_sinReceptor_seDescartaSinPersistirNiConsultarRegistro() {
		service.registrar(new NotificacionEntrante("mateo", null, "solicitud", null, null, null));

		verify(registroClient, never()).existeUsername(any());
		verify(repository, never()).save(any());
	}

	@Test
	void registrar_conReceptorInexistente_seDescartaSinPersistir() {
		when(registroClient.existeUsername("fantasma")).thenReturn(false);

		service.registrar(new NotificacionEntrante("mateo", "fantasma", "solicitud", null, null, null));

		verify(repository, never()).save(any());
	}

	@Test
	void registrar_conRemitenteInexistente_seDescartaSinPersistir() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(registroClient.existeUsername("fantasma")).thenReturn(false);

		service.registrar(new NotificacionEntrante("fantasma", "ana", "solicitud", null, null, null));

		verify(repository, never()).save(any());
	}

	@Test
	void registrar_sinRemitente_noConsultaRegistroParaElRemitenteYPersiste() {
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.registrar(new NotificacionEntrante(null, "ana", "solicitud", null, null, null));

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
		notificacion.setAvatarRemitente("https://cdn.example/mateo.png");
		notificacion.setLeida(false);
		notificacion.setCreatedAt(Instant.parse("2026-09-25T20:00:00Z"));
		Pageable pageable = PageRequest.of(0, 20);
		when(repository.findByReceptor(eq("ana"), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(notificacion), pageable, 1));

		var pagina = service.listaNotificaciones("ana", pageable);

		assertThat(pagina.content()).hasSize(1);
		assertThat(pagina.content().get(0).meta()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
		assertThat(pagina.content().get(0).avatarRemitente()).isEqualTo("https://cdn.example/mateo.png");
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

	@Test
	void actualizar_conRemitenteCoincidente_actualizaContenidoYMeta() throws Exception {
		Notificacion existente = new Notificacion();
		existente.setId(1L);
		existente.setRemitente("mateo");
		existente.setReceptor("ana");
		existente.setTipo("solicitud");
		existente.setContenido("mateo te ha enviado una solicitud de chat");
		existente.setMeta("{\"aceptada\":false,\"pendiente\":true}");
		when(repository.findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc("mateo", "ana", "solicitud"))
				.thenReturn(Optional.of(existente));
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		var meta = new ObjectMapper().readTree("{\"aceptada\":true,\"pendiente\":false}");

		service.actualizar(new NotificacionActualizacionEntrante(
				"mateo", "ana", "solicitud", "mateo aceptó tu solicitud de chat", meta));

		verify(repository).save(argThat(guardada -> {
			assertThat(guardada.getContenido()).isEqualTo("mateo aceptó tu solicitud de chat");
			assertThat(guardada.getMeta()).isEqualTo("{\"aceptada\":true,\"pendiente\":false}");
			return true;
		}));
	}

	@Test
	void actualizar_sinRemitenteEnMensaje_buscaConRemitenteNulo() {
		Notificacion existente = new Notificacion();
		existente.setId(2L);
		existente.setReceptor("ana");
		existente.setTipo("sistema");
		existente.setContenido("aviso del sistema");
		when(repository.findFirstByRemitenteIsNullAndReceptorAndTipoOrderByCreatedAtDesc("ana", "sistema"))
				.thenReturn(Optional.of(existente));
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.actualizar(new NotificacionActualizacionEntrante(null, "ana", "sistema", "aviso actualizado", null));

		verify(repository).save(argThat(guardada -> {
			assertThat(guardada.getContenido()).isEqualTo("aviso actualizado");
			return true;
		}));
		verify(repository, never()).findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc(any(), any(), any());
	}

	@Test
	void actualizar_soloConMeta_dejaContenidoSinTocar() throws Exception {
		Notificacion existente = new Notificacion();
		existente.setId(1L);
		existente.setRemitente("mateo");
		existente.setReceptor("ana");
		existente.setTipo("solicitud");
		existente.setContenido("mateo te ha enviado una solicitud de chat");
		when(repository.findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc("mateo", "ana", "solicitud"))
				.thenReturn(Optional.of(existente));
		when(repository.save(any(Notificacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		var meta = new ObjectMapper().readTree("{\"aceptada\":true,\"pendiente\":false}");

		service.actualizar(new NotificacionActualizacionEntrante("mateo", "ana", "solicitud", null, meta));

		verify(repository).save(argThat(guardada -> {
			assertThat(guardada.getContenido()).isEqualTo("mateo te ha enviado una solicitud de chat");
			assertThat(guardada.getMeta()).isEqualTo("{\"aceptada\":true,\"pendiente\":false}");
			return true;
		}));
	}

	@Test
	void actualizar_sinCoincidencia_seDescartaSinPersistir() {
		when(repository.findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc("mateo", "ana", "solicitud"))
				.thenReturn(Optional.empty());

		service.actualizar(new NotificacionActualizacionEntrante("mateo", "ana", "solicitud", "algo", null));

		verify(repository, never()).save(any());
	}

	@Test
	void actualizar_sinTipo_seDescartaSinConsultarRepositorio() {
		service.actualizar(new NotificacionActualizacionEntrante("mateo", "ana", null, "algo", null));

		verify(repository, never()).findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc(any(), any(), any());
		verify(repository, never()).save(any());
	}

	@Test
	void actualizar_sinReceptor_seDescartaSinConsultarRepositorio() {
		service.actualizar(new NotificacionActualizacionEntrante("mateo", null, "solicitud", "algo", null));

		verify(repository, never()).findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc(any(), any(), any());
		verify(repository, never()).save(any());
	}
}
