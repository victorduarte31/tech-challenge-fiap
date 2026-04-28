package br.com.oficina.application.dto;

import br.com.oficina.infrastructure.validation.ValidLicensePlate;
import jakarta.validation.constraints.*;

public record VehicleRequestDto(
    @NotBlank(message = "Placa é obrigatória")
    @ValidLicensePlate String licensePlate,
    @NotBlank(message = "Marca é obrigatória") String brand,
    @NotBlank(message = "Modelo é obrigatório") String model,
    @NotNull(message = "Ano é obrigatório")
    @Min(value = 1886, message = "Ano inválido")
    @Max(value = 2100, message = "Ano inválido") Integer productionYear,
    @NotNull(message = "ID do cliente é obrigatório") Long clientId
) {}
