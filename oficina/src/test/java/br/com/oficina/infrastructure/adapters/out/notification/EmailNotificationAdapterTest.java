package br.com.oficina.infrastructure.adapters.out.notification;

import br.com.oficina.domain.event.WorkOrderStatusChangedEvent;
import br.com.oficina.domain.model.WorkOrderStatus;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Testa o {@link EmailNotificationAdapter} isolando o {@link Mailer} (mock). O campo
 * {@code mailer} tem visibilidade de pacote e o teste está no mesmo pacote — por isso
 * é atribuído diretamente, sem CDI. Cobre as três transições que notificam, os casos
 * que não notificam e o contrato de resiliência (falha de SMTP não propaga).
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationAdapterTest {

    @Mock
    Mailer mailer;

    private EmailNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EmailNotificationAdapter();
        adapter.mailer = mailer;
    }

    private static final String APPROVAL_TOKEN = "tok3n-de-aprovacao-de-uso-unico";

    private static WorkOrderStatusChangedEvent event(WorkOrderStatus status, String email) {
        return new WorkOrderStatusChangedEvent(
            "OS-000001", status, "Maria", email, "Toyota Corolla (ABC1D23)",
            new BigDecimal("250.00"), APPROVAL_TOKEN);
    }

    @Test
    void awaitingApproval_shouldSendApprovalEmail() {
        adapter.notifyStatusChange(event(WorkOrderStatus.AWAITING_APPROVAL, "maria@x.com"));

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mailer).send(captor.capture());
        Mail mail = captor.getValue();
        assertThat(mail.getTo()).containsExactly("maria@x.com");
        assertThat(mail.getSubject()).contains("OS-000001").contains("aprovação");
        assertThat(mail.getText()).contains("Maria").contains("250.00");
        // O e-mail é o único canal por onde o código de autorização chega ao cliente
        assertThat(mail.getText()).contains(APPROVAL_TOKEN);
    }

    @Test
    void finishedAndDeliveredEmails_shouldNotLeakApprovalToken() {
        adapter.notifyStatusChange(event(WorkOrderStatus.FINISHED, "maria@x.com"));
        adapter.notifyStatusChange(event(WorkOrderStatus.DELIVERED, "maria@x.com"));

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mailer, times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
            .allSatisfy(mail -> assertThat(mail.getText()).doesNotContain(APPROVAL_TOKEN));
    }

    @Test
    void finished_shouldSendCompletionEmail() {
        adapter.notifyStatusChange(event(WorkOrderStatus.FINISHED, "maria@x.com"));

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mailer).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("concluído");
    }

    @Test
    void delivered_shouldSendDeliveryEmail() {
        adapter.notifyStatusChange(event(WorkOrderStatus.DELIVERED, "maria@x.com"));

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mailer).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("entregue");
    }

    @Test
    void nonNotifiableStatus_shouldNotSend() {
        adapter.notifyStatusChange(event(WorkOrderStatus.IN_EXECUTION, "maria@x.com"));

        verify(mailer, never()).send(org.mockito.ArgumentMatchers.<Mail>any());
    }

    @Test
    void blankEmail_shouldNotSend() {
        adapter.notifyStatusChange(event(WorkOrderStatus.FINISHED, "  "));

        verify(mailer, never()).send(org.mockito.ArgumentMatchers.<Mail>any());
    }

    @Test
    void smtpFailure_shouldNotPropagate() {
        doThrow(new RuntimeException("smtp down")).when(mailer).send(org.mockito.ArgumentMatchers.<Mail>any());

        assertThatCode(() -> adapter.notifyStatusChange(event(WorkOrderStatus.FINISHED, "maria@x.com")))
            .doesNotThrowAnyException();
    }
}
