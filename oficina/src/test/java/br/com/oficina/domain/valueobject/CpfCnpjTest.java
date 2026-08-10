package br.com.oficina.domain.valueobject;

import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class CpfCnpjTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "111.444.777-35",
        "11144477735",
        "529.982.247-25",
        "987.654.321-00",
        "123.456.789-09"
    })
    void isValid_withValidCpfs_shouldReturnTrue(String cpf) {
        assertThat(CpfCnpj.isValid(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "000.000.000-00",
        "111.111.111-11",
        "123.456.789-00",
        "abc",
        ""
    })
    void isValid_withInvalidCpfs_shouldReturnFalse(String cpf) {
        assertThat(CpfCnpj.isValid(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "11.222.333/0001-81",
        "11222333000181"
    })
    void isValid_withValidCnpj_shouldReturnTrue(String cnpj) {
        assertThat(CpfCnpj.isValid(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "00.000.000/0000-00",
        "11.111.111/1111-11",
        "12345678000199"
    })
    void isValid_withInvalidCnpj_shouldReturnFalse(String cnpj) {
        assertThat(CpfCnpj.isValid(cnpj)).isFalse();
    }

    @Test
    void normalize_shouldRemoveNonDigits() {
        assertThat(CpfCnpj.normalize("111.444.777-35")).isEqualTo("11144477735");
        assertThat(CpfCnpj.normalize("11.222.333/0001-81")).isEqualTo("11222333000181");
    }

    @Test
    void normalize_withNull_shouldReturnNull() {
        assertThat(CpfCnpj.normalize(null)).isNull();
    }

    @Test
    void isValid_withNull_shouldReturnFalse() {
        assertThat(CpfCnpj.isValid(null)).isFalse();
    }

    @Test
    void of_shouldStoreNormalizedValue() {
        assertThat(CpfCnpj.of("111.444.777-35").value()).isEqualTo("11144477735");
        assertThat(CpfCnpj.of("111.444.777-35")).hasToString("11144477735");
    }

    @Test
    void of_withInvalidDocument_shouldRejectAtConstruction() {
        assertThatThrownBy(() -> CpfCnpj.of("123.456.789-00"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("inválido");
    }

    @Test
    void formatted_shouldApplyMaskForCpfAndCnpj() {
        assertThat(CpfCnpj.of("11144477735").formatted()).isEqualTo("111.444.777-35");
        assertThat(CpfCnpj.of("11222333000181").formatted()).isEqualTo("11.222.333/0001-81");
    }

    @Test
    void isCpf_shouldDistinguishDocumentType() {
        assertThat(CpfCnpj.of("11144477735").isCpf()).isTrue();
        assertThat(CpfCnpj.of("11222333000181").isCpf()).isFalse();
    }

    @Test
    void equality_shouldIgnoreMaskDifferences() {
        assertThat(CpfCnpj.of("111.444.777-35")).isEqualTo(CpfCnpj.of("11144477735"));
    }
}
