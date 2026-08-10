package br.com.oficina.interfaces.exception;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.InvalidApprovalTokenException;
import br.com.oficina.domain.exception.InvalidStatusTransitionException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a tradução de exceções para HTTP sem subir o Quarkus: o mapper é um POJO.
 * Os ramos de concorrência e integridade referencial são a lógica não-trivial —
 * chegam embrulhados em várias camadas de causa e precisam ser reconhecidos pelo
 * SQLState, não pelo tipo.
 */
class GlobalExceptionMapperTest {

    private GlobalExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GlobalExceptionMapper();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(Response response) {
        return (Map<String, Object>) response.getEntity();
    }

    @Test
    void resourceNotFound_shouldMapTo404() {
        Response response = mapper.toResponse(new ResourceNotFoundException("Cliente", 7L));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(bodyOf(response)).containsEntry("status", 404);
        assertThat(bodyOf(response).get("message").toString()).contains("Cliente");
    }

    @Test
    void businessException_shouldMapTo422() {
        Response response = mapper.toResponse(new BusinessException("Estoque insuficiente"));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(bodyOf(response))
            .containsEntry("error", "Unprocessable Entity")
            .containsEntry("message", "Estoque insuficiente");
    }

    @Test
    void invalidStatusTransition_inheritsTheBusinessMapping() {
        try (Response response = mapper.toResponse(new InvalidStatusTransitionException("Status inválido"))) {

            assertThat(response.getStatus()).isEqualTo(422);
        }
    }

    /**
     * Apesar de herdar de BusinessException, o token inválido responde 404 com a
     * mesma mensagem de OS inexistente — do contrário o endpoint público viraria
     * um oráculo para descobrir quais números de OS existem.
     */
    @Test
    void invalidApprovalToken_shouldMapTo404NotTo422() {
        try (Response response = mapper.toResponse(
                new InvalidApprovalTokenException("Ordem de Serviço não encontrada ou link inválido."))) {

            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Test
    void illegalArgument_shouldMapTo400() {
        try (Response response = mapper.toResponse(new IllegalArgumentException("Parâmetro inválido"))) {

            assertThat(response.getStatus()).isEqualTo(400);
        }
    }

    @Test
    void webApplicationException_shouldKeepItsNativeStatus() {
        try (Response response = mapper.toResponse(new NotAuthorizedException("Bearer"))) {

            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void optimisticLock_shouldMapTo409EvenWhenWrapped() {
        Throwable wrapped = new RuntimeException("falha ao gravar",
            new IllegalStateException(new OptimisticLockException("versão divergente")));

        Response response = mapper.toResponse(wrapped);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).get("message").toString()).contains("alterado por outra operação");
    }

    @Test
    void uniqueViolation_shouldMapTo409WithUniquenessMessage() {
        Throwable wrapped = new RuntimeException("insert falhou",
            new SQLException("duplicate key", "23505"));

        Response response = mapper.toResponse(wrapped);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).get("message").toString()).contains("unicidade");
    }

    @Test
    void foreignKeyViolation_shouldMapTo409WithLinkedRecordMessage() {
        Throwable wrapped = new RuntimeException("delete falhou",
            new SQLException("FK violation", "23503"));

        Response response = mapper.toResponse(wrapped);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).get("message").toString()).contains("vinculado");
    }

    @Test
    void unexpectedError_shouldReturn500WithCorrelationIdAndNoStackTrace() {
        Response response = mapper.toResponse(new NullPointerException("segredo interno"));

        assertThat(response.getStatus()).isEqualTo(500);
        Map<String, Object> body = bodyOf(response);
        assertThat(body).containsEntry("message", "Erro interno do servidor");
        assertThat(body.get("correlationId").toString()).isNotBlank();
        assertThat(body.toString()).doesNotContain("segredo interno");
    }

    @Test
    void nonIntegritySqlState_shouldFallBackTo500() {
        try (Response response = mapper.toResponse(
                new RuntimeException("timeout", new SQLException("connection timeout", "08006")))) {

            assertThat(response.getStatus()).isEqualTo(500);
        }
    }
}
