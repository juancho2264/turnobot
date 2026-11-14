package com.turnobot.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.turnobot.model.Barbero;
import com.turnobot.model.EstadoTurno;
import com.turnobot.model.Servicio;
import com.turnobot.model.Turno;
import com.turnobot.repository.BarberoRepository;
import com.turnobot.repository.ServicioRepository;
import com.turnobot.repository.TurnoRepository;

/**
 * Carga barberos, servicios y turnos inventados al arrancar, para que la demo
 * tenga movimiento. No corre en los tests (perfil "test"), que arman sus propios datos.
 */
@Component
@Profile("!test")
public class DatosDeEjemplo implements CommandLineRunner {

    private static final List<String> CLIENTES = List.of(
            "Martín", "Lucía", "Diego", "Sofía", "Tomás", "Valentina", "Joaquín", "Camila",
            "Facundo", "Agustina", "Bruno", "Julieta", "Franco", "Micaela", "Santiago", "Rocío");

    private final BarberoRepository barberos;
    private final ServicioRepository servicios;
    private final TurnoRepository turnos;
    private final NegocioProperties negocio;
    private final Clock reloj;

    public DatosDeEjemplo(BarberoRepository barberos, ServicioRepository servicios, TurnoRepository turnos,
                          NegocioProperties negocio, Clock reloj) {
        this.barberos = barberos;
        this.servicios = servicios;
        this.turnos = turnos;
        this.negocio = negocio;
        this.reloj = reloj;
    }

    @Override
    public void run(String... args) {
        if (barberos.count() > 0) {
            return; // la base ya tiene datos (ej. PostgreSQL en producción)
        }

        List<Barbero> equipo = barberos.saveAll(List.of(
                new Barbero("Nico"), new Barbero("Lucas"), new Barbero("Mati")));

        List<Servicio> catalogo = servicios.saveAll(List.of(
                new Servicio("Corte", 30, 9000, List.of("corte", "cortar", "pelo", "cabello")),
                new Servicio("Corte + barba", 45, 13000, List.of("corte y barba", "corte + barba", "completo")),
                new Servicio("Barba", 20, 6000, List.of("barba", "afeitado", "afeitar", "perfilado")),
                new Servicio("Corte infantil", 30, 7500, List.of("infantil", "nene", "nena", "hijo", "hija", "chico")),
                new Servicio("Color", 60, 18000, List.of("color", "tintura", "mechas", "decoloración", "platinado"))));

        turnos.saveAll(generarTurnos(equipo, catalogo));
    }

    /** Turnos de la semana pasada (con estados variados) y algunos de los próximos días. */
    private List<Turno> generarTurnos(List<Barbero> equipo, List<Servicio> catalogo) {
        Random azar = new Random(23); // semilla fija: siempre genera los mismos datos
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate hoy = ahora.toLocalDate();
        List<Turno> generados = new ArrayList<>();

        for (int dia = -7; dia <= 5; dia++) {
            LocalDate fecha = hoy.plusDays(dia);
            if (!negocio.abre(fecha.getDayOfWeek())) {
                continue;
            }
            double ocupacion = dia < 0 ? 0.7 : dia == 0 ? 0.55 : 0.35 - dia * 0.04;

            for (Barbero barbero : equipo) {
                LocalDateTime cursor = fecha.atTime(negocio.apertura());
                LocalDateTime cierre = fecha.atTime(negocio.cierre());

                while (true) {
                    Servicio servicio = catalogo.get(azar.nextInt(catalogo.size()));
                    if (cursor.plusMinutes(servicio.getDuracionMinutos()).isAfter(cierre)) {
                        break;
                    }
                    if (azar.nextDouble() < ocupacion) {
                        String cliente = CLIENTES.get(azar.nextInt(CLIENTES.size()));
                        String telefono = "11" + (40000000 + azar.nextInt(59999999));
                        Turno turno = new Turno(barbero, servicio, cliente, telefono, cursor, cursor.minusDays(2));
                        turno.setEstado(estadoSegunFecha(turno, ahora, azar));
                        generados.add(turno);
                        cursor = redondearArriba(turno.getFin());
                    } else {
                        cursor = cursor.plusMinutes(30);
                    }
                }
            }
        }
        return generados;
    }

    private static EstadoTurno estadoSegunFecha(Turno turno, LocalDateTime ahora, Random azar) {
        double tirada = azar.nextDouble();
        if (turno.getFin().isAfter(ahora)) {
            return tirada < 0.06 ? EstadoTurno.CANCELADO : EstadoTurno.CONFIRMADO;
        }
        if (tirada < 0.08) return EstadoTurno.CANCELADO;
        if (tirada < 0.12) return EstadoTurno.AUSENTE;
        return EstadoTurno.COMPLETADO;
    }

    /** Lleva la hora al próximo múltiplo del intervalo (ej. 10:45 → 10:45, 10:50 → 11:00). */
    private LocalDateTime redondearArriba(LocalDateTime hora) {
        int intervalo = negocio.intervaloMinutos();
        int resto = hora.getMinute() % intervalo;
        return resto == 0 ? hora : hora.plusMinutes(intervalo - resto);
    }
}
