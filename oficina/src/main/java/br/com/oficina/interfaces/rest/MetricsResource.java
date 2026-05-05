package br.com.oficina.interfaces.rest;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.application.service.MetricsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin/metrics")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Métricas", description = "Monitoramento e métricas operacionais da oficina")
public class MetricsResource {

    MetricsService metricsService;

    public MetricsResource(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GET
    @Operation(
        summary = "Obter métricas da oficina",
        description = "Retorna métricas como total de OS, tempo médio de execução, receita total e estoque crítico"
    )
    public MetricsResponseDto getMetrics() {
        return metricsService.getMetrics();
    }
}
