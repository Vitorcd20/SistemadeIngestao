package com.ingestion.controller;

import com.ingestion.repository.UserRepository;
import com.ingestion.security.AppUserDetails;
import com.ingestion.service.LoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Autenticação", description = "Registro, login e logout de usuários")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
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

    @Operation(summary = "Registrar novo usuário",
            responses = {
                @ApiResponse(responseCode = "201", description = "Usuário criado e sessão iniciada"),
                @ApiResponse(responseCode = "409", description = "Username já está em uso"),
                @ApiResponse(responseCode = "429", description = "Conta temporariamente bloqueada")
            })
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody Credentials credentials,
                                                  HttpServletRequest request, HttpServletResponse response) {
        if (userRepository.existsByUsername(credentials.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        userRepository.createUser(credentials.username(), passwordEncoder.encode(credentials.password()));
        return authenticateAndRespond(credentials, request, response, HttpStatus.CREATED);
    }

    @Operation(summary = "Fazer login",
            description = "Autentica o usuário e inicia uma sessão. O cookie `JSESSIONID` retornado deve ser enviado nas demais requisições.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Login bem-sucedido"),
                @ApiResponse(responseCode = "401", description = "Credenciais inválidas"),
                @ApiResponse(responseCode = "429", description = "Conta temporariamente bloqueada por excesso de tentativas")
            })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody Credentials credentials,
                                               HttpServletRequest request, HttpServletResponse response) {
        return authenticateAndRespond(credentials, request, response, HttpStatus.OK);
    }

    @Operation(summary = "Usuário autenticado", description = "Retorna o username do usuário da sessão atual")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        return new UserResponse(principal.getUsername());
    }

    private ResponseEntity<UserResponse> authenticateAndRespond(Credentials credentials,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response,
                                                                  HttpStatus successStatus) {
        if (loginAttemptService.isBlocked(credentials.username())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Account temporarily locked due to too many failed attempts. Try again later.");
        }
        try {
            establishSession(credentials.username(), credentials.password(), request, response);
            loginAttemptService.recordSuccess(credentials.username());
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(credentials.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return ResponseEntity.status(successStatus).body(new UserResponse(credentials.username()));
    }

    private void establishSession(String username, String password,
                                   HttpServletRequest request, HttpServletResponse response) {
        Authentication authResult = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
