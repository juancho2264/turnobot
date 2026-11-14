package com.turnobot.chat;

import com.turnobot.dto.ChatRespuesta;

/**
 * Cualquier "cerebro" del chat implementa esta interfaz.
 *
 * Hoy existe ReglasChatEngine (gratis, basado en reglas). Mañana se puede sumar
 * un ClaudeChatEngine con IA real sin tocar el controller ni el frontend:
 * solo cambia qué implementación inyecta Spring. Eso es programar contra interfaces.
 */
public interface ChatEngine {

    ChatRespuesta responder(String sesionId, String mensaje);

    /** Nombre del modo, para mostrarlo en la interfaz ("demo" o "ia"). */
    String modo();
}
