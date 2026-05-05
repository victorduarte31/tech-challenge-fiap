package br.com.oficina.infrastructure.validation;

public final class CpfCnpjUtils {

    private CpfCnpjUtils() {}

    public static String normalize(String value) {
        if (value == null) return null;
        return value.replaceAll("[^0-9]", "");
    }

    public static boolean isValid(String value) {
        if (value == null) return false;
        String digits = normalize(value);
        if (digits.length() == 11) return isValidCpf(digits);
        if (digits.length() == 14) return isValidCnpj(digits);
        return false;
    }

    public static boolean isValidCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return false;
        if (cpf.chars().distinct().count() == 1) return false;

        int sum = 0;
        for (int i = 0; i < 9; i++) sum += Character.getNumericValue(cpf.charAt(i)) * (10 - i);
        int first = 11 - (sum % 11);
        if (first >= 10) first = 0;
        if (first != Character.getNumericValue(cpf.charAt(9))) return false;

        sum = 0;
        for (int i = 0; i < 10; i++) sum += Character.getNumericValue(cpf.charAt(i)) * (11 - i);
        int second = 11 - (sum % 11);
        if (second >= 10) second = 0;
        return second == Character.getNumericValue(cpf.charAt(10));
    }

    public static boolean isValidCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) return false;
        if (cnpj.chars().distinct().count() == 1) return false;

        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int sum = 0;
        for (int i = 0; i < 12; i++) sum += Character.getNumericValue(cnpj.charAt(i)) * weights1[i];
        int first = sum % 11 < 2 ? 0 : 11 - (sum % 11);
        if (first != Character.getNumericValue(cnpj.charAt(12))) return false;

        sum = 0;
        for (int i = 0; i < 13; i++) sum += Character.getNumericValue(cnpj.charAt(i)) * weights2[i];
        int second = sum % 11 < 2 ? 0 : 11 - (sum % 11);
        return second == Character.getNumericValue(cnpj.charAt(13));
    }
}
