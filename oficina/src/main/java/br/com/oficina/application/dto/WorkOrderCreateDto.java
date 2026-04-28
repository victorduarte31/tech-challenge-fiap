package br.com.oficina.application.dto;

import br.com.oficina.infrastructure.validation.ValidCpfCnpj;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WorkOrderCreateDto(
    @NotBlank(message = "CPF/CNPJ do cliente é obrigatório")
    @ValidCpfCnpj String clientCpfCnpj,
    @NotNull(message = "ID do veículo é obrigatório") Long vehicleId,
    String notes,
    List<WorkOrderServiceDto> services,
    List<WorkOrderPartDto> parts
) {}
