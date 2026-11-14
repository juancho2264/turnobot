package com.turnobot.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, JwtFiltro jwtFiltro) throws Exception {
        http
                // API sin sesiones ni cookies: CSRF no aplica porque el token viaja en un header
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // para la consola H2 en desarrollo
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                // Sin token → 401 (sin esto Spring devolvería 403, que confunde)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Un único usuario administrador, definido por configuración. La contraseña se guarda hasheada. */
    @Bean
    public UserDetailsService usuarios(@Value("${admin.usuario}") String usuario,
                                       @Value("${admin.password}") String password,
                                       PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(User.withUsername(usuario)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService usuarios, PasswordEncoder encoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(usuarios);
        proveedor.setPasswordEncoder(encoder);
        return new ProviderManager(proveedor);
    }

    /** Qué sitios pueden llamar a esta API desde el navegador (el frontend). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${cors.origenes}") String origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
}
