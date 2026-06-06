package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.OperationRequest;
import br.com.oficina.domain.model.RequestStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class OperationRequestRepository implements PanacheRepository<OperationRequest> {

    public List<OperationRequest> listByStatus(RequestStatus status, int page, int size) {
        return find("status", Sort.by("requestedAt").descending(), status)
            .page(Page.of(page, size)).list();
    }

    public List<OperationRequest> listAll(int page, int size) {
        return findAll(Sort.by("requestedAt").descending()).page(Page.of(page, size)).list();
    }

    public long countByStatus(RequestStatus status) {
        return count("status", status);
    }
}
