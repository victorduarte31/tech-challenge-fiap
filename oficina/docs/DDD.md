# Documentação DDD — Oficina Mecânica MVP

> Os diagramas abaixo estão em [Mermaid](https://mermaid.js.org/) e são
> renderizados automaticamente como imagem pelo GitHub/GitLab.

## 1. Linguagem Ubíqua (Ubiquitous Language)

A linguagem ubíqua estabelece um vocabulário compartilhado entre especialistas de domínio e desenvolvedores.

| Termo                     | Definição                                                                                                                                                                                                                                                                                                |
|---------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Ordem de Serviço (OS)** | Documento que registra todos os serviços e peças relacionados ao atendimento de um veículo. Possui um ciclo de vida bem definido com status.                                                                                                                                                             |
| **Cliente**               | Pessoa física (CPF) ou jurídica (CNPJ) que solicita serviços da oficina.                                                                                                                                                                                                                                 |
| **Veículo**               | Automóvel identificado por placa, marca, modelo e ano pertencente a um cliente.                                                                                                                                                                                                                          |
| **Diagnóstico**           | Análise técnica realizada pelo mecânico para identificar problemas no veículo.                                                                                                                                                                                                                           |
| **Orçamento**             | Estimativa de custo calculada automaticamente com base nos serviços e peças da OS.                                                                                                                                                                                                                       |
| **Aprovação**             | Autorização do cliente para execução dos serviços após recebimento do orçamento.                                                                                                                                                                                                                         |
| **Peça/Insumo**           | Material consumido na execução do serviço (ex.: óleo, filtro, pastilha de freio).                                                                                                                                                                                                                        |
| **Estoque**               | Quantidade disponível de peças e insumos para uso nas ordens de serviço.                                                                                                                                                                                                                                 |
| **Catálogo de Serviços**  | Conjunto de serviços oferecidos pela oficina com preço base e duração estimada.                                                                                                                                                                                                                          |
| **Mecânico**              | Profissional responsável pelo diagnóstico e execução dos serviços.                                                                                                                                                                                                                                       |
| **Entrega**               | Devolução do veículo ao cliente após conclusão dos serviços.                                                                                                                                                                                                                                             |
| **Status da OS**          | Estado atual da ordem de serviço no seu ciclo de vida.                                                                                                                                                                                                                                                   |
| **Tempo de Execução**     | Duração entre o início da execução e a conclusão dos serviços.                                                                                                                                                                                                                                           |
| **Snapshot**              | Projeção em memória dos dados de outro aggregate (cliente/veículo) ou da linha de item, usada para manter a OS isolada do JPA. Apenas o **preço** (unitário da peça / do serviço) é persistido e congelado na linha; nome, CPF/CNPJ e placa são lidos por *join* na leitura e refletem o cadastro atual. |

### Glossário PT ↔ EN (código)

| Português              | Código (inglês)                 |
|------------------------|---------------------------------|
| Ordem de Serviço (OS)  | `WorkOrder`                     |
| Recebida               | `RECEIVED`                      |
| Em diagnóstico         | `IN_DIAGNOSIS`                  |
| Aguardando aprovação   | `AWAITING_APPROVAL`             |
| Em execução            | `IN_EXECUTION`                  |
| Finalizada             | `FINISHED`                      |
| Entregue               | `DELIVERED`                     |
| Cancelada              | `CANCELLED`                     |
| Cliente                | `Client`                        |
| Veículo                | `Vehicle`                       |
| Peça                   | `Part` (`PartType.PECA`)        |
| Insumo                 | `Part` (`PartType.INSUMO`)      |
| Serviço (catálogo)     | `ServiceItem`                   |
| Linha de peça da OS    | `WorkOrderPart`                 |
| Linha de serviço da OS | `WorkOrderServiceItem`          |
| Orçamento              | `getBudget()` / `totalCost`     |
| Estoque                | `stockQuantity`                 |
| Estoque mínimo         | `minimumStock` / `isLowStock()` |

---

## 2. Context Map / Bounded Contexts

O **Core Domain** (Ordens de Serviço) referencia os demais contextos **por
identidade** — guarda apenas o id + um snapshot dos dados necessários, nunca a
entidade do outro aggregate. Esse é o limite de consistência do sistema.

```mermaid
flowchart TB
    subgraph OS["🧩 Ordens de Serviço — Core Domain"]
        WO["WorkOrder (Aggregate Root)"]
    end
    subgraph CV["Gestão de Clientes e Veículos — Supporting"]
        CL["Client"]
        VE["Vehicle"]
    end
    subgraph CAT["Catálogo e Estoque — Supporting"]
        PA["Part"]
        SI["ServiceItem"]
    end
    subgraph SEC["Segurança e Autenticação — Generic"]
        AU["AppUser · JWT · RBAC"]
    end

    CLI["👤 Cliente (App/Web)"]

    WO -->|"ref. por id (CustomerSnapshot)"| CL
    WO -->|"ref. por id (VehicleSnapshot)"| VE
    WO -->|"ref. por id (preço congelado)"| PA
    WO -->|"ref. por id (preço congelado)"| SI
    CLI -->|"Acompanhamento Público:<br/>status · approve · reject<br/>(valida CPF/CNPJ)"| OS
    SEC -.protege.-> OS
    SEC -.protege.-> CV
    SEC -.protege.-> CAT

    style OS fill:#E3F2FD,stroke:#1565C0
    style CLI fill:#FFF3E0,stroke:#EF6C00
```

- **Core Domain — Ordens de Serviço:** ciclo de vida completo da OS, orçamento e aprovação.
- **Supporting — Clientes e Veículos:** CRUD de clientes (CPF/CNPJ) e veículos.
- **Supporting — Catálogo e Estoque:** catálogo de serviços e controle de estoque de peças.
- **Generic — Segurança:** autenticação JWT e controle de acesso por papel.
- **Acompanhamento Público (canal, não um bounded context):** interface REST aberta (`PublicTrackingResource`) para o
  cliente consultar e aprovar/rejeitar a própria OS, expondo uma visão restrita do Core Domain (valida CPF/CNPJ).

---

## 3. Modelo de Domínio — Aggregates, Entities e Value Objects

O aggregate `WorkOrder` é um **POJO puro** (sem JPA). Ele não segura as entidades
`Client`/`Vehicle`/`Part`/`ServiceItem`: referencia cada uma **por id** e expõe
snapshots para exibição. Apenas o **preço** (unitário da peça / do serviço) é
persistido e congelado na linha; os nomes/CPF/placa dos snapshots são
reconstruídos por *join* na leitura — refletem o cadastro atual, preservando o
comportamento anterior à refatoração.

```mermaid
classDiagram
    class WorkOrder {
        <<Aggregate Root>>
        -Long id
        -String orderNumber
        -WorkOrderStatus status
        -BigDecimal totalCost
        +startDiagnosis()
        +sendForApproval()
        +approve()
        +reject()
        +complete()
        +deliver()
        +cancel()
        +addPart(partId, name, qty, price)
        +addService(serviceItemId, name, price, notes)
        +recalculateTotalCost()
    }
    class CustomerSnapshot {
        <<Value Object>>
        +Long clientId
        +String name
        +String cpfCnpj
    }
    class VehicleSnapshot {
        <<Value Object>>
        +Long vehicleId
        +String licensePlate
        +String brand
        +String model
        +Integer productionYear
    }
    class WorkOrderPart {
        <<Entity / linha>>
        +Long partId
        +String partName
        +Integer quantity
        +BigDecimal unitPrice
        +getSubtotal()
    }
    class WorkOrderServiceItem {
        <<Entity / linha>>
        +Long serviceItemId
        +String serviceName
        +BigDecimal price
        +String notes
    }
    class Client {
        <<Aggregate Root>>
        +String cpfCnpj
    }
    class Vehicle {
        <<Aggregate Root>>
        +String licensePlate
    }
    class Part {
        <<Aggregate Root>>
        +Integer stockQuantity
        +decreaseStock(qty)
        +increaseStock(qty)
    }
    class ServiceItem {
        <<Aggregate Root>>
        +Boolean active
    }

    WorkOrder *-- CustomerSnapshot : contém
    WorkOrder *-- VehicleSnapshot : contém
    WorkOrder *-- "0..*" WorkOrderPart : contém
    WorkOrder *-- "0..*" WorkOrderServiceItem : contém
    CustomerSnapshot ..> Client : ref. id
    VehicleSnapshot ..> Vehicle : ref. id
    WorkOrderPart ..> Part : ref. id
    WorkOrderServiceItem ..> ServiceItem : ref. id
```

**Domain Services / comportamento de domínio**

- `WorkOrder.startDiagnosis() / sendForApproval() / approve() / reject() / complete() / deliver() / cancel()` —
  transições de estado válidas.
- `WorkOrder.sendForApproval()` — recalcula `totalCost` antes da transição.
- `WorkOrder.recalculateTotalCost()` — orçamento = Σ subtotais de peças + Σ preços de serviços.
- `Part.decreaseStock(qty) / increaseStock(qty)` — invariante de estoque (`>= 0`).
- **Estoque cross-aggregate:** a reserva (ao adicionar peça) e a devolução (ao
  remover/rejeitar/cancelar) **não** ficam no `WorkOrder` — são coordenadas pelo
  `WorkOrderService`, pois envolvem dois aggregates (OS e Part) na mesma transação.

---

## 4. Diagrama de Estado da OS

> **Nota sobre o status `CANCELLED`**
> O Tech Challenge lista 6 status do *fluxo feliz* (`RECEIVED`, `IN_DIAGNOSIS`,
> `AWAITING_APPROVAL`, `IN_EXECUTION`, `FINISHED`, `DELIVERED`). Adotamos um
> sétimo, `CANCELLED`, para modelar explicitamente a rejeição do orçamento e o
> cancelamento administrativo. Sem ele, a alternativa seria reaproveitar
> `FINISHED`/`DELIVERED` (distorce métricas) ou apagar a OS (perde
> rastreabilidade). `CANCELLED` preserva o histórico, segrega métricas e mantém a
> invariante de que toda transição é auditável. A inclusão é **complementar**.

```mermaid
stateDiagram-v2
    [*] --> RECEIVED
    RECEIVED --> IN_DIAGNOSIS : startDiagnosis()
    IN_DIAGNOSIS --> AWAITING_APPROVAL : sendForApproval() / calcula totalCost
    AWAITING_APPROVAL --> IN_EXECUTION : approve()
    AWAITING_APPROVAL --> CANCELLED : reject() / restaura estoque
    IN_EXECUTION --> FINISHED : complete()
    FINISHED --> DELIVERED : deliver()
    DELIVERED --> [*]

    RECEIVED --> CANCELLED : cancel()
    IN_DIAGNOSIS --> CANCELLED : cancel()
    AWAITING_APPROVAL --> CANCELLED : cancel()
    IN_EXECUTION --> CANCELLED : cancel()
    FINISHED --> CANCELLED : cancel()
    CANCELLED --> [*]

    note right of CANCELLED
        cancel() é permitido em qualquer
        status, exceto DELIVERED e CANCELLED.
        A devolução de estoque é coordenada
        pelo WorkOrderService.
    end note
    note left of IN_DIAGNOSIS
        Peças e serviços só podem ser
        adicionados/removidos em
        RECEIVED ou IN_DIAGNOSIS.
    end note
```

---

## 5. Event Storming — Fluxo 1: Criação e Acompanhamento da OS

🟦 Comando · 🟧 Evento de domínio · 🟪 Política/Reação · 🟨 Ator

```mermaid
flowchart TB
    classDef cmd fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef evt fill:#EF6C00,stroke:#E65100,color:#fff
    classDef pol fill:#6A1B9A,stroke:#4A148C,color:#fff
    classDef actor fill:#FBC02D,stroke:#F9A825,color:#000

    A1["👤 Mecânico / Admin"]:::actor
    A2["👤 Cliente (API pública)"]:::actor

    C1["Criar OS"]:::cmd --> E1["OS Recebida<br/>RECEIVED"]:::evt
    E1 --> C2["Iniciar Diagnóstico"]:::cmd --> E2["Diagnóstico Iniciado<br/>IN_DIAGNOSIS"]:::evt
    E2 --> C3["Adicionar Serviço"]:::cmd --> E3["Serviço Adicionado"]:::evt --> P3["Orçamento recalculado"]:::pol
    E2 --> C4["Adicionar Peça"]:::cmd --> E4["Peça Adicionada"]:::evt --> P4["Estoque decrementado +<br/>orçamento recalculado<br/>(WorkOrderService)"]:::pol
    E2 --> C5["Enviar Orçamento"]:::cmd --> E5["Orçamento Enviado<br/>AWAITING_APPROVAL"]:::evt
    E5 --> C6["Aprovar Orçamento"]:::cmd --> E6["Orçamento Aprovado<br/>IN_EXECUTION"]:::evt
    E5 --> C7["Rejeitar Orçamento"]:::cmd --> E7["Orçamento Rejeitado<br/>CANCELLED"]:::evt --> P7["Estoque restaurado<br/>(WorkOrderService)"]:::pol
    E6 --> C8["Concluir Serviços"]:::cmd --> E8["Serviços Concluídos<br/>FINISHED"]:::evt
    E8 --> C9["Registrar Entrega"]:::cmd --> E9["Veículo Entregue<br/>DELIVERED"]:::evt --> P9["Receita contabilizada"]:::pol

    A1 -.-> C1
    A1 -.-> C2
    A2 -.-> C6
    A2 -.-> C7
    A1 -.-> C8
    A1 -.-> C9
```

---

## 6. Event Storming — Fluxo 2: Gestão de Peças e Insumos

```mermaid
flowchart TB
    classDef cmd fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef evt fill:#EF6C00,stroke:#E65100,color:#fff
    classDef pol fill:#6A1B9A,stroke:#4A148C,color:#fff

    D1["Cadastrar Peça"]:::cmd --> F1["Peça Cadastrada"]:::evt --> Q1["Disponível no catálogo"]:::pol
    D2["Atualizar Estoque"]:::cmd --> F2["Estoque Atualizado"]:::evt --> Q2["Alerta se ≤ estoque mínimo"]:::pol
    D3["Adicionar Peça à OS"]:::cmd --> F3["Peça Adicionada na OS"]:::evt --> Q3["Estoque decrementado +<br/>preço congelado na linha"]:::pol
    D4["Remover Peça da OS"]:::cmd --> F4["Peça Removida da OS"]:::evt --> Q4["Estoque restaurado"]:::pol
    D5["Excluir Peça"]:::cmd --> F5["Peça Excluída<br/>(soft delete)"]:::evt --> Q5["Verifica uso em OS ativa"]:::pol
    D6["Consultar Estoque"]:::cmd --> F6["Estoque Baixo Detectado"]:::evt --> Q6["Alerta de reposição"]:::pol
```

---

## 7. Arquitetura e Isolamento do Domínio

Arquitetura em camadas com o **core domain isolado de JPA**. O `WorkOrder` é um
POJO; a persistência vive na infraestrutura (`WorkOrderEntity` + `WorkOrderMapper`)
e o `WorkOrderRepository` é um adapter que traduz entidade ↔ domínio.

```mermaid
flowchart TB
    subgraph IN["interfaces (REST)"]
        R["WorkOrderResource · PublicTrackingResource<br/>ClientResource · PartResource · MetricsResource · AuthResource"]
    end
    subgraph APP["application"]
        S["WorkOrderService<br/>(orquestra estoque cross-aggregate)"]
        DTO["DTOs"]
    end
    subgraph DOM["domain (PURO — sem framework)"]
        AG["WorkOrder (Aggregate Root)<br/>WorkOrderPart · WorkOrderServiceItem<br/>CustomerSnapshot · VehicleSnapshot"]
    end
    subgraph INF["infrastructure"]
        PE["persistence:<br/>WorkOrderEntity (+ linhas) · WorkOrderMapper"]
        RP["repository:<br/>WorkOrderRepository (adapter)"]
        SUP["Client · Vehicle · Part · ServiceItem (entidades JPA)"]
    end

    R --> S
    S --> DTO
    S --> AG
    S --> RP
    RP --> PE
    PE --> AG
    PE -. mapeia FK .-> SUP

    style DOM fill:#E8F5E9,stroke:#2E7D32
```

> Os *supporting domains* (`Client`, `Vehicle`, `Part`, `ServiceItem`) permanecem
> como entidades JPA — decisão proporcional: isola-se o core, onde está a
> complexidade de negócio, sem pagar o custo de mapeamento nos CRUDs simples.

---

## 8. Regras de Negócio (Domain Invariants)

1. **Invariante de Estoque:** `part.stockQuantity >= 0` sempre. `decreaseStock()`
   lança exceção se o estoque for insuficiente. A reserva/devolução entre OS e
   Part é coordenada pelo `WorkOrderService` (cross-aggregate, mesma transação).
2. **Invariante de Estado:** transições inválidas lançam `InvalidStatusTransitionException`.
   Somente as transições do diagrama de estado são aceitas.
3. **CPF/CNPJ Único:** não é possível cadastrar dois clientes com o mesmo CPF/CNPJ.
4. **Placa Única:** não é possível cadastrar dois veículos com a mesma placa.
5. **Associação Veículo-Cliente:** um veículo só entra numa OS se pertencer ao cliente identificado pelo CPF/CNPJ.
6. **Edição de OS:** peças e serviços só podem ser adicionados/removidos em status `RECEIVED` ou `IN_DIAGNOSIS`.
7. **Orçamento Automático:** o `totalCost` é recalculado a cada inclusão/remoção e na transição para
   `AWAITING_APPROVAL`.
8. **Preço Congelado:** o preço unitário de peças e o preço de serviços são gravados na linha no momento da inclusão (
   não mudam se o catálogo for atualizado depois).
9. **Serviço/Peça Inativo:** itens `active = false` não podem ser adicionados a novas OS.
10. **Cancelamento:** uma OS `DELIVERED` não pode ser cancelada.
11. **Referência por Identidade:** o aggregate `WorkOrder` referencia os demais aggregates por id (+ snapshot), nunca
    por referência direta à entidade de persistência.
