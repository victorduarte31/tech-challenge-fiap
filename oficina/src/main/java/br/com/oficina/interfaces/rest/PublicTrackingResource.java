package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.PublicApprovalRequestDto;
import br.com.oficina.application.dto.PublicWorkOrderDto;
import br.com.oficina.application.dto.PublicWorkOrderStatusDto;
import br.com.oficina.application.ports.in.ApproveBudgetUseCase;
import br.com.oficina.application.ports.in.ListWorkOrdersUseCase;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/public/work-orders")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@Tag(name = "Acompanhamento Público", description = "Endpoint público para clientes acompanharem o status da OS")
public class PublicTrackingResource {

    private final ListWorkOrdersUseCase listWorkOrders;
    private final ApproveBudgetUseCase approveBudget;

    public PublicTrackingResource(ListWorkOrdersUseCase listWorkOrders, ApproveBudgetUseCase approveBudget) {
        this.listWorkOrders = listWorkOrders;
        this.approveBudget = approveBudget;
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
        return PublicWorkOrderStatusDto.from(listWorkOrders.findByOrderNumber(orderNumber));
    }

    @POST
    @Path("/{orderNumber}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Aprovar orçamento",
        description = "Cliente aprova o orçamento e autoriza execução (AWAITING_APPROVAL → IN_EXECUTION). " +
                      "Exige, no corpo, o CPF/CNPJ do cliente da OS e o código de autorização de uso " +
                      "único enviado por e-mail no momento do envio do orçamento. O código expira ao " +
                      "ser usado; um reenvio do orçamento gera um novo e invalida o anterior."
    )
    public PublicWorkOrderDto approve(@PathParam("orderNumber") String orderNumber,
                                      @Valid @NotNull(message = "Corpo da requisição é obrigatório") PublicApprovalRequestDto request) {
        return PublicWorkOrderDto.from(
            approveBudget.approveByOrderNumber(orderNumber, request.clientCpfCnpj(), request.approvalToken())
        );
    }

    @POST
    @Path("/{orderNumber}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Rejeitar orçamento",
        description = "Cliente rejeita o orçamento (AWAITING_APPROVAL → CANCELLED). " +
                      "Exige, no corpo, o CPF/CNPJ do cliente da OS e o código de autorização de uso " +
                      "único enviado por e-mail no momento do envio do orçamento."
    )
    public PublicWorkOrderDto reject(@PathParam("orderNumber") String orderNumber,
                                     @Valid @NotNull(message = "Corpo da requisição é obrigatório") PublicApprovalRequestDto request) {
        return PublicWorkOrderDto.from(
            approveBudget.rejectByOrderNumber(orderNumber, request.clientCpfCnpj(), request.approvalToken())
        );
    }
}
