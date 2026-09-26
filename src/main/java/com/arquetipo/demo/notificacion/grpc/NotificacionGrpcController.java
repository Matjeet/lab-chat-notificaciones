package com.arquetipo.demo.notificacion.grpc;

import com.arquetipo.demo.notificacion.service.NotificacionService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada gRPC de este servicio (hoy el unico protocolo que expone, ver CLAUDE.md).
 * {@code ListaNotificaciones} no reimplementa logica: delega en {@link NotificacionService},
 * la misma que persiste los mensajes que llegan por RabbitMQ.
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
}
