package com.turnobot.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lee el bloque "negocio:" de application.yml y lo convierte en un objeto tipado.
 * Ventaja: si alguien escribe mal una hora, la app falla al arrancar (y no en
 * medio de una reserva), y en el código usamos LocalTime en vez de Strings.
 */
@ConfigurationProperties(prefix = "negocio")
public record NegocioProperties(
        String nombre,
        String direccion,
        ZoneId zonaHoraria,
        LocalTime apertura,
        LocalTime cierre,
        Set<DayOfWeek> diasLaborales,
        int intervaloMinutos,
        int anticipacionMinimaMinutos,
        int diasDeReserva
) {
    public boolean abre(DayOfWeek dia) {
        return diasLaborales.contains(dia);
    }
}
