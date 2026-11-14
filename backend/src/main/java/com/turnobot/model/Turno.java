package com.turnobot.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
// El índice acelera la consulta más frecuente: "turnos de este barbero en este rango"
@Table(indexes = @Index(columnList = "barbero_id, inicio"))
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Barbero barbero;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Servicio servicio;

    private String clienteNombre;
    private String clienteTelefono;

    private LocalDateTime inicio;
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING) // guarda "CONFIRMADO" y no 0: si reordenás el enum, no se rompe nada
    private EstadoTurno estado = EstadoTurno.CONFIRMADO;

    private LocalDateTime creadoEn;

    protected Turno() {
    }

    public Turno(Barbero barbero, Servicio servicio, String clienteNombre, String clienteTelefono,
                 LocalDateTime inicio, LocalDateTime creadoEn) {
        this.barbero = barbero;
        this.servicio = servicio;
        this.clienteNombre = clienteNombre;
        this.clienteTelefono = clienteTelefono;
        this.inicio = inicio;
        this.fin = inicio.plusMinutes(servicio.getDuracionMinutos());
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Barbero getBarbero() { return barbero; }
    public Servicio getServicio() { return servicio; }
    public String getClienteNombre() { return clienteNombre; }
    public String getClienteTelefono() { return clienteTelefono; }
    public LocalDateTime getInicio() { return inicio; }
    public LocalDateTime getFin() { return fin; }
    public EstadoTurno getEstado() { return estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    public void setEstado(EstadoTurno estado) { this.estado = estado; }
}
