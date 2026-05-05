# Documentação DDD — Oficina Mecânica MVP

## 1. Linguagem Ubíqua (Ubiquitous Language)

A linguagem ubíqua estabelece um vocabulário compartilhado entre especialistas de domínio e desenvolvedores.

| Termo                    | Definição                                                                                      |
|--------------------------|-----------------------------------------------------------------------------------------------|
| **Ordem de Serviço (OS)**| Documento que registra todos os serviços e peças relacionados ao atendimento de um veículo. Possui um ciclo de vida bem definido com status. |
| **Cliente**              | Pessoa física (CPF) ou jurídica (CNPJ) que solicita serviços da oficina.                      |
| **Veículo**              | Automóvel identificado por placa, marca, modelo e ano pertencente a um cliente.               |
| **Diagnóstico**          | Análise técnica realizada pelo mecânico para identificar problemas no veículo.                |
| **Orçamento**            | Estimativa de custo calculada automaticamente com base nos serviços e peças da OS.            |
| **Aprovação**            | Autorização do cliente para execução dos serviços após recebimento do orçamento.             |
| **Peça/Insumo**          | Material consumido na execução do serviço (ex.: óleo, filtro, pastilha de freio).            |
| **Estoque**              | Quantidade disponível de peças e insumos para uso nas ordens de serviço.                      |
| **Catálogo de Serviços** | Conjunto de serviços oferecidos pela oficina com preço base e duração estimada.              |
| **Mecânico**             | Profissional responsável pelo diagnóstico e execução dos serviços.                            |
| **Entrega**              | Devolução do veículo ao cliente após conclusão dos serviços.                                 |
| **Status da OS**         | Estado atual da ordem de serviço no seu ciclo de vida.                                       |
| **Tempo de Execução**    | Duração entre o início da execução e a conclusão dos serviços.                               |

---

## 2. Bounded Contexts

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OFICINA MECÂNICA — MVP                               │
│                                                                         │
│  ┌──────────────────┐    ┌───────────────────┐    ┌────────────────┐    │
│  │  Gestão de       │    │  Ordens de        │    │  Catálogo de   │    │
│  │  Clientes e      │◄───│  Serviço          │───►│  Serviços e    │    │
│  │  Veículos        │    │  (Core Domain)    │    │  Peças         │    │
│  └──────────────────┘    └───────────────────┘    └────────────────┘    │
│           │                       │                       │             │
│           └───────────────────────┴───────────────────────┘             │
│                               │                                         │
│                    ┌──────────────────────┐                             │
│                    │  Segurança e         │                             │
│                    │  Autenticação        │                             │
│                    └──────────────────────┘                             │
└─────────────────────────────────────────────────────────────────────────┘
```

### Core Domain: Ordens de Serviço
O domínio central do sistema — gerencia o ciclo de vida completo das OS.

### Supporting Domains:
- **Gestão de Clientes e Veículos**: CRUD de clientes (CPF/CNPJ) e seus veículos.
- **Catálogo de Serviços e Peças**: Gestão do catálogo de serviços oferecidos e estoque de peças.
- **Segurança e Autenticação**: JWT, controle de acesso por papel.

---

## 3. Aggregates, Entities e Value Objects

### Aggregate: WorkOrder (Raiz)
```
WorkOrder (Aggregate Root)
├── id: Long
├── orderNumber: String          [Identificador único de negócio]
├── status: WorkOrderStatus      [Value Object / Enum]
├── client: Client               [Entity Reference]
├── vehicle: Vehicle             [Entity Reference]
├── notes: String
├── totalCost: BigDecimal        [Value Object]
├── timestamps: (createdAt, diagnosisStartedAt, ...)
├── parts: List<WorkOrderPart>   [Entities within Aggregate]
└── services: List<WorkOrderServiceItem> [Entities within Aggregate]
```

### Aggregate: Client
```
Client (Aggregate Root)
├── id: Long
├── name: String
├── cpfCnpj: String              [Value Object — validado]
├── clientType: ClientType       [Value Object / Enum: PF | PJ]
├── email: String
├── phone: String
└── vehicles: List<Vehicle>
```

### Aggregate: Part (Estoque)
```
Part (Aggregate Root)
├── id: Long
├── name: String
├── unitPrice: BigDecimal
├── stockQuantity: Integer       [Invariante: >= 0]
└── unit: String
```

### Domain Services
- `WorkOrder.startDiagnosis()` — transição de estado
- `WorkOrder.sendForApproval()` — calcula totalCost antes da transição
- `WorkOrder.approve() / reject()` — controle de aprovação do cliente
- `Part.decreaseStock(qty)` — invariante de estoque

---

## 4. Event Storming

### Fluxo 1: Criação e Acompanhamento da OS

```
[ATOR: Mecânico/Admin]

COMANDO               EVENTO DE DOMÍNIO           POLÍTICA/REAÇÃO
─────────────────────────────────────────────────────────────────────
Criar OS          →   OsRecebida                → Estoque reservado?
                                                  Notificar cliente
Iniciar Diagnóstico → DiagnosticoIniciado       → Mecânico alocado
Adicionar Serviços  → ServicoAdicionadoNaOs     → Orçamento atualizado
Adicionar Peças     → PecaAdicionadaNaOs        → Estoque decrementado
                                                  Orçamento atualizado
Enviar Orçamento    → OrcamentoEnviadoParaAprovacao → Aguardando cliente
                      (Status: AWAITING_APPROVAL)

[ATOR: Cliente — Via API Pública]

Aprovar Orçamento   → OrcamentoAprovado         → Execução iniciada
                      (Status: IN_EXECUTION)
Rejeitar Orçamento  → OrcamentoRejeitado        → OS cancelada
                      (Status: CANCELLED)       → Estoque restaurado

[ATOR: Mecânico/Admin]

Concluir Serviço    → ServicosConcluidos        → Aguardando entrega
                      (Status: FINISHED)
Registrar Entrega   → VeiculoEntregue           → OS finalizada
                      (Status: DELIVERED)       → Receita contabilizada

[ATOR: Admin]

Cancelar OS         → OsCancelada               → Estoque restaurado
                      (Status: CANCELLED)
```

---

### Fluxo 2: Gestão de Peças e Insumos

```
COMANDO               EVENTO DE DOMÍNIO           POLÍTICA/REAÇÃO
─────────────────────────────────────────────────────────────────────
Cadastrar Peça     →  PecaCadastrada             → Disponível no catálogo
Atualizar Estoque  →  EstoqueAtualizado          → Alerta se abaixo do limite
Adicionar a OS     →  PecaAdicionadaNaOs         → Estoque decrementado
                                                   Preço congelado na OS
Remover da OS      →  PecaRemovidaDaOs           → Estoque restaurado
Excluir Peça       →  PecaExcluida               → Verificar uso em OS ativa
Consultar Estoque  →  EstoqueBaixoDetectado      → Alerta para reposição
```

---

## 5. Diagrama de Estado da OS

```
                    ┌─────────────────────────────────────────────────────┐
                    │                                                     │
         ┌──────────▼──────────┐                                          │
         │      RECEIVED        │                                         │
         │   (OS Criada)        │                                         │
         └──────────┬──────────┘                                          │
                    │ startDiagnosis()                                    │
         ┌──────────▼──────────┐                                          │
         │    IN_DIAGNOSIS      │◄────────── [Mecânico adiciona           │
         │  (Em Diagnóstico)    │             serviços e peças]           │
         └──────────┬──────────┘                                          │
                    │ sendForApproval()                                   │
                    │ (totalCost calculado)                               │
         ┌──────────▼──────────┐                                          │
         │  AWAITING_APPROVAL   │                                         │
         │ (Aguardando Aprovação│                                         │
         └────────┬─────┬──────┘                                          │
         approve()│     │reject()                                         │
                  │     │                                                 │
    ┌─────────────▼─┐ ┌─▼───────────────┐                                 │
    │  IN_EXECUTION  │ │   CANCELLED     │◄────────────────────── cancel()│
    │ (Em Execução)  │ │  (Cancelada)    │                                │
    └───────┬────────┘ └─────────────────┘                                │
            │ complete()                                                  │
    ┌───────▼────────┐                                                    │
    │    FINISHED     │                                                   │
    │  (Concluída)    │                                                   │
    └───────┬────────┘                                                    │
            │ deliver()                                                   │
    ┌───────▼────────┐                                                    │
    │   DELIVERED     │                                                   │
    │   (Entregue)    │                                                   │
    └─────────────────┘                                                   │
                    │                                                     │
                    └─────────────────────────────────────────────────────┘
                                   cancel() pode ser chamado em qualquer
                                   status (exceto DELIVERED e CANCELLED)
```

---

## 6. Diagrama de Domínio (Modelo de Classes)

```
┌─────────────────────┐         ┌─────────────────────┐
│       Client        │ 1     * │       Vehicle       │
├─────────────────────┤─────────├─────────────────────┤
│ id: Long            │         │ id: Long            │
│ name: String        │         │ licensePlate: String│
│ cpfCnpj: String     │         │ brand: String       │
│ clientType: Enum    │         │ model: String       │
│ email: String       │         │ productionYear: Int │
│ phone: String       │         │ client: Client      │
└─────────────────────┘         └─────────────────────┘
          │                                │
          │ 1                              │ 1
          │                                │
          ▼ *                              ▼ *
┌─────────────────────────────────────────────────────────────────┐
│                         WorkOrder                               │
├─────────────────────────────────────────────────────────────────┤
│ id: Long               │ status: WorkOrderStatus                │
│ orderNumber: String    │ totalCost: BigDecimal                  │
│ client: Client         │ notes: String                          │
│ vehicle: Vehicle       │ [timestamps...]                        │
├─────────────────────────────────────────────────────────────────┤
│ + startDiagnosis()                                              │
│ + sendForApproval()                                             │
│ + approve()                                                     │
│ + reject()                                                      │
│ + complete()                                                    │
│ + deliver()                                                     │
│ + cancel()                                                      │
│ + recalculateTotalCost()                                        │
└───────────────────┬──────────────────────────┬──────────────────┘
                    │ *                        │ *
    ┌───────────────▼───────────┐  ┌───────────▼────────────────┐
    │     WorkOrderPart         │  │   WorkOrderServiceItem     │
    ├───────────────────────────┤  ├────────────────────────────┤
    │ id: Long                  │  │ id: Long                   │
    │ part: Part                │  │ serviceItem: ServiceItem   │
    │ quantity: Integer         │  │ price: BigDecimal          │
    │ unitPrice: BigDecimal     │  │ notes: String              │
    └────────────┬──────────────┘  └──────────┬─────────────────┘
                 │ *                           │ *
    ┌────────────▼──────────┐    ┌─────────────▼────────────┐
    │         Part          │    │       ServiceItem        │
    ├───────────────────────┤    ├──────────────────────────┤
    │ id: Long              │    │ id: Long                 │
    │ name: String          │    │ name: String             │
    │ unitPrice: BigDecimal │    │ basePrice: BigDecimal    │
    │ stockQuantity: Int    │    │ estimatedDurationMinutes │
    │ unit: String          │    │ active: Boolean          │
    ├───────────────────────┤    └──────────────────────────┘
    │ + decreaseStock(qty)  │
    │ + increaseStock(qty)  │
    └───────────────────────┘
```

---

## 7. Arquitetura em Camadas

```
┌─────────────────────────────────────────────────────────────────┐
│                    INTERFACES (REST)                            │
│  ClientResource | VehicleResource | PartResource                │
│  ServiceCatalogResource | WorkOrderResource                     │
│  PublicTrackingResource | MetricsResource | AuthResource        │
└──────────────────────────┬──────────────────────────────────────┘
                           │ DTOs
┌──────────────────────────▼──────────────────────────────────────┐
│                   APPLICATION (Services)                        │
│  ClientService | VehicleService | PartService                   │
│  ServiceItemService | WorkOrderService | MetricsService         │
└──────────────────────────┬──────────────────────────────────────┘
                           │ Entities
┌──────────────────────────▼──────────────────────────────────────┐
│                     DOMAIN (Model)                              │
│  Client | Vehicle | Part | ServiceItem                          │
│  WorkOrder | WorkOrderStatus | WorkOrderPart                    │
│  WorkOrderServiceItem                                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ Repository Interfaces
┌──────────────────────────▼──────────────────────────────────────┐
│                  INFRASTRUCTURE                                 │
│  Persistence: ClientRepository | VehicleRepository              │
│              PartRepository | ServiceItemRepository             │
│              WorkOrderRepository (Panache)                      │
│  Security:   AppUser | AppUserRepository | AuthService          │
│  Validation: CpfCnpjValidator | LicensePlateValidator           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 8. Regras de Negócio (Domain Invariants)

1. **Invariante de Estoque**: `part.stockQuantity >= 0` sempre. A operação `decreaseStock()` lança exceção se o estoque for insuficiente.

2. **Invariante de Estado**: Transições inválidas de status lançam `InvalidStatusTransitionException`. Somente as transições permitidas pelo diagrama de estado são aceitas.

3. **CPF/CNPJ Único**: Não é possível cadastrar dois clientes com o mesmo CPF ou CNPJ.

4. **Placa Única**: Não é possível cadastrar dois veículos com a mesma placa.

5. **Associação Veículo-Cliente**: Um veículo só pode ser associado a uma OS se pertencer ao cliente identificado pelo CPF/CNPJ informado.

6. **Edição de OS**: Serviços e peças só podem ser adicionados/removidos em OS com status `RECEIVED` ou `IN_DIAGNOSIS`.

7. **Orçamento Automático**: O `totalCost` da OS é recalculado automaticamente na transição para `AWAITING_APPROVAL`.

8. **Preço Congelado**: O preço unitário de peças e serviços é registrado no momento da adição à OS (não muda se o catálogo for atualizado posteriormente).

9. **Serviço Inativo**: Serviços marcados como `active = false` não podem ser adicionados a novas OS.

10. **Cancelamento**: Uma OS `DELIVERED` não pode ser cancelada.
