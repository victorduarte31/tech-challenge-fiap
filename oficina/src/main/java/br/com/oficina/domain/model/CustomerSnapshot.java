package br.com.oficina.domain.model;

/**
 * Snapshot imutável do cliente, mantido pela OS por identidade + dados de
 * exibição. O {@code WorkOrder} referencia o aggregate {@code Client} por id,
 * sem segurar a entidade de persistência.
 */
public record CustomerSnapshot(Long clientId, String name, String cpfCnpj) {
}
