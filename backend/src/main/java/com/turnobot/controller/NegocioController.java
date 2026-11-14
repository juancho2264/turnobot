package com.turnobot.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turnobot.config.NegocioProperties;
import com.turnobot.dto.BarberoDTO;
import com.turnobot.dto.HorarioDisponible;
import com.turnobot.dto.NegocioDTO;
import com.turnobot.dto.ServicioDTO;
import com.turnobot.repository.BarberoRepository;
import com.turnobot.repository.ServicioRepository;
import com.turnobot.service.DisponibilidadService;

/** Datos públicos del negocio: los usa el frontend para armar la pantalla. */
@RestController
@RequestMapping("/api/negocio")
public class NegocioController {

    private final NegocioProperties negocio;
    private final ServicioRepository servicios;
    private final BarberoRepository barberos;
    private final DisponibilidadService disponibilidad;

    public NegocioController(NegocioProperties negocio, ServicioRepository servicios,
                             BarberoRepository barberos, DisponibilidadService disponibilidad) {
        this.negocio = negocio;
        this.servicios = servicios;
        this.barberos = barberos;
        this.disponibilidad = disponibilidad;
    }

    @GetMapping
    public NegocioDTO datos() {
        return new NegocioDTO(
                negocio.nombre(),
                negocio.direccion(),
                negocio.apertura(),
                negocio.cierre(),
                negocio.diasLaborales(),
                servicios.findAll().stream().map(ServicioDTO::de).toList(),
                barberos.findByActivoTrueOrderById().stream().map(BarberoDTO::de).toList());
    }

    @GetMapping("/disponibilidad")
    public List<HorarioDisponible> disponibilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam Long servicioId,
            @RequestParam(required = false) Long barberoId) {
        return disponibilidad.horariosLibres(fecha, servicioId, barberoId);
    }
}
