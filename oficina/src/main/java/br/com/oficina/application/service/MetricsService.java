package br.com.oficina.application.service;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import br.com.oficina.infrastructure.repository.PartRepository;
import br.com.oficina.infrastructure.repository.WorkOrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class MetricsService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    WorkOrderRepository workOrderRepository;

    PartRepository partRepository;

    public MetricsService(WorkOrderRepository workOrderRepository, PartRepository partRepository) {
        this.workOrderRepository = workOrderRepository;
        this.partRepository = partRepository;
    }

    public MetricsResponseDto getMetrics() {
        List<WorkOrder> allOrders = workOrderRepository.listAll();

        long total = allOrders.size();
        long open = allOrders.stream()
            .filter(wo -> wo.status != WorkOrderStatus.FINISHED
                && wo.status != WorkOrderStatus.DELIVERED
                && wo.status != WorkOrderStatus.CANCELLED)
            .count();
        long finished = allOrders.stream()
            .filter(wo -> wo.status == WorkOrderStatus.FINISHED
                || wo.status == WorkOrderStatus.DELIVERED)
            .count();
        long cancelled = allOrders.stream()
            .filter(wo -> wo.status == WorkOrderStatus.CANCELLED)
            .count();

        double avgExecTime = allOrders.stream()
            .filter(wo -> wo.executionStartedAt != null && wo.finishedAt != null)
            .mapToLong(WorkOrder::getExecutionDurationMinutes)
            .average()
            .orElse(0.0);

        BigDecimal totalRevenue = allOrders.stream()
            .filter(wo -> wo.status == WorkOrderStatus.DELIVERED)
            .map(wo -> wo.totalCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long lowStock = partRepository.findLowStock(LOW_STOCK_THRESHOLD).size();

        return new MetricsResponseDto(total, open, finished, cancelled, avgExecTime, totalRevenue, lowStock);
    }
}
