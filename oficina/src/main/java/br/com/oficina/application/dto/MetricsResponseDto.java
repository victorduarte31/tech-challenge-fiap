package br.com.oficina.application.dto;

import java.math.BigDecimal;

public record MetricsResponseDto(
    long totalWorkOrders,
    long openWorkOrders,
    long finishedWorkOrders,
    long cancelledWorkOrders,
    double averageExecutionTimeMinutes,
    BigDecimal totalRevenue,
    long lowStockParts
) {}
