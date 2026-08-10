package br.com.oficina.application.validation;

import br.com.oficina.domain.valueobject.CpfCnpj;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Adaptador fino entre Bean Validation e a regra de domínio. */
public class CpfCnpjValidator implements ConstraintValidator<ValidCpfCnpj, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && !value.isBlank() && CpfCnpj.isValid(value);
    }
}
