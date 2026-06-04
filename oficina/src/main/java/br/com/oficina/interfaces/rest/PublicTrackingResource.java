package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.PublicApprovalRequestDto;
import br.com.oficina.application.dto.PublicWorkOrderDto;
import br.com.oficina.application.dto.PublicWorkOrderStatusDto;
import br.com.oficina.application.service.WorkOrderService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
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
        description = "Permite ao cliente consultar o status de sua Ordem de Serviço pelo número. " +
                      "Retorna apenas número, status e marcos temporais — não expõe dados pessoais, " +
                      "placa, orçamento ou itens (evita vazamento por enumeração do número da OS)."
    )
    public PublicWorkOrderStatusDto getStatus(@PathParam("orderNumber") String orderNumber) {
        return PublicWorkOrderStatusDto.from(workOrderService.findByOrderNumber(orderNumber));
    }

    @POST
    @Path("/{orderNumber}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Aprovar orçamento",
        description = "Cliente aprova o orçamento e autoriza execução (AWAITING_APPROVAL → IN_EXECUTION). " +
                      "Exige CPF/CNPJ no corpo como prova de identidade — deve coincidir com o cliente da OS."
    )
    public PublicWorkOrderDto approve(@PathParam("orderNumber") String orderNumber,
                                      @Valid PublicApprovalRequestDto request) {
        return PublicWorkOrderDto.from(
            workOrderService.approveByOrderNumber(orderNumber, request.clientCpfCnpj())
        );
    }

    @POST
    @Path("/{orderNumber}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Rejeitar orçamento",
        description = "Cliente rejeita o orçamento (AWAITING_APPROVAL → CANCELLED). " +
                      "Exige CPF/CNPJ no corpo como prova de identidade — deve coincidir com o cliente da OS."
    )
    public PublicWorkOrderDto reject(@PathParam("orderNumber") String orderNumber,
                                     @Valid PublicApprovalRequestDto request) {
        return PublicWorkOrderDto.from(
            workOrderService.rejectByOrderNumber(orderNumber, request.clientCpfCnpj())
        );
    }
}
