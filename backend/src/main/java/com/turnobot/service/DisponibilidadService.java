package com.turnobot.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.turnobot.config.NegocioProperties;
import com.turnobot.dto.HorarioDisponible;
import com.turnobot.exceptions.RecursoNoEncontradoException;
import com.turnobot.model.Barbero;
import com.turnobot.model.Servicio;
import com.turnobot.model.Turno;
import com.turnobot.repository.BarberoRepository;
import com.turnobot.repository.ServicioRepository;
import com.turnobot.repository.TurnoRepository;

/**
 * Calcula qué horarios están libres. Es el corazón del sistema:
 * lo usan el chat, la reserva y el panel.
 */
@Service
public class DisponibilidadService {

    private final TurnoRepository turnos;
    private final BarberoRepository barberos;
    private final ServicioRepository servicios;
    private final NegocioProperties negocio;
    private final Clock reloj;

    // Inyección por constructor: las dependencias quedan "final" y se ven de un vistazo
    public DisponibilidadService(TurnoRepository turnos, BarberoRepository barberos,
                                 ServicioRepository servicios, NegocioProperties negocio, Clock reloj) {
        this.turnos = turnos;
        this.barberos = barberos;
        this.servicios = servicios;
        this.negocio = negocio;
        this.reloj = reloj;
    }

    /**
     * Horarios libres de un día para un servicio.
     * Si barberoId es null, devuelve cada horario una sola vez, asignado al barbero
     * libre con menos trabajo ese día (así se reparte la carga).
     */
    public List<HorarioDisponible> horariosLibres(LocalDate fecha, Long servicioId, Long barberoId) {
        Servicio servicio = buscarServicio(servicioId);
        if (!esFechaReservable(fecha)) {
            return List.of();
        }

        List<Barbero> candidatos = barberoId == null
                ? barberos.findByActivoTrueOrderById()
                : List.of(buscarBarbero(barberoId));

        LocalDateTime apertura = fecha.atTime(negocio.apertura());
        LocalDateTime cierre = fecha.atTime(negocio.cierre());
        LocalDateTime minimo = LocalDateTime.now(reloj).plusMinutes(negocio.anticipacionMinimaMinutos());

        // Una sola consulta a la base para todo el día, y después trabajamos en memoria
        Map<Long, List<Turno>> ocupados = turnos.findOcupados(apertura, cierre).stream()
                .collect(Collectors.groupingBy(t -> t.getBarbero().getId()));

        // Primero los barberos con menos turnos ese día
        List<Barbero> ordenados = candidatos.stream()
                .sorted(Comparator.comparingInt(b -> ocupados.getOrDefault(b.getId(), List.of()).size()))
                .toList();

        List<HorarioDisponible> libres = new ArrayList<>();
        int duracion = servicio.getDuracionMinutos();

        for (LocalDateTime inicio = apertura;
             !inicio.plusMinutes(duracion).isAfter(cierre);
             inicio = inicio.plusMinutes(negocio.intervaloMinutos())) {

            if (inicio.isBefore(minimo)) {
                continue;
            }
            LocalDateTime fin = inicio.plusMinutes(duracion);
            for (Barbero barbero : ordenados) {
                if (!seSuperpone(ocupados.getOrDefault(barbero.getId(), List.of()), inicio, fin)) {
                    libres.add(new HorarioDisponible(inicio, barbero.getId(), barbero.getNombre()));
                    break;
                }
            }
        }
        return libres;
    }

    /** Próximos días (desde hoy) que tienen al menos un horario libre para el servicio. */
    public List<LocalDate> proximosDiasConLugar(Long servicioId, int cantidad) {
        List<LocalDate> dias = new ArrayList<>();
        LocalDate hoy = LocalDate.now(reloj);
        for (int i = 0; i <= negocio.diasDeReserva() && dias.size() < cantidad; i++) {
            LocalDate dia = hoy.plusDays(i);
            if (!horariosLibres(dia, servicioId, null).isEmpty()) {
                dias.add(dia);
            }
        }
        return dias;
    }

    public boolean esFechaReservable(LocalDate fecha) {
        LocalDate hoy = LocalDate.now(reloj);
        return negocio.abre(fecha.getDayOfWeek())
                && !fecha.isBefore(hoy)
                && !fecha.isAfter(hoy.plusDays(negocio.diasDeReserva()));
    }

    public Servicio buscarServicio(Long id) {
        return servicios.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el servicio " + id));
    }

    public Barbero buscarBarbero(Long id) {
        return barberos.findById(id)
                .filter(Barbero::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el barbero " + id));
    }

    private static boolean seSuperpone(List<Turno> ocupados, LocalDateTime inicio, LocalDateTime fin) {
        return ocupados.stream().anyMatch(t -> t.getInicio().isBefore(fin) && t.getFin().isAfter(inicio));
    }
}
