package br.com.oficina.application.dto;

import br.com.oficina.domain.model.ClientType;
import br.com.oficina.infrastructure.validation.ValidCpfCnpj;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClientRequestDto(
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres") String name,
    @NotBlank(message = "CPF/CNPJ é obrigatório")
    @ValidCpfCnpj String cpfCnpj,
    @NotNull(message = "Tipo de cliente é obrigatório") ClientType clientType,
    @Email(message = "E-mail inválido")
    @Size(max = 100, message = "E-mail deve ter no máximo 100 caracteres") String email,
    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres") String phone
) {}
