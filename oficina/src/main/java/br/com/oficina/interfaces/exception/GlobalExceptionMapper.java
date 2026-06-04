package br.com.oficina.interfaces.exception;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);
    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(Throwable exception) {
        return switch (exception) {
            case ResourceNotFoundException e -> errorResponse(Response.Status.NOT_FOUND, e.getMessage());
            case BusinessException e -> errorResponse(UNPROCESSABLE_ENTITY,
                "Unprocessable Entity", e.getMessage());
            case IllegalArgumentException e -> errorResponse(Response.Status.BAD_REQUEST, e.getMessage());
            case WebApplicationException e -> e.getResponse(); // mantém status nativo (401, 403, etc.)
            default -> handleUnexpected(exception);
        };
    }

    private Response handleUnexpected(Throwable exception) {
        // Conflito de concorrência (lock otimista @Version): outra transação alterou o registro.
        if (hasCause(exception, jakarta.persistence.OptimisticLockException.class)) {
            return errorResponse(Response.Status.CONFLICT,
                "O registro foi alterado por outra operação. Recarregue os dados e tente novamente.");
        }
        // Violação de integridade no banco -> 409 Conflict (não 500), com mensagem
        // específica por tipo: 23505 = unicidade; demais 23xxx = chave estrangeira.
        String sqlState = integrityViolationState(exception);
        if (sqlState != null) {
            String message = "23505".equals(sqlState)
                ? "Registro já existente: violação de unicidade."
                : "Registro vinculado a outros dados; operação não permitida.";
            return errorResponse(Response.Status.CONFLICT, message);
        }
        // Não vaza detalhes internos para o cliente; usa correlation id para o operador
        String correlationId = UUID.randomUUID().toString();
        LOG.errorf(exception, "Erro inesperado [%s]: %s", correlationId, exception.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", "Erro interno do servidor",
                "correlationId", correlationId,
                "timestamp", OffsetDateTime.now().toString()
            ))
            .build();
    }

    /**
     * Percorre a cadeia de causas em busca de uma SQLException de violação de
     * integridade. SQLState classe "23" = integrity constraint violation (padrão
     * SQL, válido para PostgreSQL e H2).
     */
    private String integrityViolationState(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql
                && sql.getSQLState() != null
                && sql.getSQLState().startsWith("23")) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private Response errorResponse(Response.Status status, String message) {
        return errorResponse(status.getStatusCode(), status.getReasonPhrase(), message);
    }

    private Response errorResponse(int statusCode, String error, String message) {
        return Response.status(statusCode)
            .entity(Map.of(
                "status", statusCode,
                "error", error,
                "message", message,
                "timestamp", OffsetDateTime.now().toString()
            ))
            .build();
    }
}
