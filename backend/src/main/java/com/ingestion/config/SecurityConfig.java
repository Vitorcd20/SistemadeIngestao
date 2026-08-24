package com.ingestion.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Handler simples (sem XOR): é uma API JSON pura, sem forms server-rendered —
                // o SPA lê o cookie XSRF-TOKEN cru e devolve como header. O handler padrão
                // (proteção BREACH) faz XOR no valor do cookie, o que nunca bateria com o
                // que o browser mandou de volta.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // O token CSRF do Spring Security 6 é lazy: um GET sozinho não escreve
                // o cookie XSRF-TOKEN a menos que algo durante a request resolva o token.
                // Sem esse filtro forçando essa resolução, o primeiro GET do SPA (ex:
                // /api/auth/me no load) não deixa cookie pra ecoar no POST seguinte, e o
                // POST é rejeitado — confirmado numa instância rodando: o cookie só
                // aparecia na resposta do POST rejeitado, nunca no GET de priming. É o
                // fix documentado (os próprios exemplos de integração CSRF+SPA do Spring
                // Security usam esse filtro).
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // /error também precisa ser permitAll: o Spring Boot registra o
                        // filtro de segurança pro dispatch de ERROR também, não só REQUEST
                        // — então uma falha de validação (MethodArgumentNotValidException,
                        // resolvida certo pra 400 pelo Spring MVC) tem o forward interno
                        // pra /error bloqueado e sobrescrito com 401 pra um caller anônimo
                        // se isso não for isento. Confirmado numa instância rodando: sem
                        // isso, um /api/auth/register inválido voltava 401 com corpo vazio
                        // em vez de 400 com o erro de validação.
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                // API JSON, não um app de browser com tela de login — request não
                // autenticada pra endpoint protegido deve dar 401, não redirect.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));

        return http.build();
    }

    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // Chamar .getToken() é o que dispara o supplier lazy a salvar (e
                // escrever o cookie) — só estar presente como attribute da request
                // não basta.
                csrfToken.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
