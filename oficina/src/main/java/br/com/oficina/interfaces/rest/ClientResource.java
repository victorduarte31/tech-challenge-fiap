package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.ClientRequestDto;
import br.com.oficina.application.dto.ClientResponseDto;
import br.com.oficina.application.service.ClientService;
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

@Path("/admin/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "MECHANIC"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Clientes", description = "Gestão de clientes")
public class ClientResource {

    ClientService clientService;

    public ClientResource(ClientService clientService) {
        this.clientService = clientService;
    }

    @GET
    @Operation(summary = "Listar clientes (paginado)",
               description = "Total de registros, ignorando a paginação, no cabeçalho X-Total-Count.")
    public Response listAll(@QueryParam("page") @DefaultValue("0") int page,
                            @QueryParam("size") @DefaultValue("20") int size) {
        return Response.ok(clientService.listAll(page, size))
            .header(WorkOrderResource.TOTAL_COUNT_HEADER, clientService.countAll())
            .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar cliente por ID")
    public ClientResponseDto findById(@PathParam("id") Long id) {
        return clientService.findById(id);
    }

    @GET
    @Path("/cpfcnpj/{cpfCnpj}")
    @Operation(summary = "Buscar cliente por CPF/CNPJ")
    public ClientResponseDto findByCpfCnpj(@PathParam("cpfCnpj") String cpfCnpj) {
        return clientService.findByCpfCnpj(cpfCnpj);
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(summary = "Cadastrar novo cliente")
    public Response create(@Valid @NotNull(message = "Corpo da requisição é obrigatório") ClientRequestDto dto) {
        ClientResponseDto created = clientService.create(dto);
        return Response.created(URI.create("/admin/clients/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Atualizar cliente")
    public ClientResponseDto update(@PathParam("id") Long id, @Valid @NotNull(message = "Corpo da requisição é obrigatório") ClientRequestDto dto) {
        return clientService.update(id, dto);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Excluir cliente")
    public Response delete(@PathParam("id") Long id) {
        clientService.delete(id);
        return Response.noContent().build();
    }
}
