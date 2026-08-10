package br.com.oficina.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Expõe na borda HTTP a regra de validade de CPF/CNPJ definida no domínio
 * ({@code domain.valueobject.CpfCnpj}). Vive na camada de aplicação, junto dos
 * DTOs que a usam: Bean Validation é a especificação de entrada da aplicação, e
 * manter a anotação na infraestrutura obrigava os DTOs a importarem uma camada
 * mais externa, invertendo a direção da dependência.
 */
@Documented
@Constraint(validatedBy = CpfCnpjValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCpfCnpj {
    String message() default "CPF ou CNPJ inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
