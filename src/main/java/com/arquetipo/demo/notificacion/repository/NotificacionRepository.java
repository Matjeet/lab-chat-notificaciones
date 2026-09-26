package com.arquetipo.demo.notificacion.repository;

import com.arquetipo.demo.notificacion.domain.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	Page<Notificacion> findByReceptor(String receptor, Pageable pageable);
}
