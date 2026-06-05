package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.*;
import br.com.oficina.application.service.WorkOrderService;
import br.com.oficina.domain.model.WorkOrderStatus;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.net.URI;
import java.util.List;

@Path("/admin/work-orders")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "MECHANIC"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ordens de Serviço", description = "Gestão completa de Ordens de Serviço")
public class WorkOrderResource {

    WorkOrderService workOrderService;

    public WorkOrderResource(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GET
    @Operation(summary = "Listar OS (paginado; filtro opcional por status)")
    public List<WorkOrderResponseDto> listAll(@QueryParam("status") WorkOrderStatus status,
                                              @QueryParam("page") @DefaultValue("0") int page,
                                              @QueryParam("size") @DefaultValue("20") int size) {
        if (status != null) {
            return workOrderService.listByStatus(status, page, size);
        }
        return workOrderService.listAll(page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar OS por ID")
    public WorkOrderResponseDto findById(@PathParam("id") Long id) {
        return workOrderService.findById(id);
    }

    @GET
    @Path("/number/{orderNumber}")
    @Operation(summary = "Buscar OS por número")
    public WorkOrderResponseDto findByOrderNumber(@PathParam("orderNumber") String orderNumber) {
        return workOrderService.findByOrderNumber(orderNumber);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Criar nova OS")
    public Response create(@Valid @NotNull(message = "Corpo da requisição é obrigatório") WorkOrderCreateDto dto) {
        WorkOrderResponseDto created = workOrderService.create(dto);
        return Response.created(URI.create("/admin/work-orders/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/{id}/services")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Adicionar serviço à OS")
    public WorkOrderResponseDto addService(@PathParam("id") Long id, @Valid @NotNull(message = "Corpo da requisição é obrigatório") WorkOrderServiceDto dto) {
        return workOrderService.addService(id, dto);
    }

    @DELETE
    @Path("/{id}/services/{serviceLineId}")
    @Operation(summary = "Remover serviço da OS")
    public WorkOrderResponseDto removeService(@PathParam("id") Long id,
                                              @PathParam("serviceLineId") Long serviceLineId) {
        return workOrderService.removeService(id, serviceLineId);
    }

    @POST
    @Path("/{id}/parts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Adicionar peça à OS")
    public WorkOrderResponseDto addPart(@PathParam("id") Long id, @Valid @NotNull(message = "Corpo da requisição é obrigatório") WorkOrderPartDto dto) {
        return workOrderService.addPart(id, dto);
    }

    @DELETE
    @Path("/{id}/parts/{partLineId}")
    @Operation(summary = "Remover peça da OS")
    public WorkOrderResponseDto removePart(@PathParam("id") Long id,
                                           @PathParam("partLineId") Long partLineId) {
        return workOrderService.removePart(id, partLineId);
    }

    @PATCH
    @Path("/{id}/start-diagnosis")
    @Operation(summary = "Iniciar diagnóstico (RECEIVED → IN_DIAGNOSIS)")
    public WorkOrderResponseDto startDiagnosis(@PathParam("id") Long id) {
        return workOrderService.startDiagnosis(id);
    }

    @PATCH
    @Path("/{id}/send-for-approval")
    @Operation(summary = "Enviar orçamento para aprovação (IN_DIAGNOSIS → AWAITING_APPROVAL)")
    public WorkOrderResponseDto sendForApproval(@PathParam("id") Long id) {
        return workOrderService.sendForApproval(id);
    }

    @PATCH
    @Path("/{id}/approve")
    @Operation(
        summary = "Registrar aprovação presencial do cliente (AWAITING_APPROVAL → IN_EXECUTION)",
        description = "Endpoint administrativo destinado ao atendente registrar uma aprovação " +
                      "feita pelo cliente PRESENCIALMENTE ou por telefone. " +
                      "Para aprovação remota pelo próprio cliente, usar POST /public/work-orders/{orderNumber}/approve."
    )
    public WorkOrderResponseDto approve(@PathParam("id") Long id) {
        return workOrderService.approve(id);
    }

    @PATCH
    @Path("/{id}/reject")
    @Operation(
        summary = "Registrar rejeição presencial do cliente (AWAITING_APPROVAL → CANCELLED)",
        description = "Endpoint administrativo destinado ao atendente registrar uma rejeição " +
                      "feita pelo cliente PRESENCIALMENTE ou por telefone. " +
                      "Para rejeição remota pelo próprio cliente, usar POST /public/work-orders/{orderNumber}/reject."
    )
    public WorkOrderResponseDto reject(@PathParam("id") Long id) {
        return workOrderService.reject(id);
    }

    @PATCH
    @Path("/{id}/complete")
    @Operation(summary = "Marcar OS como concluída (IN_EXECUTION → FINISHED)")
    public WorkOrderResponseDto complete(@PathParam("id") Long id) {
        return workOrderService.complete(id);
    }

    @PATCH
    @Path("/{id}/deliver")
    @Operation(summary = "Registrar entrega do veículo (FINISHED → DELIVERED)")
    public WorkOrderResponseDto deliver(@PathParam("id") Long id) {
        return workOrderService.deliver(id);
    }

    @PATCH
    @Path("/{id}/cancel")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Cancelar OS (somente ADMIN)")
    public WorkOrderResponseDto cancel(@PathParam("id") Long id) {
        return workOrderService.cancel(id);
    }
}
