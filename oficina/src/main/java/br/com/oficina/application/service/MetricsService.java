package br.com.oficina.application.service;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.infrastructure.repository.PartRepository;
import br.com.oficina.infrastructure.repository.WorkOrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

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

    @Transactional(TxType.SUPPORTS)
    public MetricsResponseDto getMetrics() {
        long total = workOrderRepository.count();
        long open = workOrderRepository.countOpen();
        long finished = workOrderRepository.countFinished();
        long cancelled = workOrderRepository.countCancelled();

        BigDecimal totalRevenue = workOrderRepository.sumRevenueDelivered();

        List<WorkOrder> withExecTime = workOrderRepository.findWithExecutionTime();
        double avgExecTime = withExecTime.stream()
            .mapToLong(WorkOrder::getExecutionDurationMinutes)
            .average()
            .orElse(0.0);

        long lowStock = partRepository.findLowStock(LOW_STOCK_THRESHOLD).size();

        return new MetricsResponseDto(total, open, finished, cancelled, avgExecTime, totalRevenue, lowStock);
    }
}
