package br.com.oficina.application.dto;

import br.com.oficina.application.validation.ValidCpfCnpj;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WorkOrderCreateDto(
    @NotBlank(message = "CPF/CNPJ do cliente é obrigatório")
    @ValidCpfCnpj String clientCpfCnpj,
    @NotNull(message = "ID do veículo é obrigatório") Long vehicleId,
    @Size(max = 500, message = "Observações devem ter no máximo 500 caracteres") String notes,
    @Valid List<WorkOrderServiceDto> services,
    @Valid List<WorkOrderPartDto> parts
) {}
