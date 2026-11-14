package com.turnobot.exceptions;

/** Se lanza cuando el pedido es válido pero el negocio no lo permite (ej. horario ocupado). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
