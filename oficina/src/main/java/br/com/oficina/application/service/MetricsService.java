package br.com.oficina.application.service;

import br.com.oficina.application.dto.MetricsResponseDto;
import br.com.oficina.domain.ports.out.PartRepositoryPort;
import br.com.oficina.domain.ports.out.WorkOrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.math.BigDecimal;

@ApplicationScoped
public class MetricsService {

    WorkOrderRepositoryPort workOrderRepository;
    PartRepositoryPort partRepository;

    public MetricsService(WorkOrderRepositoryPort workOrderRepository, PartRepositoryPort partRepository) {
        this.workOrderRepository = workOrderRepository;
        this.partRepository = partRepository;
    }

    @Transactional(TxType.SUPPORTS)
    public MetricsResponseDto getMetrics() {
        long total = workOrderRepository.countAll();
        long open = workOrderRepository.countOpen();
        long finished = workOrderRepository.countFinished();
        long cancelled = workOrderRepository.countCancelled();

        BigDecimal totalRevenue = workOrderRepository.sumRevenueDelivered();
        double avgExecTime = workOrderRepository.averageExecutionTimeMinutes();
        long lowStock = partRepository.countLowStock();

        return new MetricsResponseDto(total, open, finished, cancelled, avgExecTime, totalRevenue, lowStock);
    }
}
