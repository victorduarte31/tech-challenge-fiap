package br.com.oficina.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Expõe na borda HTTP a regra de formato de placa definida no domínio
 * ({@code domain.valueobject.LicensePlate}) — ver {@link ValidCpfCnpj} para a
 * justificativa do posicionamento na camada de aplicação.
 */
@Documented
@Constraint(validatedBy = LicensePlateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLicensePlate {
    String message() default "Placa inválida. Use o formato antigo (AAA-9999) ou Mercosul (AAA9A99)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
