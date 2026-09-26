package com.arquetipo.demo.common.exception;

/**
 * Se lanza cuando no se puede alcanzar un servicio interno del que este depende (gRPC
 * {@code UNAVAILABLE}: servicio caido, puerto equivocado, etc.).
 */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String servicio) {
		super("El servicio '%s' no esta disponible".formatted(servicio));
	}
}
