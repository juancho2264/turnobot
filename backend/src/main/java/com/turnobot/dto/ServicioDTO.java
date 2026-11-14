package com.turnobot.dto;

import com.turnobot.model.Servicio;

public record ServicioDTO(Long id, String nombre, int duracionMinutos, int precio) {

    public static ServicioDTO de(Servicio s) {
        return new ServicioDTO(s.getId(), s.getNombre(), s.getDuracionMinutos(), s.getPrecio());
    }
}
