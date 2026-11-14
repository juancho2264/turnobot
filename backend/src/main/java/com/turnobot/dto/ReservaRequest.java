package com.turnobot.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para reservar. Las anotaciones validan la entrada antes de llegar al servicio:
 * si algo no cumple, Spring responde 400 automáticamente.
 * barberoId es opcional: si viene null, se asigna el primero libre.
 */
public record ReservaRequest(
        @NotNull Long servicioId,
        Long barberoId,
        @NotNull LocalDateTime inicio,
        @NotBlank @Size(max = 60) String clienteNombre,
        @NotBlank @Pattern(regexp = "\\d{8,13}", message = "debe tener entre 8 y 13 números") String clienteTelefono
) {
}
