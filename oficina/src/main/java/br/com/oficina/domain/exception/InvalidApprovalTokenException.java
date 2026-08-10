package br.com.oficina.domain.exception;

/**
 * Token de aprovação ausente, divergente ou pertencente a outra OS.
 *
 * <p>Deliberadamente traduzida para {@code 404} com a mesma mensagem de "OS
 * inexistente" ({@code GlobalExceptionMapper}): distinguir "OS não existe" de
 * "token errado" entregaria ao atacante um oráculo para enumerar OS válidas.</p>
 */
public class InvalidApprovalTokenException extends BusinessException {

    public InvalidApprovalTokenException(String message) {
        super(message);
    }
}
