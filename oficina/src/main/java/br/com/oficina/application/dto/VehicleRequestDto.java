package br.com.oficina.application.dto;

import br.com.oficina.infrastructure.validation.ValidLicensePlate;
import jakarta.validation.constraints.*;

public record VehicleRequestDto(
    @NotBlank(message = "Placa é obrigatória")
    @Size(max = 10, message = "Placa deve ter no máximo 10 caracteres")
    @ValidLicensePlate String licensePlate,
    @NotBlank(message = "Marca é obrigatória")
    @Size(max = 50, message = "Marca deve ter no máximo 50 caracteres") String brand,
    @NotBlank(message = "Modelo é obrigatório")
    @Size(max = 80, message = "Modelo deve ter no máximo 80 caracteres") String model,
    @NotNull(message = "Ano é obrigatório")
    @Min(value = 1886, message = "Ano inválido")
    @Max(value = 2100, message = "Ano inválido") Integer productionYear,
    @NotNull(message = "ID do cliente é obrigatório") Long clientId
) {}
