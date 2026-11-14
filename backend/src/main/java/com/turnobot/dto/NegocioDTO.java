package com.turnobot.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record NegocioDTO(
        String nombre,
        String direccion,
        LocalTime apertura,
        LocalTime cierre,
        Set<DayOfWeek> diasLaborales,
        List<ServicioDTO> servicios,
        List<BarberoDTO> barberos
) {
}
