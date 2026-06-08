package br.com.oficina.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class WorkOrderBudgetTest {

    private WorkOrder newWorkOrder() {
        CustomerSnapshot customer = new CustomerSnapshot(1L, "Maria", "11144477735");
        VehicleSnapshot vehicle = new VehicleSnapshot(1L, "ABC1234", "Toyota", "Corolla", 2020);
        return new WorkOrder(customer, vehicle, null);
    }

    @Test
    void budget_shouldStartAtZero() {
        assertThat(newWorkOrder().getBudget()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void budget_shouldSumPartsAndServicesAutomatically() {
        WorkOrder workOrder = newWorkOrder();

        workOrder.addPart(1L, "Filtro", 2, new BigDecimal("10.00"));   // 2 x 10.00 = 20.00
        workOrder.addService(1L, "Troca de óleo", new BigDecimal("100.00"), null); // 100.00

        assertThat(workOrder.getBudget()).isEqualByComparingTo("120.00");
        assertThat(workOrder.getBudget()).isEqualByComparingTo(workOrder.getTotalCost());
    }
}
