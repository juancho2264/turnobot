package com.turnobot.chat;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.turnobot.chat.Conversacion.Paso;
import com.turnobot.config.NegocioProperties;
import com.turnobot.dto.ChatRespuesta;
import com.turnobot.dto.HorarioDisponible;
import com.turnobot.dto.ReservaRequest;
import com.turnobot.dto.TurnoDTO;
import com.turnobot.exceptions.RecursoNoEncontradoException;
import com.turnobot.exceptions.ReglaNegocioException;
import com.turnobot.model.Barbero;
import com.turnobot.model.Servicio;
import com.turnobot.repository.BarberoRepository;
import com.turnobot.repository.ServicioRepository;
import com.turnobot.service.DisponibilidadService;
import com.turnobot.service.TurnoService;

/**
 * Chat basado en reglas: no usa IA ni cuesta plata.
 *
 * Funciona por "relleno de casilleros" (slot filling): de cada mensaje saca todos los
 * datos que pueda (servicio, día, hora, barbero) y después pregunta solo lo que falta.
 * Por eso entiende tanto "turno" como "corte el jueves a las 5 con Lucas".
 */
@Component
public class ReglasChatEngine implements ChatEngine {

    private static final Duration EXPIRACION = Duration.ofMinutes(30);
    private static final int MAX_HORARIOS_A_MOSTRAR = 8;
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    /** Conversaciones activas, por id de sesión. ConcurrentHashMap: la usan varios hilos a la vez. */
    private final Map<String, Conversacion> conversaciones = new ConcurrentHashMap<>();

    private final Interprete interprete;
    private final DisponibilidadService disponibilidad;
    private final TurnoService turnoService;
    private final ServicioRepository servicios;
    private final BarberoRepository barberos;
    private final NegocioProperties negocio;
    private final Clock reloj;

    public ReglasChatEngine(Interprete interprete, DisponibilidadService disponibilidad, TurnoService turnoService,
                            ServicioRepository servicios, BarberoRepository barberos,
                            NegocioProperties negocio, Clock reloj) {
        this.interprete = interprete;
        this.disponibilidad = disponibilidad;
        this.turnoService = turnoService;
        this.servicios = servicios;
        this.barberos = barberos;
        this.negocio = negocio;
        this.reloj = reloj;
    }

    @Override
    public String modo() {
        return "demo";
    }

    @Override
    public ChatRespuesta responder(String sesionId, String mensaje) {
        borrarConversacionesViejas();
        Conversacion conversacion = conversaciones.computeIfAbsent(sesionId, id -> new Conversacion());
        conversacion.ultimaActividad = Instant.now(reloj);

        // Si llegan dos mensajes de la misma sesión a la vez, se procesan de a uno
        synchronized (conversacion) {
            return procesar(conversacion, mensaje.trim());
        }
    }

    // =====================================================================
    // Despacho principal
    // =====================================================================

    private ChatRespuesta procesar(Conversacion c, String mensaje) {
        if (interprete.quiereReiniciar(mensaje)) {
            c.reiniciar();
            return bienvenida();
        }
        if (c.paso == Paso.CANCELANDO_CODIGO) return pasoCancelarCodigo(c, mensaje);
        if (c.paso == Paso.CANCELANDO_TELEFONO) return pasoCancelarTelefono(c, mensaje);

        if (interprete.quiereCancelar(mensaje) && c.paso != Paso.CONFIRMANDO) {
            c.reiniciar();
            c.paso = Paso.CANCELANDO_CODIGO;
            return ChatRespuesta.de("Dale. Pasame el código de tu turno (te lo di al reservar, por ejemplo #12).",
                    List.of("Volver al inicio"));
        }

        boolean preguntaGeneral = c.paso == Paso.INICIO || c.paso == Paso.RESERVANDO;
        if (preguntaGeneral && interprete.pidePrecios(mensaje)) return precios();
        if (preguntaGeneral && interprete.pideInformacion(mensaje)) return informacion();

        return switch (c.paso) {
            case PIDIENDO_NOMBRE -> pasoNombre(c, mensaje);
            case PIDIENDO_TELEFONO -> pasoTelefono(c, mensaje);
            case CONFIRMANDO -> pasoConfirmar(c, mensaje);
            default -> pasoReserva(c, mensaje);
        };
    }

    // =====================================================================
    // Reserva
    // =====================================================================

    private ChatRespuesta pasoReserva(Conversacion c, String mensaje) {
        boolean entendiAlgo = completarDatos(c, mensaje);

        if (c.paso == Paso.INICIO && !entendiAlgo) {
            if (interprete.agradece(mensaje)) {
                return ChatRespuesta.de("¡De nada! Cualquier cosa, acá estoy 💈", opcionesDeInicio());
            }
            if (interprete.esSaludo(mensaje)) {
                return bienvenida();
            }
            return noEntendi();
        }
        if (!entendiAlgo && c.paso == Paso.RESERVANDO && !interprete.esSaludo(mensaje)) {
            return conPrefijo("Perdón, no te entendí 🤔.", siguientePregunta(c));
        }
        c.paso = Paso.RESERVANDO;
        return siguientePregunta(c);
    }

    /** Saca del mensaje todos los datos que pueda. Devuelve true si entendió algo útil. */
    private boolean completarDatos(Conversacion c, String mensaje) {
        boolean entendi = false;
        String texto = interprete.normalizar(mensaje);

        if (texto.contains("turno") || texto.contains("reserv") || texto.contains("lugar")) {
            entendi = true; // quiere reservar, aunque todavía no dio datos
        }
        if (texto.equals("otro dia") || texto.contains("otro dia")) {
            c.fecha = null;
            c.olvidarHorario();
            return true;
        }

        Optional<Servicio> servicio = interprete.servicio(mensaje, servicios.findAll());
        if (servicio.isPresent()) {
            if (!servicio.get().getId().equals(c.servicioId)) c.olvidarHorario();
            c.servicioId = servicio.get().getId();
            entendi = true;
        }

        Optional<LocalDate> fecha = interprete.fecha(mensaje, hoy());
        if (fecha.isPresent()) {
            if (!fecha.get().equals(c.fecha)) c.olvidarHorario();
            c.fecha = fecha.get();
            entendi = true;
        }

        Optional<Barbero> barbero = interprete.barbero(mensaje, barberos.findByActivoTrueOrderById());
        if (barbero.isPresent()) {
            c.barberoId = barbero.get().getId();
            c.horarioElegido = null;
            entendi = true;
        } else if (interprete.pideCualquierBarbero(mensaje)) {
            c.barberoId = null;
            entendi = true;
        }

        Optional<Interprete.Franja> franja = interprete.franja(mensaje);
        if (franja.isPresent()) {
            c.franja = franja.get();
            if (c.hora != null && !c.franja.incluye(c.hora)) c.hora = null; // "a la tarde" pisa un "a las 10" anterior
            entendi = true;
        }

        Optional<LocalTime> hora = interprete.hora(mensaje);
        if (hora.isPresent()) {
            c.hora = hora.get();
            c.franja = null; // una hora exacta es más precisa que una franja
            c.horarioElegido = null;
            entendi = true;
        }
        return entendi;
    }

    /** Pregunta lo próximo que falta. Es la única que decide el orden de las preguntas. */
    private ChatRespuesta siguientePregunta(Conversacion c) {
        if (c.servicioId == null) {
            return ChatRespuesta.de("¿Qué te querés hacer?", nombresDeServicios());
        }
        Servicio servicio = disponibilidad.buscarServicio(c.servicioId);

        if (c.fecha == null) {
            return ofrecerDias(c, "Buenísimo, " + servicio.getNombre().toLowerCase() + ". ¿Para qué día?");
        }
        if (!disponibilidad.esFechaReservable(c.fecha)) {
            String motivo = motivoFechaNoDisponible(c.fecha);
            c.fecha = null;
            return ofrecerDias(c, motivo);
        }

        List<HorarioDisponible> libres = disponibilidad.horariosLibres(c.fecha, c.servicioId, c.barberoId);
        String conQuien = c.barberoId == null ? "" : " con " + disponibilidad.buscarBarbero(c.barberoId).getNombre();

        // ¿Pidió una hora puntual?
        if (c.hora != null) {
            Optional<HorarioDisponible> exacto = libres.stream()
                    .filter(h -> h.inicio().toLocalTime().equals(c.hora))
                    .findFirst();
            if (exacto.isPresent()) {
                c.horarioElegido = exacto.get();
                return pedirDatosOConfirmar(c);
            }
            LocalTime pedida = c.hora;
            c.hora = null;
            if (!libres.isEmpty()) {
                List<String> cercanos = libres.stream()
                        .sorted(Comparator.comparingLong(h -> Math.abs(Duration.between(pedida, h.inicio().toLocalTime()).toMinutes())))
                        .limit(3)
                        .sorted(Comparator.comparing(HorarioDisponible::inicio))
                        .map(h -> h.inicio().toLocalTime().format(HH_MM))
                        .toList();
                return ChatRespuesta.de("A las " + pedida.format(HH_MM) + " no tengo lugar" + conQuien + " "
                                + fechaCorta(c.fecha) + ". Lo más cerca que tengo:",
                        conOtroDia(cercanos));
            }
        }

        List<HorarioDisponible> enFranja = c.franja == null ? libres
                : libres.stream().filter(h -> c.franja.incluye(h.inicio().toLocalTime())).toList();

        if (libres.isEmpty()) {
            LocalDate sinLugar = c.fecha;
            c.fecha = null;
            return ofrecerDias(c, capitalizar(fechaCorta(sinLugar)) + " no me queda lugar" + conQuien
                    + " 😕. ¿Probamos otro día?", sinLugar);
        }
        if (enFranja.isEmpty()) {
            String franjaTexto = c.franja.texto;
            c.franja = null;
            return ChatRespuesta.de(capitalizar(franjaTexto) + " no me queda lugar " + fechaCorta(c.fecha)
                    + ", pero tengo estos horarios" + conQuien + ":", conOtroDia(horas(muestrear(libres))));
        }

        String franjaTexto = c.franja == null ? "" : " " + c.franja.texto;
        return ChatRespuesta.de("Para " + servicio.getNombre().toLowerCase() + " " + fechaCorta(c.fecha) + franjaTexto
                        + " tengo estos horarios" + conQuien + ". ¿Cuál te queda mejor?",
                conOtroDia(horas(muestrear(enFranja))));
    }

    private ChatRespuesta pedirDatosOConfirmar(Conversacion c) {
        if (c.nombre == null) {
            c.paso = Paso.PIDIENDO_NOMBRE;
            return ChatRespuesta.de("¡Perfecto! " + resumenCorto(c) + ".\n¿A nombre de quién lo reservo?");
        }
        if (c.telefono == null) {
            c.paso = Paso.PIDIENDO_TELEFONO;
            return pedirTelefono(c);
        }
        c.paso = Paso.CONFIRMANDO;
        return pedirConfirmacion(c);
    }

    private ChatRespuesta pasoNombre(Conversacion c, String mensaje) {
        // Si en vez del nombre cambió de idea ("mejor a las 6"), volvemos a la reserva
        if (interprete.hora(mensaje).isPresent() || interprete.fecha(mensaje, hoy()).isPresent()) {
            c.paso = Paso.RESERVANDO;
            return pasoReserva(c, mensaje);
        }
        Optional<String> nombre = interprete.nombre(mensaje);
        if (nombre.isEmpty()) {
            return ChatRespuesta.de("No me quedó claro tu nombre. ¿Me lo escribís? (por ejemplo: Juan Pérez)");
        }
        c.nombre = nombre.get();
        if (c.telefono == null) {
            c.paso = Paso.PIDIENDO_TELEFONO;
            return pedirTelefono(c);
        }
        c.paso = Paso.CONFIRMANDO;
        return pedirConfirmacion(c);
    }

    private ChatRespuesta pedirTelefono(Conversacion c) {
        return ChatRespuesta.de("Gracias, " + primerNombre(c.nombre) + ". ¿Me pasás un celular? "
                + "Lo usamos solo para avisarte si hay algún cambio.");
    }

    private ChatRespuesta pasoTelefono(Conversacion c, String mensaje) {
        Optional<String> telefono = interprete.telefono(mensaje);
        if (telefono.isEmpty()) {
            return ChatRespuesta.de("Ese número no me cierra 🤔. Escribilo con la característica, por ejemplo 11 4567 8901.");
        }
        c.telefono = telefono.get();
        c.paso = Paso.CONFIRMANDO;
        return pedirConfirmacion(c);
    }

    private ChatRespuesta pedirConfirmacion(Conversacion c) {
        Servicio servicio = disponibilidad.buscarServicio(c.servicioId);
        HorarioDisponible h = c.horarioElegido;
        String texto = "Te reservo esto:\n"
                + "✂️ " + servicio.getNombre() + " (" + pesos(servicio.getPrecio()) + ")\n"
                + "📅 " + capitalizar(fechaCorta(h.inicio().toLocalDate())) + " a las " + h.inicio().toLocalTime().format(HH_MM) + "\n"
                + "💈 Con " + h.barberoNombre() + "\n"
                + "👤 " + c.nombre + " · " + c.telefono + "\n"
                + "¿Lo confirmo?";
        return ChatRespuesta.de(texto, List.of("Sí, confirmar", "Cambiar horario", "No, gracias"));
    }

    private ChatRespuesta pasoConfirmar(Conversacion c, String mensaje) {
        String texto = interprete.normalizar(mensaje);

        if (interprete.esAfirmacion(mensaje)) {
            try {
                TurnoDTO turno = turnoService.reservar(new ReservaRequest(
                        c.servicioId, c.horarioElegido.barberoId(), c.horarioElegido.inicio(), c.nombre, c.telefono));
                String nombre = primerNombre(c.nombre);
                c.reiniciar();
                return new ChatRespuesta(
                        "¡Listo, " + nombre + "! Tu turno quedó confirmado ✅\n"
                                + "Tu código es #" + turno.id() + ". Guardalo por si necesitás cancelar.\n"
                                + "📍 Te esperamos en " + negocio.direccion() + ".",
                        List.of("Sacar otro turno", "Ver precios"),
                        turno);
            } catch (ReglaNegocioException e) {
                c.olvidarHorario();
                c.paso = Paso.RESERVANDO;
                return conPrefijo("Uy, ese horario se acaba de ocupar 😕.", siguientePregunta(c));
            }
        }
        if (texto.contains("horario") || texto.contains("hora") || texto.contains("dia")) {
            c.olvidarHorario();
            c.hora = null;
            if (texto.contains("dia")) c.fecha = null;
            c.paso = Paso.RESERVANDO;
            return siguientePregunta(c);
        }
        if (interprete.esNegacion(mensaje)) {
            c.reiniciar();
            return ChatRespuesta.de("Listo, no reservé nada. Si querés, arrancamos de nuevo.", opcionesDeInicio());
        }
        // ¿Cambió algún dato en vez de confirmar? ("mejor el viernes")
        if (completarDatos(c, mensaje)) {
            c.paso = Paso.RESERVANDO;
            return siguientePregunta(c);
        }
        return conPrefijo("¿Lo confirmo? Respondeme sí o no.", pedirConfirmacion(c));
    }

    // =====================================================================
    // Cancelación
    // =====================================================================

    private ChatRespuesta pasoCancelarCodigo(Conversacion c, String mensaje) {
        Optional<Long> codigo = interprete.codigo(mensaje);
        if (codigo.isEmpty()) {
            return ChatRespuesta.de("Necesito el número de código del turno, por ejemplo #12.", List.of("Volver al inicio"));
        }
        c.codigoACancelar = codigo.get();
        c.paso = Paso.CANCELANDO_TELEFONO;
        return ChatRespuesta.de("Para confirmar que es tuyo, ¿con qué celular lo reservaste?", List.of("Volver al inicio"));
    }

    private ChatRespuesta pasoCancelarTelefono(Conversacion c, String mensaje) {
        Optional<String> telefono = interprete.telefono(mensaje);
        if (telefono.isEmpty()) {
            return ChatRespuesta.de("Ese número no me cierra 🤔. Escribilo con la característica, por ejemplo 11 4567 8901.");
        }
        Long codigo = c.codigoACancelar;
        c.reiniciar();
        try {
            TurnoDTO turno = turnoService.cancelarPorCliente(codigo, telefono.get());
            return ChatRespuesta.de("Listo, cancelé tu turno de " + turno.servicioNombre().toLowerCase() + " "
                    + fechaCorta(turno.inicio().toLocalDate()) + " a las " + turno.inicio().toLocalTime().format(HH_MM)
                    + ". ¿Querés sacar otro?", List.of("Sacar otro turno", "No, gracias"));
        } catch (RecursoNoEncontradoException | ReglaNegocioException e) {
            return ChatRespuesta.de(e.getMessage() + " Si querés, probamos de nuevo.",
                    List.of("Cancelar un turno", "Volver al inicio"));
        }
    }

    // =====================================================================
    // Respuestas fijas
    // =====================================================================

    private ChatRespuesta bienvenida() {
        return ChatRespuesta.de("¡Hola! 👋 Soy el asistente de " + negocio.nombre() + ". "
                + "Te ayudo a sacar turno en un minuto.\n¿Qué te querés hacer?", opcionesDeInicio());
    }

    private ChatRespuesta noEntendi() {
        return ChatRespuesta.de("Perdón, no te entendí 🤔. Puedo ayudarte a sacar un turno, "
                + "contarte precios y horarios, o cancelar un turno.", opcionesDeInicio());
    }

    private ChatRespuesta precios() {
        String lista = servicios.findAll().stream()
                .map(s -> "• " + s.getNombre() + ": " + pesos(s.getPrecio()) + " (" + s.getDuracionMinutos() + " min)")
                .collect(Collectors.joining("\n"));
        return ChatRespuesta.de("Estos son nuestros precios:\n" + lista + "\n¿Te reservo alguno?", nombresDeServicios());
    }

    private ChatRespuesta informacion() {
        return ChatRespuesta.de("📍 Estamos en " + negocio.direccion() + ".\n🕒 Atendemos " + diasDeAtencion()
                        + ", de " + negocio.apertura().format(HH_MM) + " a " + negocio.cierre().format(HH_MM) + ".",
                List.of("Sacar turno", "Ver precios"));
    }

    private ChatRespuesta ofrecerDias(Conversacion c, String texto) {
        return ofrecerDias(c, texto, null);
    }

    private ChatRespuesta ofrecerDias(Conversacion c, String texto, LocalDate excluir) {
        List<String> dias = disponibilidad.proximosDiasConLugar(c.servicioId, 5).stream()
                .filter(d -> !d.equals(excluir))
                .limit(4)
                .map(this::etiquetaDia)
                .toList();
        if (dias.isEmpty()) {
            return ChatRespuesta.de("Por ahora no me quedan turnos libres en las próximas dos semanas 😕. "
                    + "Escribinos más adelante.", opcionesDeInicio());
        }
        return ChatRespuesta.de(texto, dias);
    }

    private List<String> opcionesDeInicio() {
        return List.of("Sacar turno", "Ver precios", "Horarios y dirección", "Cancelar un turno");
    }

    private List<String> nombresDeServicios() {
        return servicios.findAll().stream().map(Servicio::getNombre).toList();
    }

    // =====================================================================
    // Formato de textos
    // =====================================================================

    private String motivoFechaNoDisponible(LocalDate fecha) {
        if (fecha.isBefore(hoy())) {
            return "Esa fecha ya pasó 😅. ¿Para qué día lo querés?";
        }
        if (!negocio.abre(fecha.getDayOfWeek())) {
            return "Los " + plural(nombreDia(fecha.getDayOfWeek())) + " estamos cerrados. ¿Te sirve alguno de estos días?";
        }
        return "Por ahora tomo reservas hasta " + negocio.diasDeReserva() + " días adelante. ¿Te sirve alguno de estos?";
    }

    private String resumenCorto(Conversacion c) {
        Servicio servicio = disponibilidad.buscarServicio(c.servicioId);
        HorarioDisponible h = c.horarioElegido;
        return servicio.getNombre() + " " + fechaCorta(h.inicio().toLocalDate()) + " a las "
                + h.inicio().toLocalTime().format(HH_MM) + " con " + h.barberoNombre();
    }

    /** "hoy", "mañana" o "el jueves 20/11". */
    private String fechaCorta(LocalDate fecha) {
        if (fecha.equals(hoy())) return "hoy";
        if (fecha.equals(hoy().plusDays(1))) return "mañana";
        return "el " + nombreDia(fecha.getDayOfWeek()) + " " + fecha.format(DD_MM);
    }

    /** Etiqueta de botón: "Hoy", "Mañana" o "Jueves 20/11" (el intérprete sabe leer las tres). */
    private String etiquetaDia(LocalDate fecha) {
        if (fecha.equals(hoy())) return "Hoy";
        if (fecha.equals(hoy().plusDays(1))) return "Mañana";
        return capitalizar(nombreDia(fecha.getDayOfWeek())) + " " + fecha.format(DD_MM);
    }

    private String diasDeAtencion() {
        List<DayOfWeek> dias = negocio.diasLaborales().stream().sorted().toList();
        boolean consecutivos = dias.get(dias.size() - 1).getValue() - dias.get(0).getValue() == dias.size() - 1;
        if (consecutivos && dias.size() > 2) {
            return "de " + nombreDia(dias.get(0)) + " a " + nombreDia(dias.get(dias.size() - 1));
        }
        return "los " + dias.stream().map(this::nombreDia).collect(Collectors.joining(", "));
    }

    private String nombreDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, ES_AR);
    }

    /** "domingo" → "domingos", "lunes" → "lunes". */
    private static String plural(String dia) {
        return dia.endsWith("s") ? dia : dia + "s";
    }

    /** Si hay muchos horarios, elige algunos repartidos a lo largo del día. */
    private static List<HorarioDisponible> muestrear(List<HorarioDisponible> horarios) {
        if (horarios.size() <= MAX_HORARIOS_A_MOSTRAR) {
            return horarios;
        }
        List<HorarioDisponible> muestra = new ArrayList<>();
        for (int i = 0; i < MAX_HORARIOS_A_MOSTRAR; i++) {
            muestra.add(horarios.get(i * (horarios.size() - 1) / (MAX_HORARIOS_A_MOSTRAR - 1)));
        }
        return muestra;
    }

    private static List<String> horas(List<HorarioDisponible> horarios) {
        return horarios.stream().map(h -> h.inicio().toLocalTime().format(HH_MM)).toList();
    }

    private static List<String> conOtroDia(List<String> opciones) {
        List<String> resultado = new ArrayList<>(opciones);
        resultado.add("Otro día");
        return resultado;
    }

    private static ChatRespuesta conPrefijo(String prefijo, ChatRespuesta respuesta) {
        return new ChatRespuesta(prefijo + " " + respuesta.texto(), respuesta.opciones(), respuesta.turno());
    }

    private static String pesos(int monto) {
        return "$" + NumberFormat.getIntegerInstance(ES_AR).format(monto);
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String primerNombre(String nombre) {
        return nombre.split(" ")[0];
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private void borrarConversacionesViejas() {
        Instant limite = Instant.now(reloj).minus(EXPIRACION);
        conversaciones.values().removeIf(c -> c.ultimaActividad.isBefore(limite));
    }
}
