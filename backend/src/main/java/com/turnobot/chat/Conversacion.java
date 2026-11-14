package com.turnobot.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.turnobot.chat.Interprete.Franja;
import com.turnobot.dto.HorarioDisponible;

/**
 * Lo que el bot "recuerda" de una conversación: en qué paso está y qué datos ya juntó.
 * Es una máquina de estados: cada mensaje puede completar datos y mover el paso.
 */
class Conversacion {

    enum Paso {
        INICIO,
        RESERVANDO,
        PIDIENDO_NOMBRE,
        PIDIENDO_TELEFONO,
        CONFIRMANDO,
        CANCELANDO_CODIGO,
        CANCELANDO_TELEFONO
    }

    Paso paso = Paso.INICIO;

    // Datos de la reserva que se van completando
    Long servicioId;
    Long barberoId;
    LocalDate fecha;
    Franja franja;
    LocalTime hora;
    HorarioDisponible horarioElegido;
    String nombre;
    String telefono;

    // Datos de una cancelación
    Long codigoACancelar;

    Instant ultimaActividad = Instant.now();

    void reiniciar() {
        paso = Paso.INICIO;
        servicioId = null;
        barberoId = null;
        fecha = null;
        franja = null;
        hora = null;
        horarioElegido = null;
        codigoACancelar = null;
        // nombre y teléfono se conservan: si reserva otro turno, no se los volvemos a pedir
    }

    /**
     * Si cambia el día, el servicio o el barbero, el horario ya elegido deja de valer.
     * La hora pedida ("a las 7") se conserva: es una preferencia del cliente, no un turno.
     */
    void olvidarHorario() {
        horarioElegido = null;
    }
}
