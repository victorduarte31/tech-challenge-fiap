package br.com.oficina.application.service;

import br.com.oficina.application.dto.*;
import br.com.oficina.application.ports.in.ApproveBudgetUseCase;
import br.com.oficina.application.ports.in.ChangeWorkOrderStatusUseCase;
import br.com.oficina.application.ports.in.CreateWorkOrderUseCase;
import br.com.oficina.application.ports.in.ListWorkOrdersUseCase;
import br.com.oficina.application.ports.in.ManageWorkOrderItemsUseCase;
import br.com.oficina.application.Pagination;
import br.com.oficina.domain.event.WorkOrderStatusChangedEvent;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.*;
import br.com.oficina.domain.ports.out.ClientRepositoryPort;
import br.com.oficina.domain.ports.out.NotificationGatewayPort;
import br.com.oficina.domain.ports.out.PartRepositoryPort;
import br.com.oficina.domain.ports.out.ServiceItemRepositoryPort;
import br.com.oficina.domain.ports.out.VehicleRepositoryPort;
import br.com.oficina.domain.ports.out.WorkOrderRepositoryPort;
import br.com.oficina.infrastructure.validation.CpfCnpjUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.List;

@ApplicationScoped
public class WorkOrderService implements CreateWorkOrderUseCase, ManageWorkOrderItemsUseCase,
        ChangeWorkOrderStatusUseCase, ApproveBudgetUseCase, ListWorkOrdersUseCase {

    WorkOrderRepositoryPort workOrderRepository;
    ClientRepositoryPort clientRepository;
    VehicleRepositoryPort vehicleRepository;
    PartRepositoryPort partRepository;
    ServiceItemRepositoryPort serviceItemRepository;
    NotificationGatewayPort notificationGateway;

    public WorkOrderService(WorkOrderRepositoryPort workOrderRepository,
                            ClientRepositoryPort clientRepository,
                            VehicleRepositoryPort vehicleRepository,
                            PartRepositoryPort partRepository,
                            ServiceItemRepositoryPort serviceItemRepository,
                            NotificationGatewayPort notificationGateway) {
        this.workOrderRepository = workOrderRepository;
        this.clientRepository = clientRepository;
        this.vehicleRepository = vehicleRepository;
        this.partRepository = partRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.notificationGateway = notificationGateway;
    }

    @Override
    @Transactional(TxType.SUPPORTS)
    public List<WorkOrderResponseDto> listActive(int page, int size) {
        return workOrderRepository.findActive(Pagination.page(page), Pagination.cap(size)).stream()
            .map(WorkOrderResponseDto::from)
            .toList();
    }

    @Override
    @Transactional(TxType.SUPPORTS)
    public List<WorkOrderResponseDto> listByStatus(WorkOrderStatus status, int page, int size) {
        return workOrderRepository.findByStatus(status, Pagination.page(page), Pagination.cap(size)).stream()
            .map(WorkOrderResponseDto::from)
            .toList();
    }

    @Override
    @Transactional(TxType.SUPPORTS)
    public WorkOrderResponseDto findById(Long id) {
        return WorkOrderResponseDto.from(findWorkOrder(id));
    }

    @Override
    @Transactional(TxType.SUPPORTS)
    public WorkOrderResponseDto findByOrderNumber(String orderNumber) {
        WorkOrder wo = workOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Ordem de Serviço não encontrada: " + orderNumber
            ));
        return WorkOrderResponseDto.from(wo);
    }

    @Override
    @Transactional
    public WorkOrderResponseDto create(WorkOrderCreateDto dto) {
        String normalizedCpfCnpj = CpfCnpjUtils.normalize(dto.clientCpfCnpj());
        Client client = clientRepository.findByCpfCnpj(normalizedCpfCnpj)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Cliente não encontrado com CPF/CNPJ: " + dto.clientCpfCnpj()
            ));

        Vehicle vehicle = vehicleRepository.fetchById(dto.vehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Veículo", dto.vehicleId()));

        if (!vehicle.getClientId().equals(client.getId())) {
            throw new BusinessException("O veículo não pertence ao cliente informado");
        }

        WorkOrder wo = new WorkOrder(snapshotOf(client), snapshotOf(vehicle), dto.notes());
        wo = workOrderRepository.save(wo);
        wo.assignOrderNumber("OS-" + String.format("%06d", wo.getId()));

        if (dto.services() != null) {
            for (WorkOrderServiceDto s : dto.services()) {
                addServiceToOrder(wo, s);
            }
        }
        if (dto.parts() != null) {
            for (WorkOrderPartDto p : dto.parts()) {
                addPartToOrder(wo, p);
            }
        }
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto addService(Long id, WorkOrderServiceDto dto) {
        WorkOrder wo = findWorkOrder(id);
        addServiceToOrder(wo, dto);
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto addPart(Long id, WorkOrderPartDto dto) {
        WorkOrder wo = findWorkOrder(id);
        addPartToOrder(wo, dto);
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto removeService(Long id, Long serviceLineId) {
        WorkOrder wo = findWorkOrder(id);
        wo.removeService(serviceLineId);
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto removePart(Long id, Long partLineId) {
        WorkOrder wo = findWorkOrder(id);
        WorkOrderPart removed = wo.removePart(partLineId);
        restoreStock(removed);
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto startDiagnosis(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.startDiagnosis();
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto sendForApproval(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.sendForApproval();
        WorkOrder saved = workOrderRepository.save(wo);
        notificationGateway.notifyStatusChange(WorkOrderStatusChangedEvent.of(saved));
        return WorkOrderResponseDto.from(saved);
    }

    @Override
    @Transactional
    public WorkOrderResponseDto approve(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.approve();
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto reject(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.reject();
        restoreStockOfAllParts(wo);
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto complete(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.complete();
        WorkOrder saved = workOrderRepository.save(wo);
        notificationGateway.notifyStatusChange(WorkOrderStatusChangedEvent.of(saved));
        return WorkOrderResponseDto.from(saved);
    }

    @Override
    @Transactional
    public WorkOrderResponseDto deliver(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.deliver();
        WorkOrder saved = workOrderRepository.save(wo);
        notificationGateway.notifyStatusChange(WorkOrderStatusChangedEvent.of(saved));
        return WorkOrderResponseDto.from(saved);
    }

    @Override
    @Transactional
    public WorkOrderResponseDto cancel(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.cancel();
        restoreStockOfAllParts(wo);
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto approveByOrderNumber(String orderNumber, String clientCpfCnpj) {
        WorkOrder wo = findAndAuthorize(orderNumber, clientCpfCnpj);
        wo.approve();
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    @Override
    @Transactional
    public WorkOrderResponseDto rejectByOrderNumber(String orderNumber, String clientCpfCnpj) {
        WorkOrder wo = findAndAuthorize(orderNumber, clientCpfCnpj);
        wo.reject();
        restoreStockOfAllParts(wo);
        return WorkOrderResponseDto.from(workOrderRepository.save(wo));
    }

    private WorkOrder findAndAuthorize(String orderNumber, String providedCpfCnpj) {
        WorkOrder wo = workOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Ordem de Serviço não encontrada: " + orderNumber));

        String normalized = CpfCnpjUtils.normalize(providedCpfCnpj);
        if (!wo.getCustomer().cpfCnpj().equals(normalized)) {
            // Mensagem genérica para não distinguir "OS inexistente" de "CPF/CNPJ não confere"
            throw new ResourceNotFoundException("Ordem de Serviço não encontrada: " + orderNumber);
        }
        return wo;
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ordem de Serviço", id));
    }

    private void addServiceToOrder(WorkOrder wo, WorkOrderServiceDto dto) {
        ServiceItem serviceItem = serviceItemRepository.fetchById(dto.serviceItemId())
            .orElseThrow(() -> new ResourceNotFoundException("Serviço", dto.serviceItemId()));
        if (!serviceItem.isActive()) {
            throw new BusinessException("Serviço inativo: " + serviceItem.getName());
        }
        wo.addService(serviceItem.getId(), serviceItem.getName(), serviceItem.getBasePrice(), dto.notes());
    }

    private void addPartToOrder(WorkOrder wo, WorkOrderPartDto dto) {
        Part part = partRepository.fetchById(dto.partId())
            .orElseThrow(() -> new ResourceNotFoundException("Peça/Insumo", dto.partId()));
        if (!part.isActive()) {
            throw new BusinessException("Peça/Insumo inativo: " + part.getName());
        }
        wo.addPart(part.getId(), part.getName(), dto.quantity(), part.getUnitPrice());
        part.decreaseStock(dto.quantity());
        partRepository.save(part);
    }

    /** Devolve ao estoque a quantidade de uma linha removida (coordenação cross-aggregate). */
    private void restoreStock(WorkOrderPart line) {
        partRepository.fetchById(line.getPartId())
            .ifPresent(part -> {
                part.increaseStock(line.getQuantity());
                partRepository.save(part);
            });
    }

    private void restoreStockOfAllParts(WorkOrder wo) {
        wo.getParts().forEach(this::restoreStock);
    }

    private static CustomerSnapshot snapshotOf(Client client) {
        return new CustomerSnapshot(client.getId(), client.getName(), client.getCpfCnpj(), client.getEmail());
    }

    private static VehicleSnapshot snapshotOf(Vehicle vehicle) {
        return new VehicleSnapshot(
            vehicle.getId(), vehicle.getLicensePlate(), vehicle.getBrand(),
            vehicle.getModel(), vehicle.getProductionYear());
    }
}
