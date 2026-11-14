package com.turnobot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turnobot.model.Servicio;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {
}
