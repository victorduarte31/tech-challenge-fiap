package br.com.oficina.application.service;

import br.com.oficina.application.dto.*;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.InvalidApprovalTokenException;
import br.com.oficina.domain.exception.InvalidStatusTransitionException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.event.WorkOrderStatusChangedEvent;
import br.com.oficina.domain.model.*;
import br.com.oficina.application.ports.out.ClientRepositoryPort;
import br.com.oficina.application.ports.out.NotificationGatewayPort;
import br.com.oficina.application.ports.out.PartRepositoryPort;
import br.com.oficina.application.ports.out.ServiceItemRepositoryPort;
import br.com.oficina.application.ports.out.VehicleRepositoryPort;
import br.com.oficina.application.ports.out.WorkOrderRepositoryPort;
import br.com.oficina.testsupport.DomainTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock WorkOrderRepositoryPort workOrderRepository;
    @Mock ClientRepositoryPort clientRepository;
    @Mock VehicleRepositoryPort vehicleRepository;
    @Mock PartRepositoryPort partRepository;
    @Mock ServiceItemRepositoryPort serviceItemRepository;
    @Mock NotificationGatewayPort notificationGateway;

    @InjectMocks
    WorkOrderService workOrderService;

    private Client client;
    private Vehicle vehicle;
    private WorkOrder workOrder;
    private Part part;
    private ServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        client = new Client("Cliente Teste", "11144477735", ClientType.PF, null, null);
        DomainTestFixtures.setId(client, 1L);

        vehicle = new Vehicle("ABC1234", "Toyota", "Corolla", 2020, 1L);
        DomainTestFixtures.setId(vehicle, 1L);

        workOrder = new WorkOrder(
            new CustomerSnapshot(1L, "Cliente Teste", "11144477735", "cliente@teste.com"),
            new VehicleSnapshot(1L, "ABC1234", "Toyota", "Corolla", 2020),
            null);
        DomainTestFixtures.setId(workOrder, 1L);
        DomainTestFixtures.setField(workOrder, "orderNumber", "OS-000001");
        DomainTestFixtures.setField(workOrder, "createdAt", LocalDateTime.now());

        part = new Part("Óleo Motor", null, new BigDecimal("45.90"), 10, "L");
        DomainTestFixtures.setId(part, 1L);

        serviceItem = new ServiceItem("Troca de Óleo", null, new BigDecimal("120.00"), 30);
        DomainTestFixtures.setId(serviceItem, 1L);

        // save() devolve o aggregate remapeado; no teste, mantemos a instância e
        // simulamos a geração de id para uma OS nova. Lenient: nem todo teste salva.
        lenient().when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> {
            WorkOrder arg = inv.getArgument(0);
            if (arg.getId() == null) {
                DomainTestFixtures.setId(arg, 42L);
            }
            return arg;
        });
    }

    @Test
    void listActive_shouldReturnActiveOrders() {
        when(workOrderRepository.findActive(0, 20)).thenReturn(List.of(workOrder));

        List<WorkOrderResponseDto> result = workOrderService.listActive(0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().orderNumber()).isEqualTo("OS-000001");
    }

    @Test
    void listByStatus_shouldFilterByStatus() {
        when(workOrderRepository.findByStatus(WorkOrderStatus.RECEIVED, 0, 20)).thenReturn(List.of(workOrder));

        List<WorkOrderResponseDto> result = workOrderService.listByStatus(WorkOrderStatus.RECEIVED, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(WorkOrderStatus.RECEIVED);
    }

    @Test
    void findById_whenExists_shouldReturn() {
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void findById_whenNotFound_shouldThrow() {
        when(workOrderRepository.fetchById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workOrderService.findById(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withValidData_shouldCreateWorkOrder() {
        when(clientRepository.findByCpfCnpj("11144477735")).thenReturn(Optional.of(client));
        when(vehicleRepository.fetchById(1L)).thenReturn(Optional.of(vehicle));

        WorkOrderCreateDto dto = new WorkOrderCreateDto(
            "111.444.777-35", 1L, "Revisão geral", null, null
        );

        WorkOrderResponseDto result = workOrderService.create(dto);

        assertThat(result.clientCpfCnpj()).isEqualTo("11144477735");
        assertThat(result.orderNumber()).isEqualTo("OS-000042");
        verify(workOrderRepository, atLeastOnce()).save(any(WorkOrder.class));
    }

    @Test
    void create_withWrongVehicleOwner_shouldThrowBusinessException() {
        DomainTestFixtures.setField(vehicle, "clientId", 2L);

        when(clientRepository.findByCpfCnpj("11144477735")).thenReturn(Optional.of(client));
        when(vehicleRepository.fetchById(1L)).thenReturn(Optional.of(vehicle));

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
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.startDiagnosis(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.IN_DIAGNOSIS);
        assertThat(workOrder.getDiagnosisStartedAt()).isNotNull();
    }

    @Test
    void startDiagnosis_fromWrongStatus_shouldThrow() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_EXECUTION);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> workOrderService.startDiagnosis(1L))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void sendForApproval_fromInDiagnosis_shouldChangeStatus() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.sendForApproval(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
        assertThat(workOrder.getSentForApprovalAt()).isNotNull();
    }

    @Test
    void approve_fromAwaitingApproval_shouldChangeStatus() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.AWAITING_APPROVAL);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.approve(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.IN_EXECUTION);
        assertThat(workOrder.getApprovedAt()).isNotNull();
    }

    @Test
    void reject_fromAwaitingApproval_shouldCancelOrder() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.AWAITING_APPROVAL);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.reject(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(workOrder.getCancelledAt()).isNotNull();
    }

    @Test
    void complete_fromInExecution_shouldChangeStatus() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_EXECUTION);
        DomainTestFixtures.setField(workOrder, "executionStartedAt", LocalDateTime.now());
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.complete(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.FINISHED);
        assertThat(workOrder.getFinishedAt()).isNotNull();
    }

    @Test
    void deliver_fromFinished_shouldChangeStatus() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.FINISHED);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.deliver(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.DELIVERED);
        assertThat(workOrder.getDeliveredAt()).isNotNull();
    }

    @Test
    void cancel_fromAnyEditableStatus_shouldCancel() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result = workOrderService.cancel(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void cancel_fromDelivered_shouldThrow() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.DELIVERED);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> workOrderService.cancel(1L))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void addPart_toEditableOrder_shouldDecrementStock() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(part));

        WorkOrderPartDto dto = new WorkOrderPartDto(1L, 2);
        workOrderService.addPart(1L, dto);

        assertThat(part.getStockQuantity()).isEqualTo(8);
        assertThat(workOrder.getParts()).hasSize(1);
    }

    @Test
    void addPart_withInsufficientStock_shouldThrow() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        DomainTestFixtures.setField(part, "stockQuantity", 1);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(part));

        WorkOrderPartDto dto = new WorkOrderPartDto(1L, 5);

        assertThatThrownBy(() -> workOrderService.addPart(1L, dto))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void addService_toEditableOrder_shouldAddWithBasePrice() {
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(serviceItemRepository.fetchById(1L)).thenReturn(Optional.of(serviceItem));

        WorkOrderServiceDto dto = new WorkOrderServiceDto(1L, null);
        workOrderService.addService(1L, dto);

        assertThat(workOrder.getServices()).hasSize(1);
        assertThat(workOrder.getServices().getFirst().getPrice()).isEqualByComparingTo("120.00");
    }

    @Test
    void addService_withInactiveService_shouldThrowBusinessException() {
        serviceItem.deactivate();
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(serviceItemRepository.fetchById(1L)).thenReturn(Optional.of(serviceItem));

        WorkOrderServiceDto dto = new WorkOrderServiceDto(1L, null);

        assertThatThrownBy(() -> workOrderService.addService(1L, dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("inativo");
    }

    @Test
    void reject_shouldRestoreStockOfAllParts() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(part));

        workOrderService.addPart(1L, new WorkOrderPartDto(1L, 3));
        assertThat(part.getStockQuantity()).isEqualTo(7);

        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.AWAITING_APPROVAL);
        workOrderService.reject(1L);

        assertThat(part.getStockQuantity()).isEqualTo(10);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    /** Leva a OS até AWAITING_APPROVAL pelo fluxo real e devolve o token emitido. */
    private String sendForApprovalAndCaptureToken() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        workOrder.sendForApproval();
        return workOrder.getApprovalToken();
    }

    @Test
    void approveByOrderNumber_withMatchingCpfCnpjAndToken_shouldApprove() {
        String token = sendForApprovalAndCaptureToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result =
            workOrderService.approveByOrderNumber("OS-000001", "111.444.777-35", token);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.IN_EXECUTION);
    }

    @Test
    void approveByOrderNumber_withWrongCpfCnpj_shouldThrowNotFound() {
        String token = sendForApprovalAndCaptureToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> workOrderService.approveByOrderNumber("OS-000001", "529.982.247-25", token))
            .isInstanceOf(ResourceNotFoundException.class);
        // Status não pode ter mudado
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
    }

    @Test
    void approveByOrderNumber_withWrongToken_shouldThrowNotFoundWithoutRevealingTheOrder() {
        sendForApprovalAndCaptureToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() ->
            workOrderService.approveByOrderNumber("OS-000001", "111.444.777-35", "token-chutado"))
            .isInstanceOf(InvalidApprovalTokenException.class);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
    }

    @Test
    void approveByOrderNumber_withoutTokenIssued_shouldThrowInvalidToken() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.AWAITING_APPROVAL);
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() ->
            workOrderService.approveByOrderNumber("OS-000001", "111.444.777-35", "qualquer-coisa"))
            .isInstanceOf(InvalidApprovalTokenException.class);
    }

    @Test
    void approveByOrderNumber_reusingTheSameToken_shouldBeRejected() {
        String token = sendForApprovalAndCaptureToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        workOrderService.approveByOrderNumber("OS-000001", "111.444.777-35", token);

        assertThatThrownBy(() ->
            workOrderService.approveByOrderNumber("OS-000001", "111.444.777-35", token))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("já foi utilizado");
    }

    @Test
    void sendForApproval_calledTwice_shouldInvalidateThePreviousToken() {
        String firstToken = sendForApprovalAndCaptureToken();

        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        workOrder.sendForApproval();
        String secondToken = workOrder.getApprovalToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThatThrownBy(() ->
            workOrderService.approveByOrderNumber("OS-000001", "111.444.777-35", firstToken))
            .isInstanceOf(InvalidApprovalTokenException.class);
    }

    @Test
    void rejectByOrderNumber_withMatchingCpfCnpjAndToken_shouldRejectAndRestoreStock() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(part));
        workOrderService.addPart(1L, new WorkOrderPartDto(1L, 3));
        String token = sendForApprovalAndCaptureToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        WorkOrderResponseDto result =
            workOrderService.rejectByOrderNumber("OS-000001", "11144477735", token);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(part.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void rejectByOrderNumber_withWrongCpfCnpj_shouldThrowNotFound() {
        String token = sendForApprovalAndCaptureToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> workOrderService.rejectByOrderNumber("OS-000001", "529.982.247-25", token))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
    }

    @Test
    void rejectByOrderNumber_withWrongToken_shouldNotCancelTheOrder() {
        sendForApprovalAndCaptureToken();
        when(workOrderRepository.findByOrderNumber("OS-000001")).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() ->
            workOrderService.rejectByOrderNumber("OS-000001", "11144477735", "token-chutado"))
            .isInstanceOf(InvalidApprovalTokenException.class);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
    }

    @Test
    void cancel_shouldRestoreStockOfAllParts() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(part));

        workOrderService.addPart(1L, new WorkOrderPartDto(1L, 4));
        assertThat(part.getStockQuantity()).isEqualTo(6);

        workOrderService.cancel(1L);

        assertThat(part.getStockQuantity()).isEqualTo(10);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void sendForApproval_shouldNotifyCustomerWithAwaitingApprovalEvent() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_DIAGNOSIS);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        workOrderService.sendForApproval(1L);

        ArgumentCaptor<WorkOrderStatusChangedEvent> captor =
            ArgumentCaptor.forClass(WorkOrderStatusChangedEvent.class);
        verify(notificationGateway).notifyStatusChange(captor.capture());
        assertThat(captor.getValue().newStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
        assertThat(captor.getValue().orderNumber()).isEqualTo("OS-000001");
    }

    @Test
    void complete_shouldNotifyCustomerWithFinishedEvent() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.IN_EXECUTION);
        DomainTestFixtures.setField(workOrder, "executionStartedAt", LocalDateTime.now());
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        workOrderService.complete(1L);

        ArgumentCaptor<WorkOrderStatusChangedEvent> captor =
            ArgumentCaptor.forClass(WorkOrderStatusChangedEvent.class);
        verify(notificationGateway).notifyStatusChange(captor.capture());
        assertThat(captor.getValue().newStatus()).isEqualTo(WorkOrderStatus.FINISHED);
    }

    @Test
    void deliver_shouldNotifyCustomerWithDeliveredEvent() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.FINISHED);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        workOrderService.deliver(1L);

        ArgumentCaptor<WorkOrderStatusChangedEvent> captor =
            ArgumentCaptor.forClass(WorkOrderStatusChangedEvent.class);
        verify(notificationGateway).notifyStatusChange(captor.capture());
        assertThat(captor.getValue().newStatus()).isEqualTo(WorkOrderStatus.DELIVERED);
    }

    @Test
    void deliver_whenNotificationFails_shouldNotPropagateAndKeepTransition() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.FINISHED);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        doThrow(new RuntimeException("gateway down"))
            .when(notificationGateway).notifyStatusChange(any());

        WorkOrderResponseDto result = workOrderService.deliver(1L);

        // Transição preservada apesar da falha na notificação (defesa em profundidade).
        assertThat(result.status()).isEqualTo(WorkOrderStatus.DELIVERED);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.DELIVERED);
        verify(workOrderRepository, atLeastOnce()).save(workOrder);
    }

    @Test
    void approve_shouldNotNotifyCustomer() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.AWAITING_APPROVAL);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));

        workOrderService.approve(1L);

        verify(notificationGateway, never()).notifyStatusChange(any());
    }

    @Test
    void addPart_toFinishedOrder_shouldThrowBusinessException() {
        DomainTestFixtures.setField(workOrder, "status", WorkOrderStatus.FINISHED);
        when(workOrderRepository.fetchById(1L)).thenReturn(Optional.of(workOrder));
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(part));

        WorkOrderPartDto dto = new WorkOrderPartDto(1L, 1);

        assertThatThrownBy(() -> workOrderService.addPart(1L, dto))
            .isInstanceOf(BusinessException.class);
    }
}
