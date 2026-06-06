package br.com.oficina.application.service;

import br.com.oficina.application.Pagination;
import br.com.oficina.application.dto.*;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.*;
import br.com.oficina.infrastructure.repository.*;
import br.com.oficina.infrastructure.validation.CpfCnpjUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
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

    @Transactional(TxType.SUPPORTS)
    public List<WorkOrderResponseDto> listAll(int page, int size) {
        return workOrderRepository.listAll(Pagination.page(page), Pagination.cap(size)).stream()
            .map(WorkOrderResponseDto::from)
            .toList();
    }

    @Transactional(TxType.SUPPORTS)
    public List<WorkOrderResponseDto> listByStatus(WorkOrderStatus status, int page, int size) {
        return workOrderRepository.findByStatus(status, Pagination.page(page), Pagination.cap(size)).stream()
            .map(WorkOrderResponseDto::from)
            .toList();
    }

    @Transactional(TxType.SUPPORTS)
    public WorkOrderResponseDto findById(Long id) {
        WorkOrder wo = workOrderRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ordem de Serviço", id));
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional(TxType.SUPPORTS)
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

        if (!vehicle.getClient().getId().equals(client.getId())) {
            throw new BusinessException("O veículo não pertence ao cliente informado");
        }

        WorkOrder wo = new WorkOrder(client, vehicle, dto.notes());
        workOrderRepository.persist(wo);
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
        // Força o flush para que as linhas (peças/serviços) persistidas por cascata
        // recebam seus ids antes da montagem do DTO. Sem isto, a resposta traria linhas
        // com id nulo e o cliente não conseguiria removê-las (DELETE .../parts/null -> 404).
        workOrderRepository.flush();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto addService(Long id, WorkOrderServiceDto dto) {
        WorkOrder wo = findWorkOrder(id);
        addServiceToOrder(wo, dto);
        workOrderRepository.flush();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto addPart(Long id, WorkOrderPartDto dto) {
        WorkOrder wo = findWorkOrder(id);
        addPartToOrder(wo, dto);
        workOrderRepository.flush();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto removeService(Long id, Long serviceLineId) {
        WorkOrder wo = findWorkOrder(id);
        wo.removeService(serviceLineId);
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto removePart(Long id, Long partLineId) {
        WorkOrder wo = findWorkOrder(id);
        wo.removePart(partLineId);
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

    @Transactional
    public WorkOrderResponseDto approveByOrderNumber(String orderNumber, String clientCpfCnpj) {
        WorkOrder wo = findAndAuthorize(orderNumber, clientCpfCnpj);
        wo.approve();
        return WorkOrderResponseDto.from(wo);
    }

    @Transactional
    public WorkOrderResponseDto rejectByOrderNumber(String orderNumber, String clientCpfCnpj) {
        WorkOrder wo = findAndAuthorize(orderNumber, clientCpfCnpj);
        wo.reject();
        return WorkOrderResponseDto.from(wo);
    }

    private WorkOrder findAndAuthorize(String orderNumber, String providedCpfCnpj) {
        WorkOrder wo = workOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Ordem de Serviço não encontrada: " + orderNumber));

        String normalized = CpfCnpjUtils.normalize(providedCpfCnpj);
        if (!wo.getClient().getCpfCnpj().equals(normalized)) {
            // Mensagem genérica para não distinguir "OS inexistente" de "CPF/CNPJ não confere"
            throw new ResourceNotFoundException("Ordem de Serviço não encontrada: " + orderNumber);
        }
        return wo;
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ordem de Serviço", id));
    }

    private void addServiceToOrder(WorkOrder wo, WorkOrderServiceDto dto) {
        ServiceItem serviceItem = serviceItemRepository.findByIdOptional(dto.serviceItemId())
            .orElseThrow(() -> new ResourceNotFoundException("Serviço", dto.serviceItemId()));
        wo.addService(serviceItem, dto.notes());
    }

    private void addPartToOrder(WorkOrder wo, WorkOrderPartDto dto) {
        Part part = partRepository.findByIdOptional(dto.partId())
            .orElseThrow(() -> new ResourceNotFoundException("Peça/Insumo", dto.partId()));
        wo.addPart(part, dto.quantity());
    }
}
