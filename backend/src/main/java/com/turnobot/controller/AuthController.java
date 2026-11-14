package com.turnobot.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.turnobot.dto.LoginRequest;
import com.turnobot.dto.LoginResponse;
import com.turnobot.security.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager autenticador;
    private final JwtService jwt;

    public AuthController(AuthenticationManager autenticador, JwtService jwt) {
        this.autenticador = autenticador;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido) {
        try {
            Authentication auth = autenticador.authenticate(
                    new UsernamePasswordAuthenticationToken(pedido.usuario(), pedido.password()));
            return new LoginResponse(jwt.generarToken(auth.getName()), auth.getName());
        } catch (BadCredentialsException e) {
            // Mismo mensaje si falla el usuario o la contraseña: no le damos pistas a un atacante
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
        }
    }
}
