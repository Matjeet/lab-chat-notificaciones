package com.arquetipo.demo.notificacion.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.notificacion.service.NotificacionService;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba los endpoints gRPC de este servicio sobre un servidor in-process (sin red real) --
 * mismo patron que {@code ConversacionGrpcControllerTest} en chat-conversacion.
 * {@link NotificacionService} va mockeado.
 */
class NotificacionGrpcControllerTest {

	private NotificacionService service;
	private Server server;
	private ManagedChannel channel;
	private NotificacionGrpcServiceGrpc.NotificacionGrpcServiceBlockingStub stub;

	@BeforeEach
	void iniciarServidorInProcess() throws Exception {
		String nombreServidor = "notificacion-grpc-test-" + System.nanoTime();
		service = mock(NotificacionService.class);
		NotificacionGrpcController controller = new NotificacionGrpcController(service, new NotificacionGrpcMapper());

		server = InProcessServerBuilder.forName(nombreServidor)
				.directExecutor()
				.addService(controller)
				.build()
				.start();
		channel = InProcessChannelBuilder.forName(nombreServidor).directExecutor().build();
		stub = NotificacionGrpcServiceGrpc.newBlockingStub(channel);
	}

	@AfterEach
	void detenerServidor() throws Exception {
		channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
	}

	@Test
	void listaNotificaciones_delegaEnElServicioYMapeaLaPagina() {
		NotificacionResponse notificacion = new NotificacionResponse(
				1L, "mateo", "solicitud", false, Instant.parse("2026-09-25T20:00:00Z"), "{\"aceptada\":false,\"pendiente\":true}");
		PageResponse<NotificacionResponse> pagina =
				new PageResponse<>(List.of(notificacion), 0, 20, 1, 1, true, true, false);
		when(service.listaNotificaciones(eq("ana"), any())).thenReturn(pagina);

		ListaNotificacionesResponse respuesta = stub.listaNotificaciones(
				ListaNotificacionesRequest.newBuilder().setReceptor("ana").build());

		assertThat(respuesta.getContentCount()).isEqualTo(1);
		assertThat(respuesta.getContent(0).getId()).isEqualTo(1L);
		assertThat(respuesta.getContent(0).getRemitente()).isEqualTo("mateo");
		assertThat(respuesta.getContent(0).getTipo()).isEqualTo("solicitud");
		assertThat(respuesta.getContent(0).getLeida()).isFalse();
		assertThat(respuesta.getContent(0).getCreatedAt()).isEqualTo("2026-09-25T20:00:00Z");
		assertThat(respuesta.getContent(0).getMeta()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
		assertThat(respuesta.getTotalElements()).isEqualTo(1);
		assertThat(respuesta.getFirst()).isTrue();
	}

	@Test
	void listaNotificaciones_sinRemitenteNiMeta_noTraeEsosCampos() {
		NotificacionResponse notificacion = new NotificacionResponse(
				2L, null, "sistema", true, Instant.parse("2026-09-25T20:00:00Z"), null);
		PageResponse<NotificacionResponse> pagina =
				new PageResponse<>(List.of(notificacion), 0, 20, 1, 1, true, true, false);
		when(service.listaNotificaciones(eq("ana"), any())).thenReturn(pagina);

		ListaNotificacionesResponse respuesta = stub.listaNotificaciones(
				ListaNotificacionesRequest.newBuilder().setReceptor("ana").build());

		assertThat(respuesta.getContent(0).hasRemitente()).isFalse();
		assertThat(respuesta.getContent(0).hasMeta()).isFalse();
	}

	@Test
	void listaNotificaciones_sinNotificaciones_devuelveVacia() {
		PageResponse<NotificacionResponse> pagina = new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, true);
		when(service.listaNotificaciones(eq("fantasma"), any())).thenReturn(pagina);

		ListaNotificacionesResponse respuesta = stub.listaNotificaciones(
				ListaNotificacionesRequest.newBuilder().setReceptor("fantasma").build());

		assertThat(respuesta.getContentCount()).isZero();
		assertThat(respuesta.getEmpty()).isTrue();
	}

	@Test
	void actualizarLeida_conIdExistente_delegaEnElServicioYDevuelveElItemActualizado() {
		NotificacionResponse actualizada = new NotificacionResponse(
				1L, "mateo", "solicitud", true, Instant.parse("2026-09-25T20:00:00Z"), "{\"aceptada\":false,\"pendiente\":true}");
		when(service.actualizarLeida(1L, true)).thenReturn(actualizada);

		NotificacionItem respuesta = stub.actualizarLeida(
				ActualizarLeidaRequest.newBuilder().setId(1L).setLeida(true).build());

		assertThat(respuesta.getId()).isEqualTo(1L);
		assertThat(respuesta.getLeida()).isTrue();
		assertThat(respuesta.getMeta()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
	}

	@Test
	void actualizarLeida_conIdInexistente_devuelveNotFound() {
		when(service.actualizarLeida(99L, true))
				.thenThrow(new ResourceNotFoundException("Notificacion", 99L));

		StatusRuntimeException excepcion = catchStatusRuntimeException(() -> stub.actualizarLeida(
				ActualizarLeidaRequest.newBuilder().setId(99L).setLeida(true).build()));

		assertThat(excepcion.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
	}

	private static StatusRuntimeException catchStatusRuntimeException(Runnable llamada) {
		try {
			llamada.run();
		} catch (StatusRuntimeException ex) {
			return ex;
		}
		throw new AssertionError("Se esperaba un StatusRuntimeException y no se lanzo ninguno");
	}
}
