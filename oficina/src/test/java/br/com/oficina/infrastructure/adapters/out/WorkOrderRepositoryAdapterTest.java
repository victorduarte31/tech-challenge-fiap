package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.model.ClientType;
import br.com.oficina.domain.model.CustomerSnapshot;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.domain.model.VehicleSnapshot;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import br.com.oficina.application.ports.out.ClientRepositoryPort;
import br.com.oficina.application.ports.out.VehicleRepositoryPort;
import br.com.oficina.testsupport.DomainTestFixtures;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração (H2 + Flyway) da ordenação customizada de {@code findActive}
 * (spec-api 2.4): prioridade por status via CASE (IN_EXECUTION → AWAITING_APPROVAL →
 * IN_DIAGNOSIS → RECEIVED) com exclusão lógica de OS encerradas (FINISHED/DELIVERED).
 *
 * <p>Roda em {@code @TestTransaction} (rollback ao fim, sem poluir o banco compartilhado)
 * e filtra pelo CPF/CNPJ do cliente semeado aqui, isolando as asserções de OS criadas
 * por outros testes no mesmo H2 em memória.</p>
 */
@QuarkusTest
class WorkOrderRepositoryAdapterTest {

    private static final String OWNER_DOC = "ORDTEST00001";

    @Inject
    WorkOrderRepositoryAdapter adapter;

    @Inject
    ClientRepositoryPort clientRepository;

    @Inject
    VehicleRepositoryPort vehicleRepository;

    @Test
    @TestTransaction
    void findActive_shouldOrderByStatusPriorityAndExcludeClosedOrders() {
        Client client = clientRepository.save(
            new Client("Cliente Ordenação", OWNER_DOC, ClientType.PF, "ord@x.com", null));
        Vehicle vehicle = vehicleRepository.save(
            new Vehicle("ORD1A23", "Fiat", "Uno", 2010, client.getId()));

        CustomerSnapshot customer = new CustomerSnapshot(
            client.getId(), client.getName(), client.getCpfCnpj(), client.getEmail());
        VehicleSnapshot vehicleSnapshot = new VehicleSnapshot(
            vehicle.getId(), vehicle.getLicensePlate(), vehicle.getBrand(),
            vehicle.getModel(), vehicle.getProductionYear());

        // Semeadas fora de ordem de propósito, para provar que a ordenação é da consulta.
        saveWithStatus(customer, vehicleSnapshot, WorkOrderStatus.RECEIVED);
        saveWithStatus(customer, vehicleSnapshot, WorkOrderStatus.FINISHED);   // deve ser excluída
        saveWithStatus(customer, vehicleSnapshot, WorkOrderStatus.IN_DIAGNOSIS);
        saveWithStatus(customer, vehicleSnapshot, WorkOrderStatus.IN_EXECUTION);
        saveWithStatus(customer, vehicleSnapshot, WorkOrderStatus.DELIVERED);  // deve ser excluída
        saveWithStatus(customer, vehicleSnapshot, WorkOrderStatus.AWAITING_APPROVAL);

        List<WorkOrderStatus> ownOrder = adapter.findActive(0, 1000).stream()
            .filter(wo -> OWNER_DOC.equals(wo.getCustomer().cpfCnpj()))
            .map(WorkOrder::getStatus)
            .toList();

        assertThat(ownOrder).containsExactly(
            WorkOrderStatus.IN_EXECUTION,
            WorkOrderStatus.AWAITING_APPROVAL,
            WorkOrderStatus.IN_DIAGNOSIS,
            WorkOrderStatus.RECEIVED);
        assertThat(ownOrder).doesNotContain(WorkOrderStatus.FINISHED, WorkOrderStatus.DELIVERED);
    }

    private void saveWithStatus(CustomerSnapshot customer, VehicleSnapshot vehicle, WorkOrderStatus status) {
        WorkOrder wo = new WorkOrder(customer, vehicle, null);
        DomainTestFixtures.setField(wo, "status", status);
        adapter.save(wo);
    }
}
