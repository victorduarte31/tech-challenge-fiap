package br.com.oficina.domain.model;

/**
 * Snapshot imutável do veículo, mantido pela OS por identidade + dados de
 * exibição. O {@code WorkOrder} referencia o aggregate {@code Vehicle} por id,
 * sem segurar a entidade de persistência.
 */
public record VehicleSnapshot(
    Long vehicleId,
    String licensePlate,
    String brand,
    String model,
    Integer productionYear
) {
}
