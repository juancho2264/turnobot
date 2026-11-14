package com.turnobot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turnobot.model.Barbero;

public interface BarberoRepository extends JpaRepository<Barbero, Long> {

    // Spring Data arma la consulta a partir del nombre: WHERE activo = true ORDER BY id
    List<Barbero> findByActivoTrueOrderById();
}
