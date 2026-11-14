package com.turnobot.dto;

import java.util.List;

/**
 * Respuesta del bot.
 * @param texto   lo que dice el bot
 * @param opciones botones de respuesta rápida (pueden estar vacíos)
 * @param turno   el turno recién reservado, si lo hubo (para mostrar una tarjeta)
 */
public record ChatRespuesta(String texto, List<String> opciones, TurnoDTO turno) {

    public static ChatRespuesta de(String texto, List<String> opciones) {
        return new ChatRespuesta(texto, opciones, null);
    }

    public static ChatRespuesta de(String texto) {
        return new ChatRespuesta(texto, List.of(), null);
    }
}
