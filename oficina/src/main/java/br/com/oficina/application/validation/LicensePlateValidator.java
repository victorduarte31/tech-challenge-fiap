package br.com.oficina.application.validation;

import br.com.oficina.domain.valueobject.LicensePlate;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Adaptador fino entre Bean Validation e a regra de domínio. */
public class LicensePlateValidator implements ConstraintValidator<ValidLicensePlate, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return LicensePlate.isValid(value);
    }
}
