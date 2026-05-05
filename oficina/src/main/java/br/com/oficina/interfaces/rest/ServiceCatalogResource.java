package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.ServiceItemRequestDto;
import br.com.oficina.application.dto.ServiceItemResponseDto;
import br.com.oficina.application.service.ServiceItemService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.net.URI;
import java.util.List;

@Path("/admin/services")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "MECHANIC"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Catálogo de Serviços", description = "Gestão do catálogo de serviços da oficina")
public class ServiceCatalogResource {

    ServiceItemService serviceItemService;

    public ServiceCatalogResource(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @GET
    @Operation(summary = "Listar todos os serviços")
    public List<ServiceItemResponseDto> listAll() {
        return serviceItemService.listAll();
    }

    @GET
    @Path("/active")
    @Operation(summary = "Listar serviços ativos")
    public List<ServiceItemResponseDto> listActive() {
        return serviceItemService.listActive();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar serviço por ID")
    public ServiceItemResponseDto findById(@PathParam("id") Long id) {
        return serviceItemService.findById(id);
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(summary = "Cadastrar novo serviço")
    public Response create(@Valid ServiceItemRequestDto dto) {
        ServiceItemResponseDto created = serviceItemService.create(dto);
        return Response.created(URI.create("/admin/services/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Atualizar serviço")
    public ServiceItemResponseDto update(@PathParam("id") Long id, @Valid ServiceItemRequestDto dto) {
        return serviceItemService.update(id, dto);
    }

    @PATCH
    @Path("/{id}/deactivate")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Desativar serviço")
    public ServiceItemResponseDto deactivate(@PathParam("id") Long id) {
        return serviceItemService.deactivate(id);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Excluir serviço")
    public Response delete(@PathParam("id") Long id) {
        serviceItemService.delete(id);
        return Response.noContent().build();
    }
}
