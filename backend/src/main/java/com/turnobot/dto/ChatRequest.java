package com.turnobot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 64) String sesionId,
        @NotBlank @Size(max = 500) String mensaje
) {
}
