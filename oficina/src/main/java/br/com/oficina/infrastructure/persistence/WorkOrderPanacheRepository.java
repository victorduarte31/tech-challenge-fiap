package br.com.oficina.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositório Panache de {@code WorkOrderEntity}, usado por composição pelo
 * adapter de persistência.
 *
 * <p>Herdar {@code PanacheRepository} direto no adapter fazia com que ~40 métodos
 * do Panache ({@code deleteAll}, {@code findAll}, {@code persist}...) virassem
 * API pública do adapter, vazando o modelo de persistência para qualquer
 * colaborador que o injetasse. Aqui a superfície do Panache fica contida.</p>
 */
@ApplicationScoped
public class WorkOrderPanacheRepository implements PanacheRepository<WorkOrderEntity> {
}
