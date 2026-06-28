package br.com.oficina.domain.ports.out;

import br.com.oficina.domain.model.Vehicle;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code Vehicle}.
 * Definida no domínio; implementada por um adapter na infraestrutura.
 */
public interface VehicleRepositoryPort {

    List<Vehicle> listAll(int page, int size);

    Optional<Vehicle> fetchById(Long id);

    List<Vehicle> findByClientId(Long clientId);

    boolean existsByLicensePlate(String licensePlate);

    /** Upsert: persiste/atualiza e devolve o domínio remapeado (id e timestamps populados). */
    Vehicle save(Vehicle vehicle);

    void removeById(Long id);
}
