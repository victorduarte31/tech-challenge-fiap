package br.com.oficina.application.service;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.infrastructure.repository.PartRepository;
import br.com.oficina.infrastructure.repository.WorkOrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@ApplicationScoped
public class MetricsService {

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

        double avgExecTime = workOrderRepository.findExecutionTimestamps().stream()
            .mapToLong(row -> Duration.between((LocalDateTime) row[0], (LocalDateTime) row[1]).toMinutes())
            .average()
            .orElse(0.0);

        long lowStock = partRepository.countLowStock();

        return new MetricsResponseDto(total, open, finished, cancelled, avgExecTime, totalRevenue, lowStock);
    }
}
