package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.*;
import br.com.oficina.application.ports.in.ChangeWorkOrderStatusUseCase;
import br.com.oficina.application.ports.in.CreateWorkOrderUseCase;
import br.com.oficina.application.ports.in.ListWorkOrdersUseCase;
import br.com.oficina.application.ports.in.ManageWorkOrderItemsUseCase;
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

    private final CreateWorkOrderUseCase createWorkOrder;
    private final ListWorkOrdersUseCase listWorkOrders;
    private final ManageWorkOrderItemsUseCase manageItems;
    private final ChangeWorkOrderStatusUseCase changeStatus;

    public WorkOrderResource(CreateWorkOrderUseCase createWorkOrder,
                             ListWorkOrdersUseCase listWorkOrders,
                             ManageWorkOrderItemsUseCase manageItems,
                             ChangeWorkOrderStatusUseCase changeStatus) {
        this.createWorkOrder = createWorkOrder;
        this.listWorkOrders = listWorkOrders;
        this.manageItems = manageItems;
        this.changeStatus = changeStatus;
    }

    @GET
    @Operation(summary = "Listar OS (paginado; filtro opcional por status)")
    public List<WorkOrderResponseDto> listAll(@QueryParam("status") WorkOrderStatus status,
                                              @QueryParam("page") @DefaultValue("0") int page,
                                              @QueryParam("size") @DefaultValue("20") int size) {
        if (status != null) {
            return listWorkOrders.listByStatus(status, page, size);
        }
        return listWorkOrders.listAll(page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar OS por ID")
    public WorkOrderResponseDto findById(@PathParam("id") Long id) {
        return listWorkOrders.findById(id);
    }

    @GET
    @Path("/number/{orderNumber}")
    @Operation(summary = "Buscar OS por número")
    public WorkOrderResponseDto findByOrderNumber(@PathParam("orderNumber") String orderNumber) {
        return listWorkOrders.findByOrderNumber(orderNumber);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Criar nova OS")
    public Response create(@Valid @NotNull(message = "Corpo da requisição é obrigatório") WorkOrderCreateDto dto) {
        WorkOrderResponseDto created = createWorkOrder.create(dto);
        return Response.created(URI.create("/admin/work-orders/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/{id}/services")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Adicionar serviço à OS")
    public WorkOrderResponseDto addService(@PathParam("id") Long id, @Valid @NotNull(message = "Corpo da requisição é obrigatório") WorkOrderServiceDto dto) {
        return manageItems.addService(id, dto);
    }

    @DELETE
    @Path("/{id}/services/{serviceLineId}")
    @Operation(summary = "Remover serviço da OS")
    public WorkOrderResponseDto removeService(@PathParam("id") Long id,
                                              @PathParam("serviceLineId") Long serviceLineId) {
        return manageItems.removeService(id, serviceLineId);
    }

    @POST
    @Path("/{id}/parts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Adicionar peça à OS")
    public WorkOrderResponseDto addPart(@PathParam("id") Long id, @Valid @NotNull(message = "Corpo da requisição é obrigatório") WorkOrderPartDto dto) {
        return manageItems.addPart(id, dto);
    }

    @DELETE
    @Path("/{id}/parts/{partLineId}")
    @Operation(summary = "Remover peça da OS")
    public WorkOrderResponseDto removePart(@PathParam("id") Long id,
                                           @PathParam("partLineId") Long partLineId) {
        return manageItems.removePart(id, partLineId);
    }

    @PATCH
    @Path("/{id}/start-diagnosis")
    @Operation(summary = "Iniciar diagnóstico (RECEIVED → IN_DIAGNOSIS)")
    public WorkOrderResponseDto startDiagnosis(@PathParam("id") Long id) {
        return changeStatus.startDiagnosis(id);
    }

    @PATCH
    @Path("/{id}/send-for-approval")
    @Operation(summary = "Enviar orçamento para aprovação (IN_DIAGNOSIS → AWAITING_APPROVAL)")
    public WorkOrderResponseDto sendForApproval(@PathParam("id") Long id) {
        return changeStatus.sendForApproval(id);
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
        return changeStatus.approve(id);
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
        return changeStatus.reject(id);
    }

    @PATCH
    @Path("/{id}/complete")
    @Operation(summary = "Marcar OS como concluída (IN_EXECUTION → FINISHED)")
    public WorkOrderResponseDto complete(@PathParam("id") Long id) {
        return changeStatus.complete(id);
    }

    @PATCH
    @Path("/{id}/deliver")
    @Operation(summary = "Registrar entrega do veículo (FINISHED → DELIVERED)")
    public WorkOrderResponseDto deliver(@PathParam("id") Long id) {
        return changeStatus.deliver(id);
    }

    @PATCH
    @Path("/{id}/cancel")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Cancelar OS (somente ADMIN)")
    public WorkOrderResponseDto cancel(@PathParam("id") Long id) {
        return changeStatus.cancel(id);
    }
}
