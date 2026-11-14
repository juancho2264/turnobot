package com.turnobot.dto;

import com.turnobot.model.EstadoTurno;

import jakarta.validation.constraints.NotNull;

public record CambioEstadoRequest(@NotNull EstadoTurno estado) {
}
