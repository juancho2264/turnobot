package com.turnobot.chat;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.turnobot.model.Barbero;
import com.turnobot.model.Servicio;

/**
 * Extrae datos de un mensaje escrito en castellano rioplatense:
 * servicio, día, franja horaria, hora, barbero y teléfono.
 *
 * No guarda estado y no toca la base: recibe texto y devuelve resultados.
 * Eso lo hace muy fácil de testear (ver InterpreteTest).
 */
@Component
public class Interprete {

    public enum Franja {
        MANANA(LocalTime.of(0, 0), LocalTime.of(13, 0), "a la mañana"),
        TARDE(LocalTime.of(13, 0), LocalTime.of(18, 0), "a la tarde"),
        NOCHE(LocalTime.of(18, 0), LocalTime.of(23, 59), "a última hora");

        public final LocalTime desde;
        public final LocalTime hasta;
        public final String texto;

        Franja(LocalTime desde, LocalTime hasta, String texto) {
            this.desde = desde;
            this.hasta = hasta;
            this.texto = texto;
        }

        public boolean incluye(LocalTime hora) {
            return !hora.isBefore(desde) && hora.isBefore(hasta);
        }
    }

    private static final Map<String, DayOfWeek> DIAS = Map.of(
            "lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY, "miercoles", DayOfWeek.WEDNESDAY,
            "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);

    // "a la mañana" es una franja, no el día de mañana: lo sacamos antes de buscar la fecha
    private static final Pattern FRANJA_MANANA = Pattern.compile("\\b(a|por|de) la manana\\b|\\btemprano\\b");
    private static final Pattern FECHA_NUMERICA = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})\\b");
    private static final Pattern DIA_DEL_MES = Pattern.compile("\\bel (?:dia )?(\\d{1,2})\\b(?![:.]\\d)(?!\\s*(?:hs|h)\\b)");
    private static final Pattern HORA_CON_MINUTOS = Pattern.compile("\\b(\\d{1,2})[:.](\\d{2})\\b");
    private static final Pattern HORA_CON_SUFIJO = Pattern.compile("\\b(\\d{1,2})\\s*(?:hs|h|horas)\\b");
    private static final Pattern HORA_CON_PREFIJO = Pattern.compile(
            "\\b(?:a las|las|tipo|a eso de|para las)\\s+(\\d{1,2})(\\s+y\\s+(media|cuarto))?\\b");

    /** Minúsculas, sin tildes y sin espacios repetidos: "Mañana  a la TARDE" → "manana a la tarde". */
    public String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase().replaceAll("[¿?¡!,;]", " ").replaceAll("\\s+", " ").trim();
    }

    // ---------- Servicio ----------

    /**
     * Elige el servicio que mejor coincide con el texto, con un puntaje:
     * suma el largo de cada término del servicio (palabras clave + palabras de su nombre)
     * que aparece en el mensaje. Si hay empate, gana el que tiene menos palabras del nombre sin mencionar.
     *
     * "corte para mi nene" → Corte infantil ("corte" + "nene") le gana a Corte (solo "corte").
     * "quiero corte"       → Corte y Corte + barba empatan, pero a Corte + barba le falta "barba".
     */
    public Optional<Servicio> servicio(String texto, List<Servicio> servicios) {
        String t = normalizar(texto);
        record Puntaje(Servicio servicio, int coincidencia, int faltantes) { }

        return servicios.stream()
                .map(s -> {
                    List<String> palabrasDelNombre = palabras(s.getNombre());
                    Set<String> terminos = new HashSet<>(palabrasDelNombre);
                    s.getPalabrasClave().forEach(clave -> terminos.add(normalizar(clave)));

                    int coincidencia = terminos.stream().filter(term -> contienePalabra(t, term)).mapToInt(String::length).sum();
                    int faltantes = (int) palabrasDelNombre.stream().filter(p -> !contienePalabra(t, p)).count();
                    return new Puntaje(s, coincidencia, faltantes);
                })
                .filter(p -> p.coincidencia() > 0)
                .max(Comparator.comparingInt(Puntaje::coincidencia)
                        .thenComparing(Comparator.comparingInt(Puntaje::faltantes).reversed()))
                .map(Puntaje::servicio);
    }

    /** Palabras significativas de un nombre: "Corte + barba" → [corte, barba]. */
    private List<String> palabras(String nombre) {
        return Arrays.stream(normalizar(nombre).split("[^a-z]+"))
                .filter(p -> p.length() >= 3)
                .toList();
    }

    // ---------- Fecha ----------

    public Optional<LocalDate> fecha(String texto, LocalDate hoy) {
        String t = FRANJA_MANANA.matcher(normalizar(texto)).replaceAll(" ");

        if (t.contains("pasado manana")) return Optional.of(hoy.plusDays(2));
        if (contienePalabra(t, "manana")) return Optional.of(hoy.plusDays(1));
        if (contienePalabra(t, "hoy")) return Optional.of(hoy);

        Matcher numerica = FECHA_NUMERICA.matcher(t);
        if (numerica.find()) {
            return fechaDesdeDiaYMes(Integer.parseInt(numerica.group(1)), Integer.parseInt(numerica.group(2)), hoy);
        }

        for (Map.Entry<String, DayOfWeek> dia : DIAS.entrySet()) {
            if (contienePalabra(t, dia.getKey())) {
                // "el jueves" = el próximo jueves (o hoy, si hoy es jueves)
                return Optional.of(hoy.with(TemporalAdjusters.nextOrSame(dia.getValue())));
            }
        }

        Matcher diaDelMes = DIA_DEL_MES.matcher(t);
        if (diaDelMes.find()) {
            int dia = Integer.parseInt(diaDelMes.group(1));
            if (dia >= 1 && dia <= 31) {
                LocalDate candidata = hoy.withDayOfMonth(Math.min(dia, hoy.lengthOfMonth()));
                return Optional.of(candidata.isBefore(hoy) ? candidata.plusMonths(1) : candidata);
            }
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> fechaDesdeDiaYMes(int dia, int mes, LocalDate hoy) {
        try {
            LocalDate fecha = LocalDate.of(hoy.getYear(), mes, dia);
            return Optional.of(fecha.isBefore(hoy) ? fecha.plusYears(1) : fecha);
        } catch (java.time.DateTimeException e) {
            return Optional.empty(); // ej. 31/11
        }
    }

    // ---------- Franja y hora ----------

    public Optional<Franja> franja(String texto) {
        String t = normalizar(texto);
        if (FRANJA_MANANA.matcher(t).find()) return Optional.of(Franja.MANANA);
        if (contienePalabra(t, "tarde") || contienePalabra(t, "tardecita")) return Optional.of(Franja.TARDE);
        if (contienePalabra(t, "noche") || t.contains("ultima hora")) return Optional.of(Franja.NOCHE);
        return Optional.empty();
    }

    /** "17:30", "17hs", "a las 5" (→ 17:00, porque nadie se corta el pelo a las 5 AM), "a las 6 y media". */
    public Optional<LocalTime> hora(String texto) {
        String t = normalizar(texto);

        Matcher m = HORA_CON_MINUTOS.matcher(t);
        if (m.find()) return crearHora(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));

        m = HORA_CON_PREFIJO.matcher(t);
        if (m.find()) {
            int minutos = m.group(3) == null ? 0 : m.group(3).equals("media") ? 30 : 15;
            return crearHora(Integer.parseInt(m.group(1)), minutos);
        }

        m = HORA_CON_SUFIJO.matcher(t);
        if (m.find()) return crearHora(Integer.parseInt(m.group(1)), 0);

        return Optional.empty();
    }

    private static Optional<LocalTime> crearHora(int hora, int minutos) {
        if (hora >= 1 && hora <= 8) {
            hora += 12; // horario comercial: "a las 5" es a la tarde
        }
        if (hora > 23 || minutos > 59) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hora, minutos));
    }

    // ---------- Barbero, teléfono y nombre ----------

    public Optional<Barbero> barbero(String texto, List<Barbero> barberos) {
        String t = normalizar(texto);
        return barberos.stream()
                .filter(b -> contienePalabra(t, normalizar(b.getNombre())))
                .findFirst();
    }

    public boolean pideCualquierBarbero(String texto) {
        String t = normalizar(texto);
        return t.contains("cualquiera") || t.contains("me da igual") || t.contains("el que este");
    }

    /** Acepta "11 4567-8901", "+54 9 11 4567 8901", etc. Devuelve solo los números. */
    public Optional<String> telefono(String texto) {
        String numeros = texto.replaceAll("[\\s()+.-]", "");
        if (!numeros.matches("\\d{8,15}")) {
            return Optional.empty();
        }
        // Sacamos el prefijo internacional de Argentina para guardar siempre igual
        if (numeros.startsWith("549") && numeros.length() > 12) numeros = numeros.substring(3);
        else if (numeros.startsWith("54") && numeros.length() > 11) numeros = numeros.substring(2);
        return numeros.length() <= 13 ? Optional.of(numeros) : Optional.empty();
    }

    /** "soy juan cruz" → "Juan Cruz". Vacío si no parece un nombre. */
    public Optional<String> nombre(String texto) {
        String limpio = texto.trim()
                .replaceAll("(?i)^(soy|me llamo|mi nombre es|a nombre de)\\s+", "")
                .replaceAll("[^\\p{L}\\s'-]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (limpio.length() < 2 || limpio.length() > 40) {
            return Optional.empty();
        }
        StringBuilder resultado = new StringBuilder();
        for (String parte : limpio.split(" ")) {
            resultado.append(Character.toUpperCase(parte.charAt(0)))
                    .append(parte.substring(1).toLowerCase())
                    .append(' ');
        }
        return Optional.of(resultado.toString().trim());
    }

    /** Extrae un código de turno: "#12", "el 12", "turno 12". */
    public Optional<Long> codigo(String texto) {
        Matcher m = Pattern.compile("#?\\s*(\\d{1,7})\\b").matcher(texto);
        return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
    }

    // ---------- Intenciones ----------

    public boolean esSaludo(String texto) {
        return empiezaCon(normalizar(texto), "hola", "buenas", "buen dia", "buenos dias", "que tal", "hey");
    }

    public boolean pidePrecios(String texto) {
        String t = normalizar(texto);
        return t.contains("precio") || t.contains("cuanto sale") || t.contains("cuanto cuesta")
                || t.contains("cuanto esta") || t.contains("valor") || t.contains("servicios") || t.contains("lista");
    }

    public boolean pideInformacion(String texto) {
        String t = normalizar(texto);
        return t.contains("horario") || t.contains("abren") || t.contains("abiertos") || t.contains("cierran")
                || t.contains("direccion") || t.contains("donde estan") || t.contains("ubicacion") || t.contains("donde queda");
    }

    public boolean quiereCancelar(String texto) {
        String t = normalizar(texto);
        return t.contains("cancelar") || t.contains("anular") || t.contains("no voy a poder") || t.contains("no puedo ir");
    }

    public boolean quiereReiniciar(String texto) {
        String t = normalizar(texto);
        return t.contains("empezar de nuevo") || t.contains("reiniciar") || t.equals("menu") || t.contains("volver al inicio");
    }

    public boolean esAfirmacion(String texto) {
        return empiezaCon(normalizar(texto), "si", "dale", "ok", "confirmo", "confirmar", "perfecto", "de una",
                "joya", "listo", "genial", "obvio", "claro");
    }

    public boolean esNegacion(String texto) {
        return empiezaCon(normalizar(texto), "no", "nop", "mejor no", "cambiar");
    }

    public boolean agradece(String texto) {
        String t = normalizar(texto);
        return t.contains("gracias") || t.equals("genial") || t.equals("buenisimo");
    }

    // ---------- Utilidades ----------

    /** true si "palabra" aparece como palabra completa ("corte" no matchea dentro de "cortez"). */
    private static boolean contienePalabra(String texto, String palabra) {
        return Pattern.compile("(^|\\W)" + Pattern.quote(palabra) + "($|\\W)").matcher(texto).find();
    }

    private static boolean empiezaCon(String texto, String... opciones) {
        for (String opcion : opciones) {
            if (texto.equals(opcion) || texto.startsWith(opcion + " ")) {
                return true;
            }
        }
        return false;
    }
}
