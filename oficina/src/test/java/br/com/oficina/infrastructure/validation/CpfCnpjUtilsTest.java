package br.com.oficina.infrastructure.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class CpfCnpjUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "111.444.777-35",
        "11144477735",
        "529.982.247-25",
        "987.654.321-00",
        "123.456.789-09"
    })
    void isValidCpf_withValidCpfs_shouldReturnTrue(String cpf) {
        assertThat(CpfCnpjUtils.isValid(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "000.000.000-00",
        "111.111.111-11",
        "123.456.789-00",
        "abc",
        ""
    })
    void isValidCpf_withInvalidCpfs_shouldReturnFalse(String cpf) {
        assertThat(CpfCnpjUtils.isValid(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "11.222.333/0001-81",
        "11222333000181"
    })
    void isValidCnpj_withValidCnpj_shouldReturnTrue(String cnpj) {
        assertThat(CpfCnpjUtils.isValid(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "00.000.000/0000-00",
        "11.111.111/1111-11",
        "12345678000199"
    })
    void isValidCnpj_withInvalidCnpj_shouldReturnFalse(String cnpj) {
        assertThat(CpfCnpjUtils.isValid(cnpj)).isFalse();
    }

    @Test
    void normalize_shouldRemoveNonDigits() {
        assertThat(CpfCnpjUtils.normalize("111.444.777-35")).isEqualTo("11144477735");
        assertThat(CpfCnpjUtils.normalize("11.222.333/0001-81")).isEqualTo("11222333000181");
    }

    @Test
    void normalize_withNull_shouldReturnNull() {
        assertThat(CpfCnpjUtils.normalize(null)).isNull();
    }

    @Test
    void isValid_withNull_shouldReturnFalse() {
        assertThat(CpfCnpjUtils.isValid(null)).isFalse();
    }
}
