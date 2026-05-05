package br.com.oficina.application.service;

import br.com.oficina.application.dto.*;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.InvalidStatusTransitionException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.*;
import br.com.oficina.infrastructure.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock ClientRepository clientRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock PartRepository partRepository;
    @Mock ServiceItemRepository serviceItemRepository;

    @InjectMocks
    WorkOrderService workOrderService;

    private Client client;
    private Vehicle vehicle;
    private WorkOrder workOrder;
    private Part part;
    private ServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        client = new Client();
        client.id = 1L;
        client.name = "Cliente Teste";
        client.cpfCnpj = "11144477735";
        client.clientType = ClientType.PF;

        vehicle = new Vehicle();
        vehicle.id = 1L;
        vehicle.licensePlate = "ABC1234";
        vehicle.brand = "Toyota";
        vehicle.model = "Corolla";
        vehicle.productionYear = 2020;
        vehicle.client = client;

        workOrder = new WorkOrder();
        workOrder.id = 1L;
        workOrder.orderNumber = "OS-000001";
        workOrder.client = client;
        workOrder.vehicle = vehicle;
        workOrder.status = WorkOrderStatus.RECEIVED;
        workOrder.totalCost = BigDecimal.ZERO;
        workOrder.parts = new ArrayList<>();
        workOrder.services = new ArrayList<>();
        workOrder.createdAt = LocalDateTime.now();
        workOrder.updatedAt = LocalDateTime.now();

        part = new Part();
        part.id = 1L;
        part.name = "Óleo Motor";
        part.unitPrice = new BigDecimal("45.90");
        part.stockQuantity = 10;
        part.unit = "L";

        serviceItem = new ServiceItem();
        serviceItem.id = 1L;
        serviceItem.name = "Troca de Óleo";
        serviceItem.basePrice = new BigDecimal("120.00");
        serviceItem.estimatedDurationMinutes = 30;
        serviceItem.active = true;
    }

    @Test
    void listAll_shouldReturnAllOrders() {
        when(workOrderRepository.listAll()).thenReturn(List.of(workOrder));

        List<WorkOrderResponseDto> result = workOrderService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().orderNumber()).isEqualTo("OS-000001");
    }

    @Test
    void listByStatus_shouldFilterByStatus() {
        when(workOrderRepository.findByStatus(WorkOrderStatus.RECEIVED)).thenReturn(List.of(workOrder));

        List<WorkOrderResponseDto> result = workOrderService.listByStatus(WorkOrderStatus.RECEIVED);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(WorkOrderStatus.RECEIVED);
    }

    @Test
    void findById_whenExists_shouldReturn() {
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void findById_whenNotFound_shouldThrow() {
        when(workOrderRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workOrderService.findById(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withValidData_shouldCreateWorkOrder() {
        when(clientRepository.findByCpfCnpj("11144477735")).thenReturn(Optional.of(client));
        when(vehicleRepository.findByIdOptional(1L)).thenReturn(Optional.of(vehicle));
        doNothing().when(workOrderRepository).persist(any(WorkOrder.class));

        WorkOrderCreateDto dto = new WorkOrderCreateDto(
            "111.444.777-35", 1L, "Revisão geral", null, null
        );

        WorkOrderResponseDto result = workOrderService.create(dto);

        assertThat(result.clientCpfCnpj()).isEqualTo("11144477735");
        verify(workOrderRepository).persist(any(WorkOrder.class));
    }

    @Test
    void create_withWrongVehicleOwner_shouldThrowBusinessException() {
        Client otherClient = new Client();
        otherClient.id = 2L;
        vehicle.client = otherClient;

        when(clientRepository.findByCpfCnpj("11144477735")).thenReturn(Optional.of(client));
        when(vehicleRepository.findByIdOptional(1L)).thenReturn(Optional.of(vehicle));

        WorkOrderCreateDto dto = new WorkOrderCreateDto(
            "111.444.777-35", 1L, null, null, null
        );

        assertThatThrownBy(() -> workOrderService.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("veículo não pertence");
    }

    @Test
    void create_withUnknownClient_shouldThrowNotFoundException() {
        when(clientRepository.findByCpfCnpj(anyString())).thenReturn(Optional.empty());

        WorkOrderCreateDto dto = new WorkOrderCreateDto(
            "11144477735", 1L, null, null, null
        );

        assertThatThrownBy(() -> workOrderService.create(dto))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startDiagnosis_fromReceived_shouldChangeStatus() {
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.startDiagnosis(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.IN_DIAGNOSIS);
        assertThat(workOrder.diagnosisStartedAt).isNotNull();
    }

    @Test
    void startDiagnosis_fromWrongStatus_shouldThrow() {
        workOrder.status = WorkOrderStatus.IN_EXECUTION;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> workOrderService.startDiagnosis(1L))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void sendForApproval_fromInDiagnosis_shouldChangeStatus() {
        workOrder.status = WorkOrderStatus.IN_DIAGNOSIS;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.sendForApproval(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
        assertThat(workOrder.sentForApprovalAt).isNotNull();
    }

    @Test
    void approve_fromAwaitingApproval_shouldChangeStatus() {
        workOrder.status = WorkOrderStatus.AWAITING_APPROVAL;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.approve(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.IN_EXECUTION);
        assertThat(workOrder.approvedAt).isNotNull();
    }

    @Test
    void reject_fromAwaitingApproval_shouldCancelOrder() {
        workOrder.status = WorkOrderStatus.AWAITING_APPROVAL;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.reject(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(workOrder.cancelledAt).isNotNull();
    }

    @Test
    void complete_fromInExecution_shouldChangeStatus() {
        workOrder.status = WorkOrderStatus.IN_EXECUTION;
        workOrder.executionStartedAt = LocalDateTime.now();
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.complete(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.FINISHED);
        assertThat(workOrder.finishedAt).isNotNull();
    }

    @Test
    void deliver_fromFinished_shouldChangeStatus() {
        workOrder.status = WorkOrderStatus.FINISHED;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.deliver(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.DELIVERED);
        assertThat(workOrder.deliveredAt).isNotNull();
    }

    @Test
    void cancel_fromAnyEditableStatus_shouldCancel() {
        workOrder.status = WorkOrderStatus.IN_DIAGNOSIS;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.cancel(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void cancel_fromDelivered_shouldThrow() {
        workOrder.status = WorkOrderStatus.DELIVERED;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> workOrderService.cancel(1L))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void addPart_toEditableOrder_shouldDecrementStock() {
        workOrder.status = WorkOrderStatus.IN_DIAGNOSIS;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.findByIdOptional(1L)).thenReturn(Optional.of(part));

        WorkOrderPartDto dto = new WorkOrderPartDto(1L, 2);
        workOrderService.addPart(1L, dto);

        assertThat(part.stockQuantity).isEqualTo(8);
        assertThat(workOrder.parts).hasSize(1);
    }

    @Test
    void addPart_withInsufficientStock_shouldThrow() {
        workOrder.status = WorkOrderStatus.IN_DIAGNOSIS;
        part.stockQuantity = 1;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.findByIdOptional(1L)).thenReturn(Optional.of(part));

        WorkOrderPartDto dto = new WorkOrderPartDto(1L, 5);

        assertThatThrownBy(() -> workOrderService.addPart(1L, dto))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addService_toEditableOrder_shouldAddWithBasePrice() {
        workOrder.status = WorkOrderStatus.RECEIVED;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));
        when(serviceItemRepository.findByIdOptional(1L)).thenReturn(Optional.of(serviceItem));

        WorkOrderServiceDto dto = new WorkOrderServiceDto(1L, null);
        workOrderService.addService(1L, dto);

        assertThat(workOrder.services).hasSize(1);
        assertThat(workOrder.services.getFirst().price).isEqualByComparingTo("120.00");
    }

    @Test
    void addService_withInactiveService_shouldThrowBusinessException() {
        serviceItem.active = false;
        workOrder.status = WorkOrderStatus.RECEIVED;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));
        when(serviceItemRepository.findByIdOptional(1L)).thenReturn(Optional.of(serviceItem));

        WorkOrderServiceDto dto = new WorkOrderServiceDto(1L, null);

        assertThatThrownBy(() -> workOrderService.addService(1L, dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("inativo");
    }

    @Test
    void addPart_toFinishedOrder_shouldThrowBusinessException() {
        workOrder.status = WorkOrderStatus.FINISHED;
        when(workOrderRepository.findByIdOptional(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderPartDto dto = new WorkOrderPartDto(1L, 1);

        assertThatThrownBy(() -> workOrderService.addPart(1L, dto))
            .isInstanceOf(BusinessException.class);
    }
}
