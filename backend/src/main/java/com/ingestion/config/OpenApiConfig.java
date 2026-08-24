package com.ingestion.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema de Ingestão — API")
                        .version("1.0")
                        .description("""
                                API REST para ingestão, consulta e agregação de transações financeiras via CSV.

                                **Autenticação:** baseada em sessão (cookie `JSESSIONID`). Faça login via \
                                `POST /api/auth/login` antes de chamar os endpoints protegidos. \
                                A Swagger UI enviará o cookie automaticamente nas requisições subsequentes.
                                """)
                        .contact(new Contact().name("Vitor").email("vitorvieeira12008@gmail.com")))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Cookie de sessão obtido após login em `POST /api/auth/login`")));
    }
}
