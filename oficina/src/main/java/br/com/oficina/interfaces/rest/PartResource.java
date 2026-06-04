package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.PartRequestDto;
import br.com.oficina.application.dto.PartResponseDto;
import br.com.oficina.application.service.PartService;
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

@Path("/admin/parts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "MECHANIC"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Peças e Insumos", description = "Gestão de peças e insumos com controle de estoque")
public class PartResource {

    PartService partService;

    public PartResource(PartService partService) {
        this.partService = partService;
    }

    @GET
    @Operation(summary = "Listar peças/insumos ativos (paginado)")
    public List<PartResponseDto> listAll(@QueryParam("page") @DefaultValue("0") int page,
                                         @QueryParam("size") @DefaultValue("20") int size) {
        return partService.listAll(page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar peça/insumo por ID")
    public PartResponseDto findById(@PathParam("id") Long id) {
        return partService.findById(id);
    }

    @GET
    @Path("/low-stock")
    @Operation(summary = "Listar itens com estoque igual ou abaixo do mínimo de cada peça")
    public List<PartResponseDto> findLowStock() {
        return partService.findLowStock();
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(summary = "Cadastrar nova peça/insumo")
    public Response create(@Valid PartRequestDto dto) {
        PartResponseDto created = partService.create(dto);
        return Response.created(URI.create("/admin/parts/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Atualizar peça/insumo")
    public PartResponseDto update(@PathParam("id") Long id, @Valid PartRequestDto dto) {
        return partService.update(id, dto);
    }

    @PATCH
    @Path("/{id}/stock")
    @RolesAllowed("ADMIN")
    @Operation(
        summary = "Ajustar estoque (positivo=entrada, negativo=saída) — somente ADMIN",
        description = "Operação restrita a ADMIN. Mecânicos consomem estoque indiretamente ao adicionar peças à OS; " +
                      "ajustes manuais de entrada/saída (compra, devolução, perda) são responsabilidade administrativa."
    )
    public PartResponseDto adjustStock(@PathParam("id") Long id, @QueryParam("adjustment") int adjustment) {
        return partService.adjustStock(id, adjustment);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
        summary = "Excluir peça/insumo (exclusão lógica) — somente ADMIN",
        description = "Soft-delete: a peça é desativada (não removida fisicamente), pois pode estar " +
                      "referenciada por OS históricas. Deixa de aparecer no catálogo e nos alertas de reposição."
    )
    public Response delete(@PathParam("id") Long id) {
        partService.delete(id);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/reactivate")
    @RolesAllowed("ADMIN")
    @Operation(
        summary = "Reativar peça/insumo — somente ADMIN",
        description = "Reverte o soft-delete, devolvendo a peça ao catálogo e aos alertas de reposição."
    )
    public PartResponseDto reactivate(@PathParam("id") Long id) {
        return partService.reactivate(id);
    }
}
