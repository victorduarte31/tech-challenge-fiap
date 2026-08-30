# Fase 2 — Evolução da Oficina Mecânica (Hexagonal + AWS + CI/CD)

## Contexto

A Fase 1 entregou uma aplicação Quarkus funcional, já em camadas (domain/application/infrastructure/interfaces), com
máquina de estados de OS robusta, segurança JWT/RBAC e 80% de cobertura de testes em pacotes críticos. A Fase 2 exige (
a) formalizar a arquitetura como Hexagonal de fato, (b) evoluir 5 APIs de negócio, e (c) entregar a cadeia completa de
infraestrutura: Docker → Kubernetes (k3s) → Terraform (AWS) → CI/CD.

Decisões já fechadas com o usuário:

- **Cloud:** AWS. Conta **AWS Academy Learner Lab**, região fixa **`us-east-1`**.
- **Kubernetes:** k3s self-managed em **1 EC2 única** — não Amazon EKS gerenciado. Motivo: o orçamento de
  **US\$50 é único para o curso inteiro (não mensal)**, e o control plane do EKS (US\$0,10/h fixo) **não é
  parado quando a sessão do AWS Academy termina** (só instâncias EC2 param automaticamente com "End Lab") —
  risco desproporcional para o ganho, já que o desafio aceita explicitamente cluster "local ou cloud" (PDF,
  p.4). Justificativa completa e trade-offs em `oficina/spec-terraform-aws.md`, seção "Decisão de
  arquitetura".
- **Orçamento:** US\$50, único para todo o curso, não recarrega. `terraform destroy` ao final de **cada**
  sessão de trabalho contra a AWS é **obrigatório**, não opcional (comando pronto em
  `oficina/spec-terraform-aws.md`).
- **Refactor:** purificação completa do domínio (Client/Vehicle/Part/ServiceItem deixam de ser `@Entity` e
  passam a ser POJOs puros, no mesmo padrão já usado por `WorkOrder`), com ports in/out explícitos.

**Status do gap original** (Client/Vehicle/Part/ServiceItem como `@Entity` JPA no domínio, violando a
separação de camadas exigida pelo desafio): **resolvido.** Confirmado nesta revisão via gate estático (`grep
-r "jakarta.persistence" domain/model/` retorna vazio). `WorkOrder` continua sendo o padrão de referência
(POJO + `rehydrate()` + mapper + entity em `infrastructure.persistence`), agora replicado nas quatro
entidades. O que resta desse esforço é debito de teste, não de estrutura — ver item 1 abaixo.

## Ordem de execução recomendada — status revalidado em 2026-07-15

Todos os itens foram checados por inspeção direta do código e por `mvn verify` (202 testes verdes, gate JaCoCo
aprovado) nesta revisão — não apenas por mensagem de commit.

1. ✅ **Refactor hexagonal estrutural** — feito (commit `be6b282`), confirmado por gate estático. **Débito de
   teste fechado nesta revisão:** existem agora `ClientMapperTest`, `VehicleMapperTest`, `PartMapperTest`,
   `ServiceItemMapperTest` em `src/test/.../infrastructure/persistence/` (ida-e-volta entity⇄domínio). Critério
   de aceite de `spec-hexagonal.md` satisfeito. `oficina/spec-hexagonal.md`.
2. ✅ **Ports `in`** para os casos de uso de WorkOrder com regra de negócio — feito (`CreateWorkOrderUseCase`,
   `ChangeWorkOrderStatusUseCase`, `ApproveBudgetUseCase`, `ListWorkOrdersUseCase`,
   `ManageWorkOrderItemsUseCase` em `application/ports/in/`). `oficina/spec-hexagonal.md`.
3. ✅ **API:** criação unificada de OS (`WorkOrderCreateDto`), label de status em PT-BR
   (`WorkOrderStatusLabel`), ordenação/exclusão lógica na listagem (commit `cc316a0`) — feito.
   `oficina/spec-api.md`.
4. ✅ **NotificationGatewayPort + EmailNotificationAdapter + Mailpit no compose** (commit `08f7c8f`) — feito;
   `mailpit` presente em `docker-compose.yml`. `oficina/spec-hexagonal.md`, `oficina/spec-docker.md`.
5. ✅ **Testes novos** cobrindo os itens 3 e 4 — **feito nesta revisão**: `EmailNotificationAdapterTest`
   (3 transições que notificam + casos que não notificam + falha de SMTP não propagando) e
   `WorkOrderRepositoryAdapterTest` (`@QuarkusTest` da ordenação customizada 2.4, com 4 status semeados e
   exclusão de FINISHED/DELIVERED). `WorkOrderServiceTest` ganhou verificação de notificação nas transições.
   Pacote `infrastructure.adapters.out.notification` incluído no gate JaCoCo. `oficina/spec-test-unit.md`.
6. ✅ **Docker compose revisado** (serviço `mailpit`, variáveis `MAILER_HOST`/`MAILER_PORT` injetadas) — feito.
   `oficina/spec-docker.md`.
7. ✅ **Manifestos K8s** (Deployment/Service/ConfigMap/Secret/HPA sobre k3s) — já existem em `oficina/k8s/`.
   `oficina/spec-kubernetes.md`.
8. ✅ **Terraform AWS** (EC2 + k3s + RDS PostgreSQL + ECR, conta AWS Academy) — já existe em `oficina/infra/`.
   `oficina/spec-terraform-aws.md`.
9. ✅ **Pipeline CI/CD** — já existe em `.github/workflows/ci-cd.yml` (corrigido nesta revisão: dispara em
   `master`, gating explícito dos jobs de deploy). `oficina/spec-github-actions.md`.
10. ⬜ **Documentação final** (README, diagramas). `oficina/spec.document.md`.

> Débito de teste (itens 1 e 5) **fechado**. O que resta para a entrega é o item 10 (documentação/diagramas) e
> a execução real contra a AWS (apply + demo de HPA para o vídeo). O código de infraestrutura (7-9) está
> escrito e revisado; falta apenas exercitá-lo numa sessão AWS Academy — ver `SESSION-GUIDE.md`.

## Verificação

- `mvn verify` local após cada etapa de refactor/teste (gate JaCoCo deve continuar passando).
- `docker compose up` local validando app + Mailpit (capturar e-mail de teste na UI do Mailpit em `:8025`).
- `terraform plan` revisado manualmente antes de qualquer `apply` real contra AWS — **gate de custo
  obrigatório** (estimativa vs. orçamento único de US\$50, ver `spec-terraform-aws.md`).
- Após `terraform apply` + `kubectl apply -f k8s/`: `kubectl get pods,hpa,svc -n oficina` e teste de carga
  simples (`hey`/`k6`) para observar o HPA escalando réplicas — isso é o que o vídeo demonstrativo precisa
  capturar.
- **`terraform destroy` ao final de cada sessão de trabalho contra a AWS** — obrigatório (não apenas ao
  final do projeto). Comando e verificação de recursos órfãos em `spec-terraform-aws.md`. Motivo: contas AWS
  Academy só param automaticamente instâncias EC2 ao encerrar a sessão — RDS e demais recursos continuam
  cobrando até serem destruídos explicitamente.
