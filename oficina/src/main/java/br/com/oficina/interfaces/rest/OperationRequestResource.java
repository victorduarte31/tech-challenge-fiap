package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.CancellationRequestDto;
import br.com.oficina.application.dto.OperationRequestResponseDto;
import br.com.oficina.application.dto.RequestDecisionDto;
import br.com.oficina.application.dto.StockAdjustmentRequestDto;
import br.com.oficina.application.service.OperationRequestService;
import br.com.oficina.domain.model.RequestStatus;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Path("/admin/requests")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "ATTENDANT"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Solicitações", description = "Fluxo de aprovação (maker-checker) para operações sensíveis")
public class OperationRequestResource {

    OperationRequestService service;

    public OperationRequestResource(OperationRequestService service) {
        this.service = service;
    }

    @POST
    @Path("/stock-adjustment")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Solicitar ajuste de estoque (atendente) — requer aprovação do dono")
    public Response requestStockAdjustment(
            @Valid @NotNull(message = "Corpo da requisição é obrigatório") StockAdjustmentRequestDto dto,
            @Context SecurityContext ctx) {
        OperationRequestResponseDto created = service.requestStockAdjustment(dto, username(ctx));
        return Response.created(URI.create("/admin/requests/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/cancellation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Solicitar cancelamento de OS (atendente) — requer aprovação do dono")
    public Response requestCancellation(
            @Valid @NotNull(message = "Corpo da requisição é obrigatório") CancellationRequestDto dto,
            @Context SecurityContext ctx) {
        OperationRequestResponseDto created = service.requestCancellation(dto, username(ctx));
        return Response.created(URI.create("/admin/requests/" + created.id())).entity(created).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Listar solicitações (filtro opcional por status) — somente dono")
    public List<OperationRequestResponseDto> list(@QueryParam("status") RequestStatus status,
                                                  @QueryParam("page") @DefaultValue("0") int page,
                                                  @QueryParam("size") @DefaultValue("20") int size) {
        return service.list(status, page, size);
    }

    @GET
    @Path("/pending-count")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Quantidade de solicitações pendentes — somente dono")
    public Map<String, Long> pendingCount() {
        return Map.of("pending", service.countPending());
    }

    @POST
    @Path("/{id}/approve")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Aprovar e executar a solicitação — somente dono")
    public OperationRequestResponseDto approve(@PathParam("id") Long id, @Context SecurityContext ctx) {
        return service.approve(id, username(ctx));
    }

    @POST
    @Path("/{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Rejeitar a solicitação — somente dono")
    public OperationRequestResponseDto reject(@PathParam("id") Long id,
                                              RequestDecisionDto decision,
                                              @Context SecurityContext ctx) {
        String note = decision != null ? decision.note() : null;
        return service.reject(id, username(ctx), note);
    }

    private String username(SecurityContext ctx) {
        return ctx.getUserPrincipal() != null ? ctx.getUserPrincipal().getName() : "desconhecido";
    }
}
