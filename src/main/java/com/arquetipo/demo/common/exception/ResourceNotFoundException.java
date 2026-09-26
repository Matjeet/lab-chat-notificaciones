package com.arquetipo.demo.common.exception;

/**
 * Se lanza cuando no existe el recurso solicitado. El controller gRPC la traduce a
 * {@code NOT_FOUND} (ver {@code NotificacionGrpcController}).
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public ResourceNotFoundException(String resourceName, Object id) {
		super("%s no encontrado con id %s".formatted(resourceName, id));
	}
}
