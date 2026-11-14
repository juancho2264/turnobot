package com.turnobot.dto;

import com.turnobot.model.Barbero;

public record BarberoDTO(Long id, String nombre) {

    public static BarberoDTO de(Barbero b) {
        return new BarberoDTO(b.getId(), b.getNombre());
    }
}
