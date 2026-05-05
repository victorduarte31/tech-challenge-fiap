package br.com.oficina.infrastructure.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = LicensePlateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLicensePlate {
    String message() default "Placa inválida. Use o formato antigo (AAA-9999) ou Mercosul (AAA9A99)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
