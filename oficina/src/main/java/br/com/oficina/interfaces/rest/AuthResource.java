package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.LoginRequestDto;
import br.com.oficina.application.dto.LoginResponseDto;
import br.com.oficina.infrastructure.security.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Autenticação", description = "Endpoints de autenticação e geração de tokens JWT")
public class AuthResource {

    AuthService authService;

    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    @POST
    @Path("/login")
    @Operation(summary = "Realizar login", description = "Autentica o usuário e retorna um token JWT")
    public Response login(@Valid @NotNull(message = "Corpo da requisição é obrigatório") LoginRequestDto request) {
        LoginResponseDto response = authService.login(request);
        return Response.ok(response).build();
    }
}
