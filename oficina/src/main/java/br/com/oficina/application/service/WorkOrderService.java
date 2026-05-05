package br.com.oficina.application.service;

import br.com.oficina.application.dto.*;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.*;
import br.com.oficina.infrastructure.repository.*;
import br.com.oficina.infrastructure.validation.CpfCnpjUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class WorkOrderService {

    WorkOrderRepository workOrderRepository;
    ClientRepository clientRepository;
    VehicleRepository vehicleRepository;
    PartRepository partRepository;
    ServiceItemRepository serviceItemRepository;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ClientRepository clientRepository,
                            VehicleRepository vehicleRepository,
                            PartRepository partRepository,
                            ServiceItemRepository serviceItemRepository) {
        this.workOrderRepository = workOrderRepository;
        this.clientRepository = clientRepository;
        this.vehicleRepository = vehicleRepository;
        this.partRepository = partRepository;
        this.serviceItemRepository = serviceItemRepository;
    }

    public List<WorkOrderResponseDto> listAll() {
        return workOrderRepository.listAll().stream()
            .map(WorkOrderResponseDto::from)
            .toList();
    }

    public List<WorkOrderResponseDto> listByStatus(WorkOrderStatus status) {
        return workOrderRepository.findByStatus(status).stream()
            .map(WorkOrderResponseDto::from)
            .toList();
    }

    public WorkOrderResponseDto findById(Long id) {
        WorkOrder wo = workOrderRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ordem de Serviço", id));
        return WorkOrderResponseDto.from(wo);
    }

    public WorkOrderResponseDto findByOrderNumber(String orderNumber) {
        WorkOrder wo = workOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Ordem de Serviço não encontrada: " + orderNumber
            ));
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto create(WorkOrderCreateDto dto) {
        String normalizedCpfCnpj = CpfCnpjUtils.normalize(dto.clientCpfCnpj());
        Client client = clientRepository.findByCpfCnpj(normalizedCpfCnpj)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Cliente não encontrado com CPF/CNPJ: " + dto.clientCpfCnpj()
            ));

        Vehicle vehicle = vehicleRepository.findByIdOptional(dto.vehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Veículo", dto.vehicleId()));

        if (!vehicle.client.id.equals(client.id)) {
            throw new BusinessException("O veículo não pertence ao cliente informado");
        }

        WorkOrder wo = new WorkOrder();
        wo.client = client;
        wo.vehicle = vehicle;
        wo.notes = dto.notes();
        workOrderRepository.persist(wo);

        wo.orderNumber = "OS-" + String.format("%06d", wo.id);

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
        wo.recalculateTotalCost();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto addService(Long id, WorkOrderServiceDto dto) {
        WorkOrder wo = getEditableWorkOrder(id);
        addServiceToOrder(wo, dto);
        wo.recalculateTotalCost();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto addPart(Long id, WorkOrderPartDto dto) {
        WorkOrder wo = getEditableWorkOrder(id);
        addPartToOrder(wo, dto);
        wo.recalculateTotalCost();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto removeService(Long id, Long serviceLineId) {
        WorkOrder wo = getEditableWorkOrder(id);
        wo.services.removeIf(s -> s.id.equals(serviceLineId));
        wo.recalculateTotalCost();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto removePart(Long id, Long partLineId) {
        WorkOrder wo = getEditableWorkOrder(id);
        WorkOrderPart toRemove = wo.parts.stream()
            .filter(p -> p.id.equals(partLineId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Linha de peça", partLineId));
        toRemove.part.increaseStock(toRemove.quantity);
        wo.parts.remove(toRemove);
        wo.recalculateTotalCost();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto startDiagnosis(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.startDiagnosis();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto sendForApproval(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.sendForApproval();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto approve(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.approve();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto reject(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.reject();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto complete(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.complete();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto deliver(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.deliver();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto cancel(Long id) {
        WorkOrder wo = findWorkOrder(id);
        wo.cancel();
        return WorkOrderResponseDto.from(wo);
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ordem de Serviço", id));
    }

    private WorkOrder getEditableWorkOrder(Long id) {
        WorkOrder wo = findWorkOrder(id);
        if (wo.status != WorkOrderStatus.RECEIVED && wo.status != WorkOrderStatus.IN_DIAGNOSIS) {
            throw new BusinessException(
                "Não é possível editar uma OS com status: " + wo.status
            );
        }
        return wo;
    }

    private void addServiceToOrder(WorkOrder wo, WorkOrderServiceDto dto) {
        ServiceItem serviceItem = serviceItemRepository.findByIdOptional(dto.serviceItemId())
            .orElseThrow(() -> new ResourceNotFoundException("Serviço", dto.serviceItemId()));
        if (Boolean.FALSE.equals(serviceItem.active)) {
            throw new BusinessException("Serviço inativo: " + serviceItem.name);
        }
        WorkOrderServiceItem line = new WorkOrderServiceItem();
        line.workOrder = wo;
        line.serviceItem = serviceItem;
        line.price = serviceItem.basePrice;
        line.notes = dto.notes();
        wo.services.add(line);
    }

    private void addPartToOrder(WorkOrder wo, WorkOrderPartDto dto) {
        Part part = partRepository.findByIdOptional(dto.partId())
            .orElseThrow(() -> new ResourceNotFoundException("Peça/Insumo", dto.partId()));
        part.decreaseStock(dto.quantity());

        WorkOrderPart line = new WorkOrderPart();
        line.workOrder = wo;
        line.part = part;
        line.quantity = dto.quantity();
        line.unitPrice = part.unitPrice;
        wo.parts.add(line);
    }
}
