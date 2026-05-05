package br.com.oficina.domain.exception;

public class InvalidStatusTransitionException extends BusinessException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
