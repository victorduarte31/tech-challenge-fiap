package br.com.oficina.application.ports.out;

import br.com.oficina.domain.model.Vehicle;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code Vehicle}.
 * Declarada na camada de aplicação (que a consome) e implementada por um adapter
 * na infraestrutura — a dependência aponta de fora para dentro.
 */
public interface VehicleRepositoryPort {

    List<Vehicle> listAll(int page, int size);

    Optional<Vehicle> fetchById(Long id);

    List<Vehicle> findByClientId(Long clientId);

    boolean existsByLicensePlate(String licensePlate);

    /** Upsert: persiste/atualiza e devolve o domínio remapeado (id e timestamps populados). */
    Vehicle save(Vehicle vehicle);

    /** Total de registros, para o cabeçalho X-Total-Count da listagem. */
    long countAll();

    void removeById(Long id);
}
