package br.com.oficina.application.service;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.model.ClientType;
import br.com.oficina.domain.model.Part;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import br.com.oficina.infrastructure.repository.PartRepository;
import br.com.oficina.infrastructure.repository.WorkOrderRepository;
import br.com.oficina.testsupport.DomainTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock PartRepository partRepository;

    @InjectMocks
    MetricsService metricsService;

    private WorkOrder buildWorkOrderWithExecTime(int execMinutes) {
        Client client = new Client("Test", "11144477735", ClientType.PF, null, null);
        Vehicle vehicle = new Vehicle("ABC1234", "Toyota", "Corolla", 2020, client);
        WorkOrder wo = new WorkOrder(client, vehicle, null);
        DomainTestFixtures.setField(wo, "status", WorkOrderStatus.FINISHED);
        DomainTestFixtures.setField(wo, "executionStartedAt", LocalDateTime.now().minusMinutes(execMinutes));
        DomainTestFixtures.setField(wo, "finishedAt", LocalDateTime.now());
        return wo;
    }

    @Test
    void getMetrics_shouldCalculateCorrectly() {
        when(workOrderRepository.count()).thenReturn(5L);
        when(workOrderRepository.countOpen()).thenReturn(2L);
        when(workOrderRepository.countFinished()).thenReturn(2L);
        when(workOrderRepository.countCancelled()).thenReturn(1L);
        when(workOrderRepository.sumRevenueDelivered()).thenReturn(new BigDecimal("500.00"));
        when(workOrderRepository.findWithExecutionTime()).thenReturn(
            List.of(buildWorkOrderWithExecTime(60), buildWorkOrderWithExecTime(120))
        );
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
        when(workOrderRepository.count()).thenReturn(0L);
        when(workOrderRepository.countOpen()).thenReturn(0L);
        when(workOrderRepository.countFinished()).thenReturn(0L);
        when(workOrderRepository.countCancelled()).thenReturn(0L);
        when(workOrderRepository.sumRevenueDelivered()).thenReturn(BigDecimal.ZERO);
        when(workOrderRepository.findWithExecutionTime()).thenReturn(List.of());
        when(partRepository.findLowStock(5)).thenReturn(List.of());

        MetricsResponseDto result = metricsService.getMetrics();

        assertThat(result.totalWorkOrders()).isZero();
        assertThat(result.averageExecutionTimeMinutes()).isZero();
        assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMetrics_withLowStockParts_shouldCountCorrectly() {
        when(workOrderRepository.count()).thenReturn(0L);
        when(workOrderRepository.countOpen()).thenReturn(0L);
        when(workOrderRepository.countFinished()).thenReturn(0L);
        when(workOrderRepository.countCancelled()).thenReturn(0L);
        when(workOrderRepository.sumRevenueDelivered()).thenReturn(BigDecimal.ZERO);
        when(workOrderRepository.findWithExecutionTime()).thenReturn(List.of());
        Part p1 = new Part("p1", null, BigDecimal.ONE, 1, "UN");
        Part p2 = new Part("p2", null, BigDecimal.ONE, 1, "UN");
        when(partRepository.findLowStock(5)).thenReturn(List.of(p1, p2));

        MetricsResponseDto result = metricsService.getMetrics();

        assertThat(result.lowStockParts()).isEqualTo(2);
    }
}
