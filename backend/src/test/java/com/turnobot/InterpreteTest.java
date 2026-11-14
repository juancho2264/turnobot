package com.turnobot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.turnobot.chat.Interprete;
import com.turnobot.chat.Interprete.Franja;
import com.turnobot.model.Servicio;

/** Tests unitarios puros: no levantan Spring ni base de datos, corren en milisegundos. */
class InterpreteTest {

    private final Interprete interprete = new Interprete();
    private final LocalDate martes = LocalDate.of(2026, 11, 17);

    private final List<Servicio> servicios = List.of(
            new Servicio("Corte", 30, 9000, List.of("corte", "pelo")),
            new Servicio("Corte + barba", 45, 13000, List.of("corte y barba", "corte + barba")),
            new Servicio("Barba", 20, 6000, List.of("barba")),
            new Servicio("Corte infantil", 30, 7500, List.of("infantil", "nene")));

    // @ParameterizedTest: el mismo test con muchos casos. Cada fila del CSV es uno.
    @ParameterizedTest
    @CsvSource({
            "hoy,                         2026-11-17",
            "mañana,                      2026-11-18",
            "Mañana a la tarde,           2026-11-18",
            "pasado mañana,               2026-11-19",
            "el jueves,                   2026-11-19",
            "el martes,                   2026-11-17",
            "el lunes,                    2026-11-23",
            "Viernes 20/11,               2026-11-20",
            "el 25,                       2026-11-25",
            "el 3,                        2026-12-03",
            "15/01,                       2027-01-15"
    })
    void entiendeFechas(String texto, LocalDate esperada) {
        assertThat(interprete.fecha(texto, martes)).contains(esperada);
    }

    @Test
    void aLaMananaEsUnaFranjaNoElDiaDeManana() {
        assertThat(interprete.fecha("quiero ir a la mañana", martes)).isEmpty();
        assertThat(interprete.franja("quiero ir a la mañana")).contains(Franja.MANANA);
    }

    @ParameterizedTest
    @CsvSource({
            "17:30,                 17:30",
            "a las 5,               17:00",
            "a las 6 y media,       18:30",
            "tipo 11,               11:00",
            "19hs,                  19:00",
            "a las 10.15,           10:15"
    })
    void entiendeHoras(String texto, LocalTime esperada) {
        assertThat(interprete.hora(texto)).contains(esperada);
    }

    @Test
    void unaFechaNoSeConfundeConUnaHora() {
        assertThat(interprete.hora("Viernes 20/11")).isEmpty();
        assertThat(interprete.hora("el 25")).isEmpty();
    }

    @Test
    void eligeElServicioMasEspecifico() {
        assertThat(interprete.servicio("quiero corte y barba", servicios)).get()
                .extracting(Servicio::getNombre).isEqualTo("Corte + barba");
        assertThat(interprete.servicio("un corte con barba", servicios)).get()
                .extracting(Servicio::getNombre).isEqualTo("Corte + barba");
        assertThat(interprete.servicio("corte para mi nene", servicios)).get()
                .extracting(Servicio::getNombre).isEqualTo("Corte infantil");
        assertThat(interprete.servicio("me corto el pelo", servicios)).get()
                .extracting(Servicio::getNombre).isEqualTo("Corte");
        // Empates: gana el servicio al que no le falta ninguna palabra
        assertThat(interprete.servicio("quiero corte", servicios)).get()
                .extracting(Servicio::getNombre).isEqualTo("Corte");
        assertThat(interprete.servicio("barba", servicios)).get()
                .extracting(Servicio::getNombre).isEqualTo("Barba");
        assertThat(interprete.servicio("hola que tal", servicios)).isEmpty();
    }

    @Test
    void normalizaTelefonos() {
        assertThat(interprete.telefono("11 4567-8901")).contains("1145678901");
        assertThat(interprete.telefono("+54 9 11 4567 8901")).contains("1145678901");
        assertThat(interprete.telefono("hola")).isEmpty();
        assertThat(interprete.telefono("123")).isEmpty();
    }

    @Test
    void limpiaNombres() {
        assertThat(interprete.nombre("soy juan cruz")).contains("Juan Cruz");
        assertThat(interprete.nombre("MARÍA")).contains("María");
        assertThat(interprete.nombre("1")).isEmpty();
    }
}
