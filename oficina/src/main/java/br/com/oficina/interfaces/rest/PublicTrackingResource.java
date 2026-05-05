package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.WorkOrderResponseDto;
import br.com.oficina.application.service.WorkOrderService;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/public/work-orders")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@Tag(name = "Acompanhamento Público", description = "Endpoint público para clientes acompanharem o status da OS")
public class PublicTrackingResource {

    WorkOrderService workOrderService;

    public PublicTrackingResource(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GET
    @Path("/{orderNumber}/status")
    @Operation(
        summary = "Consultar status da OS",
        description = "Permite ao cliente consultar o status e detalhes de sua Ordem de Serviço pelo número"
    )
    public WorkOrderResponseDto getStatus(@PathParam("orderNumber") String orderNumber) {
        return workOrderService.findByOrderNumber(orderNumber);
    }

    @POST
    @Path("/{id}/approve")
    @Operation(
        summary = "Aprovar orçamento",
        description = "Cliente aprova o orçamento e autoriza execução (AWAITING_APPROVAL → IN_EXECUTION)"
    )
    public WorkOrderResponseDto approve(@PathParam("id") Long id) {
        return workOrderService.approve(id);
    }

    @POST
    @Path("/{id}/reject")
    @Operation(
        summary = "Rejeitar orçamento",
        description = "Cliente rejeita o orçamento (AWAITING_APPROVAL → CANCELLED)"
    )
    public WorkOrderResponseDto reject(@PathParam("id") Long id) {
        return workOrderService.reject(id);
    }
}
