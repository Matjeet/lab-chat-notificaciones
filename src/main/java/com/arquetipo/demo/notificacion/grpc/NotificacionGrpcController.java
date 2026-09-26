package com.arquetipo.demo.notificacion.grpc;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.notificacion.service.NotificacionService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada gRPC de este servicio (hoy el unico protocolo que expone, ver CLAUDE.md).
 * Ninguno de los dos rpc reimplementa logica: delegan en {@link NotificacionService}, la misma
 * que persiste los mensajes que llegan por RabbitMQ.
 */
@Slf4j
@Component
public class NotificacionGrpcController extends NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase {

	private static final String DETALLE_ERROR_INTERNO = "Ocurrio un error inesperado. Contacte con soporte.";

	private final NotificacionService service;
	private final NotificacionGrpcMapper mapper;

	public NotificacionGrpcController(NotificacionService service, NotificacionGrpcMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Override
	public void listaNotificaciones(ListaNotificacionesRequest request,
			StreamObserver<ListaNotificacionesResponse> responseObserver) {
		log.debug(">> listaNotificaciones(receptor='{}')", request.getReceptor());
		try {
			var pagina = service.listaNotificaciones(request.getReceptor(), mapper.aPageable(request));
			responseObserver.onNext(mapper.aListaNotificacionesResponse(pagina));
			responseObserver.onCompleted();
			log.debug("<< listaNotificaciones() -> OK, totalElements={}", pagina.totalElements());
		} catch (Exception ex) {
			log.error("Excepcion no controlada en el endpoint gRPC de lista de notificaciones. receptor='{}'",
					request.getReceptor(), ex);
			log.debug("<< listaNotificaciones() -> INTERNAL");
			responseObserver.onError(
					Status.INTERNAL.withDescription(DETALLE_ERROR_INTERNO).asRuntimeException());
		}
	}

	@Override
	public void actualizarLeida(ActualizarLeidaRequest request, StreamObserver<NotificacionItem> responseObserver) {
		log.debug(">> actualizarLeida(id={}, leida={})", request.getId(), request.getLeida());
		try {
			var respuesta = service.actualizarLeida(request.getId(), request.getLeida());
			responseObserver.onNext(mapper.aNotificacionItem(respuesta));
			responseObserver.onCompleted();
			log.debug("<< actualizarLeida() -> OK, leida={}", respuesta.leida());
		} catch (ResourceNotFoundException ex) {
			log.debug("<< actualizarLeida() -> NOT_FOUND");
			responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
		} catch (Exception ex) {
			log.error("Excepcion no controlada en el endpoint gRPC de actualizacion de leida. id={}",
					request.getId(), ex);
			log.debug("<< actualizarLeida() -> INTERNAL");
			responseObserver.onError(
					Status.INTERNAL.withDescription(DETALLE_ERROR_INTERNO).asRuntimeException());
		}
	}
}
