package br.com.oficina.domain.valueobject;

import br.com.oficina.domain.exception.BusinessException;
import java.util.regex.Pattern;

/**
 * Value Object da placa do veículo, aceitando o formato antigo ({@code AAA9999})
 * e o Mercosul ({@code AAA9A99}). Normalizada em maiúsculas e sem separador. A
 * regra de formato é de negócio — vive no domínio; a anotação Bean Validation
 * correspondente fica em {@code application.validation} e delega para cá.
 */
public record LicensePlate(String value) {

    private static final Pattern OLD_FORMAT = Pattern.compile("^[A-Z]{3}[0-9]{4}$");
    private static final Pattern MERCOSUL_FORMAT = Pattern.compile("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");

    public LicensePlate {
        if (!isValid(value)) {
            throw new BusinessException("Placa inválida: " + value);
        }
        value = normalize(value);
    }

    public static LicensePlate of(String raw) {
        return new LicensePlate(raw);
    }

    /** Remove hífen/espaços e converte para maiúsculas. {@code null} preservado. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("[\\s-]", "").toUpperCase();
    }

    public static boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = normalize(raw);
        return OLD_FORMAT.matcher(normalized).matches() || MERCOSUL_FORMAT.matcher(normalized).matches();
    }

    public boolean isMercosul() {
        return MERCOSUL_FORMAT.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
