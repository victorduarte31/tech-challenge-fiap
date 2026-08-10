package br.com.oficina.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ApprovalTokenTest {

    @Test
    void generate_shouldProduceUrlSafeTokenOf256Bits() {
        String value = ApprovalToken.generate().value();

        // 32 bytes em base64url sem padding = 43 caracteres
        assertThat(value).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generate_shouldNotRepeat() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            generated.add(ApprovalToken.generate().value());
        }
        assertThat(generated).hasSize(500);
    }

    @Test
    void matches_shouldAcceptOnlyTheExactValue() {
        ApprovalToken token = ApprovalToken.generate();

        assertThat(token.matches(token.value())).isTrue();
        assertThat(token.matches(token.value() + "x")).isFalse();
        assertThat(token.matches(token.value().toUpperCase())).isFalse();
        assertThat(token.matches("")).isFalse();
        assertThat(token.matches(null)).isFalse();
    }

    @Test
    void toString_shouldExposeRawValueForEmailTemplate() {
        ApprovalToken token = ApprovalToken.generate();
        assertThat(token).hasToString(token.value());
    }
}
