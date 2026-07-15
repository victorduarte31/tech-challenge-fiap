package br.com.oficina.application.service;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.domain.ports.out.PartRepositoryPort;
import br.com.oficina.domain.ports.out.WorkOrderRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock WorkOrderRepositoryPort workOrderRepository;
    @Mock PartRepositoryPort partRepository;

    @InjectMocks
    MetricsService metricsService;

    @Test
    void getMetrics_shouldCalculateCorrectly() {
        when(workOrderRepository.countAll()).thenReturn(5L);
        when(workOrderRepository.countOpen()).thenReturn(2L);
        when(workOrderRepository.countFinished()).thenReturn(2L);
        when(workOrderRepository.countCancelled()).thenReturn(1L);
        when(workOrderRepository.sumRevenueDelivered()).thenReturn(new BigDecimal("500.00"));
        when(workOrderRepository.averageExecutionTimeMinutes()).thenReturn(90.0);
        when(partRepository.countLowStock()).thenReturn(0L);

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
        when(workOrderRepository.countAll()).thenReturn(0L);
        when(workOrderRepository.countOpen()).thenReturn(0L);
        when(workOrderRepository.countFinished()).thenReturn(0L);
        when(workOrderRepository.countCancelled()).thenReturn(0L);
        when(workOrderRepository.sumRevenueDelivered()).thenReturn(BigDecimal.ZERO);
        when(workOrderRepository.averageExecutionTimeMinutes()).thenReturn(0.0);
        when(partRepository.countLowStock()).thenReturn(0L);

        MetricsResponseDto result = metricsService.getMetrics();

        assertThat(result.totalWorkOrders()).isZero();
        assertThat(result.averageExecutionTimeMinutes()).isZero();
        assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMetrics_withLowStockParts_shouldCountCorrectly() {
        when(workOrderRepository.countAll()).thenReturn(0L);
        when(workOrderRepository.countOpen()).thenReturn(0L);
        when(workOrderRepository.countFinished()).thenReturn(0L);
        when(workOrderRepository.countCancelled()).thenReturn(0L);
        when(workOrderRepository.sumRevenueDelivered()).thenReturn(BigDecimal.ZERO);
        when(workOrderRepository.averageExecutionTimeMinutes()).thenReturn(0.0);
        when(partRepository.countLowStock()).thenReturn(2L);

        MetricsResponseDto result = metricsService.getMetrics();

        assertThat(result.lowStockParts()).isEqualTo(2);
    }
}
