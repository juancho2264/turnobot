package com.turnobot.dto;

import java.time.LocalDateTime;

import com.turnobot.model.EstadoTurno;
import com.turnobot.model.Turno;

/**
 * Lo que la API devuelve de un turno. Nunca devolvemos la entidad JPA directo:
 * expondríamos campos internos y cualquier cambio en la base rompería el frontend.
 */
public record TurnoDTO(
        Long id,
        Long barberoId,
        String barberoNombre,
        Long servicioId,
        String servicioNombre,
        int precio,
        String clienteNombre,
        String clienteTelefono,
        LocalDateTime inicio,
        LocalDateTime fin,
        EstadoTurno estado
) {
    public static TurnoDTO de(Turno t) {
        return new TurnoDTO(
                t.getId(),
                t.getBarbero().getId(),
                t.getBarbero().getNombre(),
                t.getServicio().getId(),
                t.getServicio().getNombre(),
                t.getServicio().getPrecio(),
                t.getClienteNombre(),
                t.getClienteTelefono(),
                t.getInicio(),
                t.getFin(),
                t.getEstado());
    }
}
