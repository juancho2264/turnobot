package com.turnobot.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turnobot.config.NegocioProperties;
import com.turnobot.dto.EstadisticasDTO;
import com.turnobot.dto.EstadisticasDTO.Ranking;
import com.turnobot.model.EstadoTurno;
import com.turnobot.model.Turno;
import com.turnobot.repository.BarberoRepository;
import com.turnobot.repository.TurnoRepository;

@Service
public class EstadisticasService {

    private final TurnoRepository turnos;
    private final BarberoRepository barberos;
    private final NegocioProperties negocio;

    public EstadisticasService(TurnoRepository turnos, BarberoRepository barberos, NegocioProperties negocio) {
        this.turnos = turnos;
        this.barberos = barberos;
        this.negocio = negocio;
    }

    /** Números del día elegido y de los 7 días que terminan en él. */
    @Transactional(readOnly = true)
    public EstadisticasDTO calcular(LocalDate fecha) {
        List<Turno> delDia = activos(turnosEntre(fecha, fecha));
        List<Turno> semanaCompleta = turnosEntre(fecha.minusDays(6), fecha);
        List<Turno> semana = activos(semanaCompleta);

        long cancelados = semanaCompleta.stream().filter(t -> t.getEstado() == EstadoTurno.CANCELADO).count();

        return new EstadisticasDTO(
                delDia.size(),
                ingresos(delDia),
                ocupacion(delDia, fecha),
                semana.size(),
                ingresos(semana),
                (int) cancelados,
                ranking(semana, t -> t.getServicio().getNombre()),
                ranking(semana, t -> t.getBarbero().getNombre()));
    }

    private List<Turno> turnosEntre(LocalDate desde, LocalDate hasta) {
        return turnos.findByInicioBetweenOrderByInicio(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
    }

    /** Los que ocupan agenda: todos menos los cancelados. */
    private static List<Turno> activos(List<Turno> lista) {
        return lista.stream().filter(t -> t.getEstado() != EstadoTurno.CANCELADO).toList();
    }

    /** Los ausentes ocuparon el lugar pero no pagaron. */
    private static int ingresos(List<Turno> lista) {
        return lista.stream()
                .filter(t -> t.getEstado() != EstadoTurno.AUSENTE)
                .mapToInt(t -> t.getServicio().getPrecio())
                .sum();
    }

    /** Porcentaje de minutos ocupados sobre los minutos disponibles de todos los barberos. */
    private int ocupacion(List<Turno> delDia, LocalDate fecha) {
        if (!negocio.abre(fecha.getDayOfWeek())) {
            return 0;
        }
        long minutosAbierto = Duration.between(negocio.apertura(), negocio.cierre()).toMinutes();
        long capacidad = minutosAbierto * barberos.findByActivoTrueOrderById().size();
        long ocupados = delDia.stream().mapToLong(t -> t.getServicio().getDuracionMinutos()).sum();
        return capacidad == 0 ? 0 : (int) Math.round(100.0 * ocupados / capacidad);
    }

    private static List<Ranking> ranking(List<Turno> lista, Function<Turno, String> clave) {
        Map<String, Long> conteo = lista.stream().collect(Collectors.groupingBy(clave, Collectors.counting()));
        return conteo.entrySet().stream()
                .map(e -> new Ranking(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(Ranking::cantidad).reversed())
                .toList();
    }
}
