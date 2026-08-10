package br.com.oficina.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Segredo de uso único que autoriza o cliente a aprovar ou recusar o orçamento
 * pelo canal público, sem autenticação.
 *
 * <p>Substitui o CPF/CNPJ como prova de autorização: o número da OS é sequencial
 * (portanto enumerável) e o CPF/CNPJ é um dado amplamente conhecido — a combinação
 * dos dois permitiria a um terceiro aprovar ou cancelar orçamentos alheios por
 * força bruta. O token é gerado com {@link SecureRandom} (256 bits), entregue
 * apenas no e-mail enviado ao cliente e invalidado no primeiro uso.</p>
 */
public record ApprovalToken(String value) {

    private static final int BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static ApprovalToken generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new ApprovalToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * Comparação em tempo constante: um {@code equals} de String sai no primeiro
     * caractere divergente, o que permitiria descobrir o token byte a byte
     * medindo o tempo de resposta.
     */
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
            value.getBytes(StandardCharsets.UTF_8),
            candidate.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return value;
    }
}
