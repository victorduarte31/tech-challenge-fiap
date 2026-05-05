package br.com.oficina;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@ApplicationPath("/")
@OpenAPIDefinition(
    info = @Info(
        title = "Oficina Mecânica API",
        description = "Sistema Integrado de Atendimento e Execução de Serviços - MVP Fase 1",
        version = "1.0.0"
    )
)
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT Bearer Token — use POST /auth/login para obter o token"
)
public class OficinaMecanicaApplication extends Application {
}
