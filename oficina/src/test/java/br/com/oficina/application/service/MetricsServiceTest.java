package br.com.oficina.application.service;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import br.com.oficina.infrastructure.repository.PartRepository;
import br.com.oficina.infrastructure.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock PartRepository partRepository;

    @InjectMocks
    MetricsService metricsService;

    private WorkOrder buildWorkOrder(WorkOrderStatus status, int execMinutes) {
        WorkOrder wo = new WorkOrder();
        wo.status = status;
        wo.totalCost = status == WorkOrderStatus.DELIVERED ? new BigDecimal("500.00") : BigDecimal.ZERO;
        wo.parts = new ArrayList<>();
        wo.services = new ArrayList<>();
        if (execMinutes > 0) {
            wo.executionStartedAt = LocalDateTime.now().minusMinutes(execMinutes);
            wo.finishedAt = LocalDateTime.now();
        }
        return wo;
    }

    @Test
    void getMetrics_shouldCalculateCorrectly() {
        List<WorkOrder> orders = List.of(
            buildWorkOrder(WorkOrderStatus.RECEIVED, 0),
            buildWorkOrder(WorkOrderStatus.IN_DIAGNOSIS, 0),
            buildWorkOrder(WorkOrderStatus.FINISHED, 60),
            buildWorkOrder(WorkOrderStatus.DELIVERED, 120),
            buildWorkOrder(WorkOrderStatus.CANCELLED, 0)
        );
        when(workOrderRepository.listAll()).thenReturn(orders);
        when(partRepository.findLowStock(5)).thenReturn(List.of());

        MetricsResponseDto result = metricsService.getMetrics();

        assertThat(result.totalWorkOrders()).isEqualTo(5);
        assertThat(result.openWorkOrders()).isEqualTo(2);
        assertThat(result.finishedWorkOrders()).isEqualTo(2);
        assertThat(result.cancelledWorkOrders()).isEqualTo(1);
        assertThat(result.totalRevenue()).isEqualByComparingTo("500.00");
        assertThat(result.averageExecutionTimeMinutes()).isGreaterThan(0);
    }

    @Test
    void getMetrics_withNoOrders_shouldReturnZeros() {
        when(workOrderRepository.listAll()).thenReturn(List.of());
        when(partRepository.findLowStock(5)).thenReturn(List.of());

        MetricsResponseDto result = metricsService.getMetrics();

        assertThat(result.totalWorkOrders()).isZero();
        assertThat(result.averageExecutionTimeMinutes()).isZero();
        assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMetrics_withLowStockParts_shouldCountCorrectly() {
        when(workOrderRepository.listAll()).thenReturn(List.of());
        when(partRepository.findLowStock(5)).thenReturn(List.of(
            new br.com.oficina.domain.model.Part(),
            new br.com.oficina.domain.model.Part()
        ));

        MetricsResponseDto result = metricsService.getMetrics();

        assertThat(result.lowStockParts()).isEqualTo(2);
    }
}
