package com.turnobot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.turnobot.chat.ChatEngine;
import com.turnobot.dto.ChatRespuesta;
import com.turnobot.dto.HorarioDisponible;
import com.turnobot.dto.ReservaRequest;
import com.turnobot.exceptions.ReglaNegocioException;
import com.turnobot.model.Barbero;
import com.turnobot.model.Servicio;
import com.turnobot.repository.BarberoRepository;
import com.turnobot.repository.ServicioRepository;
import com.turnobot.repository.TurnoRepository;
import com.turnobot.service.DisponibilidadService;
import com.turnobot.service.TurnoService;

/**
 * Tests de integración: levantan Spring con la base H2 y prueban servicios y chat juntos.
 * El reloj está congelado en el martes 17/11/2026 a las 9:00, así los resultados son siempre iguales.
 */
@SpringBootTest
@ActiveProfiles("test")
class TurnosYChatTest {

    static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    static final LocalDate MARTES = LocalDate.of(2026, 11, 17);

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojDeTest() {
            return Clock.fixed(ZonedDateTime.of(MARTES.atTime(9, 0), ZONA).toInstant(), ZONA);
        }
    }

    @Autowired DisponibilidadService disponibilidad;
    @Autowired TurnoService turnoService;
    @Autowired ChatEngine chat;
    @Autowired TurnoRepository turnos;
    @Autowired BarberoRepository barberos;
    @Autowired ServicioRepository servicios;

    Servicio corte;
    Barbero nico;

    @BeforeEach
    void prepararDatos() {
        turnos.deleteAll();
        barberos.deleteAll();
        servicios.deleteAll();
        nico = barberos.save(new Barbero("Nico"));
        corte = servicios.save(new Servicio("Corte", 30, 9000, List.of("corte")));
    }

    @Test
    void elPrimerHorarioEsLaAperturaYElUltimoTerminaAlCierre() {
        List<HorarioDisponible> libres = disponibilidad.horariosLibres(MARTES, corte.getId(), null);

        assertThat(libres.get(0).inicio()).isEqualTo(MARTES.atTime(10, 0));
        assertThat(libres.get(libres.size() - 1).inicio()).isEqualTo(MARTES.atTime(19, 30));
    }

    @Test
    void losDomingosNoHayHorarios() {
        LocalDate domingo = LocalDate.of(2026, 11, 22);
        assertThat(disponibilidad.horariosLibres(domingo, corte.getId(), null)).isEmpty();
    }

    @Test
    void unTurnoReservadoBloqueaLosHorariosQueSeSuperponen() {
        reservar(MARTES.atTime(11, 0));

        List<LocalDateTime> libres = disponibilidad.horariosLibres(MARTES, corte.getId(), null)
                .stream().map(HorarioDisponible::inicio).toList();

        assertThat(libres).doesNotContain(MARTES.atTime(10, 45), MARTES.atTime(11, 0), MARTES.atTime(11, 15));
        assertThat(libres).contains(MARTES.atTime(10, 30), MARTES.atTime(11, 30));
    }

    @Test
    void noSePuedeReservarDosVecesElMismoHorario() {
        reservar(MARTES.atTime(11, 0));

        assertThatThrownBy(() -> reservar(MARTES.atTime(11, 0)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void conDosBarberosElMismoHorarioSigueDisponible() {
        barberos.save(new Barbero("Lucas"));
        reservar(MARTES.atTime(11, 0));

        var segundo = reservar(MARTES.atTime(11, 0));

        assertThat(segundo.barberoNombre()).isEqualTo("Lucas");
    }

    @Test
    void elChatReservaUnTurnoDePuntaAPunta() {
        String sesion = "test-reserva";

        responder(sesion, "hola");
        ChatRespuesta horarios = responder(sesion, "quiero un corte el jueves a la tarde");
        assertThat(horarios.opciones()).contains("Otro día");

        responder(sesion, "a las 5");
        responder(sesion, "Juan Cruz");
        ChatRespuesta confirmacion = responder(sesion, "11 4567 8901");
        assertThat(confirmacion.texto()).contains("Corte", "17:00", "Juan Cruz");

        ChatRespuesta listo = responder(sesion, "sí");

        assertThat(listo.turno()).isNotNull();
        assertThat(listo.turno().inicio()).isEqualTo(LocalDate.of(2026, 11, 19).atTime(17, 0));
        assertThat(turnos.count()).isEqualTo(1);
    }

    @Test
    void elChatNoCancelaSiElTelefonoNoCoincide() {
        var turno = reservar(MARTES.atTime(15, 0));

        responder("test-cancelar", "quiero cancelar");
        responder("test-cancelar", "#" + turno.id());
        ChatRespuesta respuesta = responder("test-cancelar", "1199999999");

        assertThat(respuesta.texto()).contains("No encontré");
        assertThat(turnos.findById(turno.id()).orElseThrow().getEstado().name()).isEqualTo("CONFIRMADO");
    }

    private com.turnobot.dto.TurnoDTO reservar(LocalDateTime inicio) {
        return turnoService.reservar(new ReservaRequest(corte.getId(), null, inicio, "Cliente", "1145678901"));
    }

    private ChatRespuesta responder(String sesion, String mensaje) {
        return chat.responder(sesion, mensaje);
    }
}
