package com.ingestion.controller;

import com.ingestion.repository.UserRepository;
import com.ingestion.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record Credentials(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required")
            @Size(min = MIN_PASSWORD_LENGTH, message = "Password must be at least " + MIN_PASSWORD_LENGTH + " characters")
            String password
    ) {
    }

    public record UserResponse(String username) {
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody Credentials credentials,
                                                  HttpServletRequest request, HttpServletResponse response) {
        // UserRepository normaliza (trim/lowercase) internamente, então esse check
        // e o insert abaixo concordam na mesma identidade. Ainda não é atômico com
        // o insert — um register duplicado concorrente cai na violação de
        // constraint única, que o GlobalExceptionHandler mapeia pra um 409 limpo
        // em vez de 500.
        if (userRepository.existsByUsername(credentials.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        userRepository.createUser(credentials.username(), passwordEncoder.encode(credentials.password()));
        return authenticateAndRespond(credentials, request, response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody Credentials credentials,
                                               HttpServletRequest request, HttpServletResponse response) {
        return authenticateAndRespond(credentials, request, response, HttpStatus.OK);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        return new UserResponse(principal.getUsername());
    }

    // Chamado por register (após criar conta) e login — ambos só autenticam e abrem sessão.
    private ResponseEntity<UserResponse> authenticateAndRespond(Credentials credentials,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response,
                                                                  HttpStatus successStatus) {
        try {
            establishSession(credentials.username(), credentials.password(), request, response);
        } catch (AuthenticationException e) {
            // Mensagem genérica de propósito — não confirma se o username existe,
            // então não dá pra enumerar contas com isso.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return ResponseEntity.status(successStatus).body(new UserResponse(credentials.username()));
    }

    private void establishSession(String username, String password,
                                   HttpServletRequest request, HttpServletResponse response) {
        Authentication authResult = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        // Rotaciona o id da sessão pós-autenticação (mesma coisa que o formLogin
        // padrão do Spring Security faz via ChangeSessionIdAuthenticationStrategy).
        // Esse fluxo manual com saveContext() não faz isso sozinho — sem essa
        // linha, um id fixado por um atacante antes do login da vítima iria
        // direto pra uma sessão autenticada.
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
