package br.com.oficina.application.dto;

import br.com.oficina.domain.model.OperationRequest;
import br.com.oficina.domain.model.RequestStatus;
import br.com.oficina.domain.model.RequestType;
import java.time.LocalDateTime;

public record OperationRequestResponseDto(
    Long id,
    RequestType type,
    RequestStatus status,
    Long targetId,
    String targetDescription,
    Integer adjustment,
    String reason,
    String requestedBy,
    LocalDateTime requestedAt,
    String decidedBy,
    LocalDateTime decidedAt,
    String decisionNote
) {
    public static OperationRequestResponseDto from(OperationRequest r) {
        return new OperationRequestResponseDto(
            r.getId(), r.getType(), r.getStatus(), r.getTargetId(), r.getTargetDescription(),
            r.getAdjustment(), r.getReason(), r.getRequestedBy(), r.getRequestedAt(),
            r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote()
        );
    }
}
