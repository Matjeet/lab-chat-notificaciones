package com.arquetipo.demo.notificacion.amqp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@code NotificacionListener} recibe este record a traves de {@code Jackson2JsonMessageConverter}
 * (ObjectMapper sin configuracion propia, ver {@code RabbitMqConfig}) -- esta prueba usa el mismo
 * tipo de deserializacion para comprobar que un campo que este record no declara (como
 * {@code meta}, que ya manda chat-conversacion) no rompe la lectura del mensaje.
 */
class NotificacionEntranteTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void deserializa_conCampoDesconocidoMeta_loIgnoraSinFallar() throws Exception {
		String json = """
				{
				  "solicitante": "mateo",
				  "solicitado": "ana",
				  "tipo": "solicitud",
				  "meta": { "aceptada": false }
				}
				""";

		NotificacionEntrante mensaje = objectMapper.readValue(json, NotificacionEntrante.class);

		assertThat(mensaje.solicitante()).isEqualTo("mateo");
		assertThat(mensaje.solicitado()).isEqualTo("ana");
		assertThat(mensaje.tipo()).isEqualTo("solicitud");
		assertThat(mensaje.contenido()).isNull();
	}
}
