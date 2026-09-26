package com.arquetipo.demo.notificacion.amqp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@code NotificacionListener} recibe este record a traves de {@code Jackson2JsonMessageConverter}
 * (ObjectMapper sin configuracion propia, ver {@code RabbitMqConfig}) -- esta prueba usa el mismo
 * tipo de deserializacion para comprobar que {@code meta} (informacion adicional propia del
 * tipo, p. ej. {@code MetaSolicitud} en chat-conversacion) se captura tal cual llega, y que un
 * campo que este record no declara sigue sin romper la lectura del mensaje
 * ({@code @JsonIgnoreProperties(ignoreUnknown = true)}, defensivo ante campos futuros).
 */
class NotificacionEntranteTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void deserializa_conMeta_loCapturaComoJsonNode() throws Exception {
		String json = """
				{
				  "solicitante": "mateo",
				  "solicitado": "ana",
				  "tipo": "solicitud",
				  "meta": { "aceptada": false, "pendiente": true }
				}
				""";

		NotificacionEntrante mensaje = objectMapper.readValue(json, NotificacionEntrante.class);

		assertThat(mensaje.solicitante()).isEqualTo("mateo");
		assertThat(mensaje.solicitado()).isEqualTo("ana");
		assertThat(mensaje.tipo()).isEqualTo("solicitud");
		assertThat(mensaje.contenido()).isNull();
		assertThat(mensaje.meta()).isNotNull();
		assertThat(mensaje.meta().get("aceptada").asBoolean()).isFalse();
		assertThat(mensaje.meta().get("pendiente").asBoolean()).isTrue();
		assertThat(mensaje.meta().toString()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
	}

	@Test
	void deserializa_sinMeta_loDejaNulo() throws Exception {
		String json = """
				{
				  "solicitante": "mateo",
				  "solicitado": "ana",
				  "tipo": "solicitud"
				}
				""";

		NotificacionEntrante mensaje = objectMapper.readValue(json, NotificacionEntrante.class);

		assertThat(mensaje.meta()).isNull();
	}

	@Test
	void deserializa_conCampoDesconocidoDistintoDeMeta_loIgnoraSinFallar() throws Exception {
		String json = """
				{
				  "solicitante": "mateo",
				  "solicitado": "ana",
				  "tipo": "solicitud",
				  "otroCampoFuturo": "algo"
				}
				""";

		NotificacionEntrante mensaje = objectMapper.readValue(json, NotificacionEntrante.class);

		assertThat(mensaje.solicitante()).isEqualTo("mateo");
	}
}
