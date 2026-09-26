package com.arquetipo.demo.notificacion.grpc;

import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Traduce entre los mensajes de {@code notificacion.proto} y los DTO del dominio, mismo patron
 * que {@code ConversacionGrpcMapper} en chat-conversacion (paginacion pagina/offset, no cursor
 * -- este servicio es JPA/MySQL, no la agregacion de Mongo que motivo el cursor de ListaChats).
 */
@Component
public class NotificacionGrpcMapper {

	static final int TAMANO_PAGINA_DEFECTO = 20;
	static final int TAMANO_PAGINA_MAXIMO = 100;
	private static final String CAMPO_ORDEN_DEFECTO = "createdAt";

	Pageable aPageable(ListaNotificacionesRequest request) {
		int page = Math.max(request.getPage(), 0);
		return PageRequest.of(page, tamanoPagina(request.getSize()), aSort(request.getSort()));
	}

	private static int tamanoPagina(int size) {
		return size <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(size, TAMANO_PAGINA_MAXIMO);
	}

	private Sort aSort(String valor) {
		if (valor == null || valor.isBlank()) {
			// Mas reciente primero: es el orden natural para una lista de notificaciones (a
			// diferencia del historial de chat, que por defecto es cronologico ascendente).
			return Sort.by(Sort.Direction.DESC, CAMPO_ORDEN_DEFECTO);
		}
		String[] partes = valor.split(",", 2);
		String campo = partes[0].trim();
		Sort.Direction direccion = partes.length > 1 && "desc".equalsIgnoreCase(partes[1].trim())
				? Sort.Direction.DESC
				: Sort.Direction.ASC;
		return Sort.by(direccion, campo.isEmpty() ? CAMPO_ORDEN_DEFECTO : campo);
	}

	NotificacionItem aNotificacionItem(NotificacionResponse response) {
		NotificacionItem.Builder builder = NotificacionItem.newBuilder()
				.setTipo(response.tipo())
				.setLeida(response.leida())
				.setCreatedAt(response.createdAt().toString());
		if (response.remitente() != null) {
			builder.setRemitente(response.remitente());
		}
		return builder.build();
	}

	ListaNotificacionesResponse aListaNotificacionesResponse(PageResponse<NotificacionResponse> pagina) {
		ListaNotificacionesResponse.Builder builder = ListaNotificacionesResponse.newBuilder()
				.setPage(pagina.page())
				.setSize(pagina.size())
				.setTotalElements(pagina.totalElements())
				.setTotalPages(pagina.totalPages())
				.setFirst(pagina.first())
				.setLast(pagina.last())
				.setEmpty(pagina.empty());
		pagina.content().forEach(notificacion -> builder.addContent(aNotificacionItem(notificacion)));
		return builder.build();
	}
}
