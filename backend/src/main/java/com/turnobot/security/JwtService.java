package com.turnobot.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Crea y valida tokens JWT.
 * Un JWT es un texto firmado que dice "este es el usuario X y vale hasta tal hora".
 * El servidor no guarda sesiones: le alcanza con verificar la firma.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final Duration duracion;
    private final Clock reloj;

    public JwtService(@Value("${jwt.secreto}") String secreto,
                      @Value("${jwt.expiracion-minutos}") long minutos,
                      Clock reloj) {
        if (secreto.length() < 32) {
            throw new IllegalStateException("jwt.secreto debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofMinutes(minutos);
        this.reloj = reloj;
    }

    public String generarToken(String usuario) {
        Date ahora = Date.from(reloj.instant());
        return Jwts.builder()
                .subject(usuario)
                .issuedAt(ahora)
                .expiration(Date.from(reloj.instant().plus(duracion)))
                .signWith(clave)
                .compact();
    }

    /** Devuelve el usuario si el token es válido y no venció; vacío en cualquier otro caso. */
    public Optional<String> usuarioDelToken(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(clave)
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
