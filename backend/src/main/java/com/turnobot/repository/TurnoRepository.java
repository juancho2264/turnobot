package com.turnobot.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turnobot.model.EstadoTurno;
import com.turnobot.model.Turno;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    /**
     * Turnos que ocupan la agenda (no cancelados) y se cruzan con el rango [desde, hasta).
     * Dos rangos se superponen si uno empieza antes de que el otro termine, y viceversa.
     */
    @Query("""
            SELECT t FROM Turno t
            WHERE t.estado <> com.turnobot.model.EstadoTurno.CANCELADO
              AND t.inicio < :hasta AND t.fin > :desde
            ORDER BY t.inicio
            """)
    List<Turno> findOcupados(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
            SELECT COUNT(t) > 0 FROM Turno t
            WHERE t.barbero.id = :barberoId
              AND t.estado <> com.turnobot.model.EstadoTurno.CANCELADO
              AND t.inicio < :hasta AND t.fin > :desde
            """)
    boolean existeSuperposicion(@Param("barberoId") Long barberoId,
                                @Param("desde") LocalDateTime desde,
                                @Param("hasta") LocalDateTime hasta);

    List<Turno> findByInicioBetweenOrderByInicio(LocalDateTime desde, LocalDateTime hasta);

    List<Turno> findByClienteTelefonoAndEstadoAndInicioAfterOrderByInicio(
            String telefono, EstadoTurno estado, LocalDateTime despuesDe);
}
