package com.turnobot.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Se ejecuta en cada request: si trae "Authorization: Bearer <token>" válido,
 * marca al usuario como autenticado para el resto de la cadena.
 */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwt;

    public JwtFiltro(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIJO)) {
            jwt.usuarioDelToken(header.substring(PREFIJO.length())).ifPresent(usuario -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        cadena.doFilter(request, response);
    }
}
