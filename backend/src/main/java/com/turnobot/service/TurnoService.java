package com.turnobot.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turnobot.dto.HorarioDisponible;
import com.turnobot.dto.ReservaRequest;
import com.turnobot.dto.TurnoDTO;
import com.turnobot.exceptions.RecursoNoEncontradoException;
import com.turnobot.exceptions.ReglaNegocioException;
import com.turnobot.model.Barbero;
import com.turnobot.model.EstadoTurno;
import com.turnobot.model.Servicio;
import com.turnobot.model.Turno;
import com.turnobot.repository.TurnoRepository;

@Service
public class TurnoService {

    private final TurnoRepository turnos;
    private final DisponibilidadService disponibilidad;
    private final Clock reloj;

    public TurnoService(TurnoRepository turnos, DisponibilidadService disponibilidad, Clock reloj) {
        this.turnos = turnos;
        this.disponibilidad = disponibilidad;
        this.reloj = reloj;
    }

    /**
     * Reserva un turno.
     *
     * "synchronized": si dos clientes piden el mismo horario al mismo tiempo, el segundo
     * espera a que termine el primero y recién ahí verifica. Sin esto, los dos verían el
     * horario libre y se reservaría dos veces (una "condición de carrera").
     * Alcanza para un solo servidor; con varios habría que bloquear en la base de datos.
     */
    public synchronized TurnoDTO reservar(ReservaRequest pedido) {
        Servicio servicio = disponibilidad.buscarServicio(pedido.servicioId());
        LocalDate fecha = pedido.inicio().toLocalDate();

        // Reusamos el cálculo de disponibilidad: si el horario no aparece como libre, no se puede
        HorarioDisponible horario = disponibilidad.horariosLibres(fecha, servicio.getId(), pedido.barberoId())
                .stream()
                .filter(h -> h.inicio().equals(pedido.inicio()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Ese horario no está disponible."));

        Barbero barbero = disponibilidad.buscarBarbero(horario.barberoId());
        Turno turno = new Turno(barbero, servicio, pedido.clienteNombre().trim(), pedido.clienteTelefono(),
                pedido.inicio(), LocalDateTime.now(reloj));
        return TurnoDTO.de(turnos.save(turno));
    }

    /** Cancelación pedida por el cliente: tiene que coincidir el teléfono con el del turno. */
    @Transactional
    public TurnoDTO cancelarPorCliente(Long id, String telefono) {
        Turno turno = buscar(id);
        if (!turno.getClienteTelefono().equals(telefono)) {
            throw new RecursoNoEncontradoException("No encontré un turno con ese código y teléfono.");
        }
        if (turno.getEstado() != EstadoTurno.CONFIRMADO || turno.getInicio().isBefore(LocalDateTime.now(reloj))) {
            throw new ReglaNegocioException("Ese turno ya no se puede cancelar.");
        }
        turno.setEstado(EstadoTurno.CANCELADO);
        return TurnoDTO.de(turno); // dentro de @Transactional, JPA guarda el cambio solo al terminar
    }

    @Transactional
    public TurnoDTO cambiarEstado(Long id, EstadoTurno estado) {
        Turno turno = buscar(id);
        turno.setEstado(estado);
        return TurnoDTO.de(turno);
    }

    @Transactional(readOnly = true)
    public List<TurnoDTO> delDia(LocalDate fecha) {
        return turnos.findByInicioBetweenOrderByInicio(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                .stream().map(TurnoDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public List<TurnoDTO> proximosDelCliente(String telefono) {
        return turnos.findByClienteTelefonoAndEstadoAndInicioAfterOrderByInicio(
                        telefono, EstadoTurno.CONFIRMADO, LocalDateTime.now(reloj))
                .stream().map(TurnoDTO::de).toList();
    }

    private Turno buscar(Long id) {
        return turnos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el turno " + id));
    }
}
