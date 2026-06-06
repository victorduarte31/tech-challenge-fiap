package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.VehicleRequestDto;
import br.com.oficina.application.dto.VehicleResponseDto;
import br.com.oficina.application.service.VehicleService;
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

@Path("/admin/vehicles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "ATTENDANT"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Veículos", description = "Gestão de veículos")
public class VehicleResource {

    VehicleService vehicleService;

    public VehicleResource(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GET
    @Operation(summary = "Listar veículos (paginado)")
    public List<VehicleResponseDto> listAll(@QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("20") int size) {
        return vehicleService.listAll(page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar veículo por ID")
    public VehicleResponseDto findById(@PathParam("id") Long id) {
        return vehicleService.findById(id);
    }

    @GET
    @Path("/client/{clientId}")
    @Operation(summary = "Listar veículos por cliente")
    public List<VehicleResponseDto> findByClientId(@PathParam("clientId") Long clientId) {
        return vehicleService.findByClientId(clientId);
    }

    @POST
    @RolesAllowed({"ADMIN", "ATTENDANT"})
    @Operation(summary = "Cadastrar novo veículo")
    public Response create(@Valid @NotNull(message = "Corpo da requisição é obrigatório") VehicleRequestDto dto) {
        VehicleResponseDto created = vehicleService.create(dto);
        return Response.created(URI.create("/admin/vehicles/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "ATTENDANT"})
    @Operation(summary = "Atualizar veículo")
    public VehicleResponseDto update(@PathParam("id") Long id, @Valid @NotNull(message = "Corpo da requisição é obrigatório") VehicleRequestDto dto) {
        return vehicleService.update(id, dto);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Excluir veículo")
    public Response delete(@PathParam("id") Long id) {
        vehicleService.delete(id);
        return Response.noContent().build();
    }
}
