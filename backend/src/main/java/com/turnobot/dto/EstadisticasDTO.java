package com.turnobot.dto;

import java.util.List;

public record EstadisticasDTO(
        int turnosDelDia,
        int ingresosDelDia,
        int ocupacionDelDia,
        int turnosSemana,
        int ingresosSemana,
        int cancelacionesSemana,
        List<Ranking> serviciosMasPedidos,
        List<Ranking> turnosPorBarbero
) {
    public record Ranking(String nombre, long cantidad) {
    }
}
