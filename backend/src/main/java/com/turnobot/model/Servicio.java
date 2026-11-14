package com.turnobot.model;

import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private int duracionMinutos;

    /** Precio en pesos, sin decimales. Para plata nunca usar double (redondea mal). */
    private int precio;

    /** Palabras con las que un cliente puede pedir este servicio en el chat. */
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> palabrasClave;

    protected Servicio() {
    }

    public Servicio(String nombre, int duracionMinutos, int precio, List<String> palabrasClave) {
        this.nombre = nombre;
        this.duracionMinutos = duracionMinutos;
        this.precio = precio;
        this.palabrasClave = palabrasClave;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public int getDuracionMinutos() { return duracionMinutos; }
    public int getPrecio() { return precio; }
    public List<String> getPalabrasClave() { return palabrasClave; }
}
