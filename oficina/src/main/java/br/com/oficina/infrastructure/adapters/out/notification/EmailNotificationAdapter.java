package br.com.oficina.infrastructure.adapters.out.notification;

import br.com.oficina.domain.event.WorkOrderStatusChangedEvent;
import br.com.oficina.domain.ports.out.NotificationGatewayPort;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Adapter de saída que notifica o cliente por e-mail nas mudanças de status da OS,
 * usando o {@link Mailer} do Quarkus. O envio é tolerante a falha: erros de SMTP
 * são apenas logados e nunca propagados, para não quebrar a transição de status
 * (a notificação é um efeito colateral, não parte da invariante do domínio).
 *
 * <p>Os templates abaixo são padrão e podem ser ajustados livremente.</p>
 */
@ApplicationScoped
public class EmailNotificationAdapter implements NotificationGatewayPort {

    private static final Logger LOG = Logger.getLogger(EmailNotificationAdapter.class);

    @Inject
    Mailer mailer;

    @Override
    public void notifyStatusChange(WorkOrderStatusChangedEvent event) {
        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            LOG.warnf("OS %s sem e-mail de cliente cadastrado; notificação ignorada.", event.orderNumber());
            return;
        }

        Mail mail = buildMail(event);
        if (mail == null) {
            return; // status sem notificação associada
        }

        try {
            mailer.send(mail);
            LOG.infof("Notificação de status (%s) enviada para %s referente à OS %s",
                event.newStatus(), event.customerEmail(), event.orderNumber());
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao enviar notificação por e-mail da OS %s", event.orderNumber());
        }
    }

    private Mail buildMail(WorkOrderStatusChangedEvent e) {
        return switch (e.newStatus()) {
            case AWAITING_APPROVAL -> Mail.withText(e.customerEmail(),
                "Orçamento da OS %s aguardando sua aprovação".formatted(e.orderNumber()),
                awaitingApprovalBody(e));
            case FINISHED -> Mail.withText(e.customerEmail(),
                "Serviço da OS %s concluído".formatted(e.orderNumber()),
                finishedBody(e));
            case DELIVERED -> Mail.withText(e.customerEmail(),
                "Veículo da OS %s entregue".formatted(e.orderNumber()),
                deliveredBody(e));
            default -> null;
        };
    }

    private String awaitingApprovalBody(WorkOrderStatusChangedEvent e) {
        return """
            Olá, %s!

            O diagnóstico do seu veículo %s foi concluído e o orçamento está pronto.

            Ordem de Serviço: %s
            Valor total do orçamento: R$ %s

            Para aprovar ou recusar o orçamento, acesse o acompanhamento da sua OS
            informando o número acima e o seu CPF/CNPJ.

            Atenciosamente,
            Oficina Mecânica
            """.formatted(e.customerName(), e.vehicleDescription(), e.orderNumber(), e.totalCost());
    }

    private String finishedBody(WorkOrderStatusChangedEvent e) {
        return """
            Olá, %s!

            O serviço do seu veículo %s foi finalizado.

            Ordem de Serviço: %s
            Valor total: R$ %s

            Em breve entraremos em contato para combinar a retirada do veículo.

            Atenciosamente,
            Oficina Mecânica
            """.formatted(e.customerName(), e.vehicleDescription(), e.orderNumber(), e.totalCost());
    }

    private String deliveredBody(WorkOrderStatusChangedEvent e) {
        return """
            Olá, %s!

            Confirmamos a entrega do seu veículo %s.

            Ordem de Serviço: %s

            Obrigado pela confiança. Estamos à disposição!

            Atenciosamente,
            Oficina Mecânica
            """.formatted(e.customerName(), e.vehicleDescription(), e.orderNumber());
    }
}
