package com.turnobot.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * En vez de llamar a LocalDateTime.now() por todos lados, pedimos un Clock.
 * ¿Por qué? En los tests podemos "congelar" la fecha (ej. un martes a las 9:00)
 * y verificar resultados exactos. Con now() los tests darían distinto cada día.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(NegocioProperties negocio) {
        return Clock.system(negocio.zonaHoraria());
    }
}
