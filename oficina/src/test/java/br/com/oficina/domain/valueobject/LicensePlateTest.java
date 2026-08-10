package br.com.oficina.domain.valueobject;

import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class LicensePlateTest {

    @ParameterizedTest
    @ValueSource(strings = {"ABC-1234", "ABC1234", "abc1234", "ABC1D23", "abc1d23"})
    void isValid_withSupportedFormats_shouldReturnTrue(String plate) {
        assertThat(LicensePlate.isValid(plate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB-1234", "ABCD123", "1234ABC", "ABC12D3", "", "   "})
    void isValid_withUnsupportedFormats_shouldReturnFalse(String plate) {
        assertThat(LicensePlate.isValid(plate)).isFalse();
    }

    @Test
    void isValid_withNull_shouldReturnFalse() {
        assertThat(LicensePlate.isValid(null)).isFalse();
    }

    @Test
    void normalize_shouldStripSeparatorsAndUppercase() {
        assertThat(LicensePlate.normalize("abc-1234")).isEqualTo("ABC1234");
        assertThat(LicensePlate.normalize(null)).isNull();
    }

    @Test
    void of_shouldStoreNormalizedValue() {
        assertThat(LicensePlate.of("abc-1234").value()).isEqualTo("ABC1234");
        assertThat(LicensePlate.of("abc-1234")).hasToString("ABC1234");
    }

    @Test
    void of_withInvalidPlate_shouldRejectAtConstruction() {
        assertThatThrownBy(() -> LicensePlate.of("XX-999"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Placa inválida");
    }

    @Test
    void isMercosul_shouldDistinguishPlateGeneration() {
        assertThat(LicensePlate.of("ABC1D23").isMercosul()).isTrue();
        assertThat(LicensePlate.of("ABC-1234").isMercosul()).isFalse();
    }
}
