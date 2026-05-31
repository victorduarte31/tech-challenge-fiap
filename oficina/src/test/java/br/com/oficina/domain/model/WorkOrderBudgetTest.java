package br.com.oficina.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class WorkOrderBudgetTest {

    private WorkOrder newWorkOrder() {
        Client client = new Client("Maria", "11144477735", ClientType.PF, null, null);
        Vehicle vehicle = new Vehicle("ABC1234", "Toyota", "Corolla", 2020, client);
        return new WorkOrder(client, vehicle, null);
    }

    @Test
    void budget_shouldStartAtZero() {
        assertThat(newWorkOrder().getBudget()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void budget_shouldSumPartsAndServicesAutomatically() {
        WorkOrder workOrder = newWorkOrder();
        Part part = new Part("Filtro", "Filtro de óleo", new BigDecimal("10.00"), 10, "UN");
        ServiceItem service = new ServiceItem("Troca de óleo", "Troca completa", new BigDecimal("100.00"), 30);

        workOrder.addPart(part, 2);            // 2 x 10.00 = 20.00
        workOrder.addService(service, null);   // 100.00

        assertThat(workOrder.getBudget()).isEqualByComparingTo("120.00");
        assertThat(workOrder.getBudget()).isEqualByComparingTo(workOrder.getTotalCost());
    }
}
