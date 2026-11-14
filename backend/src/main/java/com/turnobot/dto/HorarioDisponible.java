package com.turnobot.dto;

import java.time.LocalDateTime;

public record HorarioDisponible(LocalDateTime inicio, Long barberoId, String barberoNombre) {
}
