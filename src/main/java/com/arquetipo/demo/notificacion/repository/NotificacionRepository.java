package com.arquetipo.demo.notificacion.repository;

import com.arquetipo.demo.notificacion.domain.Notificacion;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	Page<Notificacion> findByReceptor(String receptor, Pageable pageable);

	/**
	 * La mas reciente que coincida, para {@code NotificacionService#actualizar}. Dos metodos en
	 * vez de uno con {@code remitente} nulable a proposito: {@code remitente = null} en una
	 * consulta JPQL/SQL nunca hace match (semantica de SQL), asi que un {@code remitente} nulo
	 * necesita {@code IsNull} explicito, no la igualdad de siempre.
	 */
	Optional<Notificacion> findFirstByRemitenteAndReceptorAndTipoOrderByCreatedAtDesc(
			String remitente, String receptor, String tipo);

	Optional<Notificacion> findFirstByRemitenteIsNullAndReceptorAndTipoOrderByCreatedAtDesc(
			String receptor, String tipo);
}
