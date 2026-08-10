package br.com.oficina.domain.valueobject;

import br.com.oficina.domain.exception.BusinessException;

/**
 * Value Object do documento do cliente (CPF ou CNPJ), sempre armazenado
 * normalizado (apenas dígitos). A validação de dígitos verificadores é regra de
 * negócio — por isso vive no domínio, sem dependência de framework. A anotação
 * Bean Validation que expõe essa regra na borda HTTP fica em
 * {@code application.validation} e apenas delega para cá.
 */
public record CpfCnpj(String value) {

    private static final int CPF_LENGTH = 11;
    private static final int CNPJ_LENGTH = 14;

    public CpfCnpj {
        if (!isValid(value)) {
            throw new BusinessException("CPF ou CNPJ inválido: " + value);
        }
        value = normalize(value);
    }

    public static CpfCnpj of(String raw) {
        return new CpfCnpj(raw);
    }

    /** Remove máscara e separadores, mantendo apenas os dígitos. {@code null} preservado. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("[^0-9]", "");
    }

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String digits = normalize(raw);
        return switch (digits.length()) {
            case CPF_LENGTH -> isValidCpf(digits);
            case CNPJ_LENGTH -> isValidCnpj(digits);
            default -> false;
        };
    }

    public static boolean isValidCpf(String cpf) {
        if (cpf == null || cpf.length() != CPF_LENGTH || allDigitsEqual(cpf)) {
            return false;
        }
        int first = checkDigit(cpf, 9, 10);
        if (first != digitAt(cpf, 9)) {
            return false;
        }
        return checkDigit(cpf, 10, 11) == digitAt(cpf, 10);
    }

    public static boolean isValidCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != CNPJ_LENGTH || allDigitsEqual(cnpj)) {
            return false;
        }
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        if (weightedCheckDigit(cnpj, weights1) != digitAt(cnpj, 12)) {
            return false;
        }
        return weightedCheckDigit(cnpj, weights2) == digitAt(cnpj, 13);
    }

    public boolean isCpf() {
        return value.length() == CPF_LENGTH;
    }

    /** Representação com máscara, para exibição (e-mail, relatórios). */
    public String formatted() {
        if (isCpf()) {
            return "%s.%s.%s-%s".formatted(
                value.substring(0, 3), value.substring(3, 6), value.substring(6, 9), value.substring(9));
        }
        return "%s.%s.%s/%s-%s".formatted(
            value.substring(0, 2), value.substring(2, 5), value.substring(5, 8),
            value.substring(8, 12), value.substring(12));
    }

    @Override
    public String toString() {
        return value;
    }

    private static boolean allDigitsEqual(String digits) {
        return digits.chars().distinct().count() == 1;
    }

    private static int digitAt(String digits, int index) {
        return Character.getNumericValue(digits.charAt(index));
    }

    /** Dígito verificador do CPF: pesos decrescentes a partir de {@code startWeight}. */
    private static int checkDigit(String cpf, int length, int startWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digitAt(cpf, i) * (startWeight - i);
        }
        int digit = 11 - (sum % 11);
        return digit >= 10 ? 0 : digit;
    }

    /** Dígito verificador do CNPJ: pesos fixos por posição. */
    private static int weightedCheckDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digitAt(cnpj, i) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
