package br.com.oficina.infrastructure.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class LicensePlateValidator implements ConstraintValidator<ValidLicensePlate, String> {

    // Formato antigo: ABC-1234 ou ABC1234
    private static final Pattern OLD_FORMAT = Pattern.compile("^[A-Za-z]{3}-?[0-9]{4}$");
    // Formato Mercosul: ABC1D23
    private static final Pattern MERCOSUL_FORMAT = Pattern.compile("^[A-Za-z]{3}[0-9][A-Za-z][0-9]{2}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.replace("-", "").toUpperCase();
        return OLD_FORMAT.matcher(normalized).matches() || MERCOSUL_FORMAT.matcher(normalized).matches();
    }
}
