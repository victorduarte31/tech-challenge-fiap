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
