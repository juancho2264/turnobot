package com.turnobot.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turnobot.dto.CambioEstadoRequest;
import com.turnobot.dto.EstadisticasDTO;
import com.turnobot.dto.TurnoDTO;
import com.turnobot.service.EstadisticasService;
import com.turnobot.service.TurnoService;

import jakarta.validation.Valid;

/** Endpoints del panel del dueño. Todos requieren token (ver SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TurnoService turnos;
    private final EstadisticasService estadisticas;

    public AdminController(TurnoService turnos, EstadisticasService estadisticas) {
        this.turnos = turnos;
        this.estadisticas = estadisticas;
    }

    @GetMapping("/turnos")
    public List<TurnoDTO> turnosDelDia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return turnos.delDia(fecha);
    }

    @PatchMapping("/turnos/{id}/estado")
    public TurnoDTO cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest pedido) {
        return turnos.cambiarEstado(id, pedido.estado());
    }

    @GetMapping("/estadisticas")
    public EstadisticasDTO estadisticas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return estadisticas.calcular(fecha);
    }
}
