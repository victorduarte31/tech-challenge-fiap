# Roteiro de Apresentação — Tech Challenge Fase 1 (Oficina Mecânica)

> Documento de apoio para a gravação do vídeo / apresentação da banca.
> Cada tópico traz **o que foi feito**, a **justificativa técnica** e a **evidência no código**,
> sempre mapeado contra os requisitos do PDF `15SOAT - Fase 1 - Tech Challenge`.

---

## Slide 0 — Abertura / contexto do desafio

**Fala:** A oficina hoje opera com anotações manuais e planilhas, gerando 5 dores
(priorização, controle de peças, status, histórico, fluxo de orçamento/aprovação). A proposta
do PDF é o **MVP do back-end** de um *Sistema Integrado de Atendimento e Execução de Serviços*,
com foco em **OS, clientes e peças**, aplicando **DDD** e boas práticas de **Qualidade e Segurança**.

**Justificativa de escopo:** o enunciado pede um MVP monolítico em camadas — portanto **não**
introduzimos microsserviços, mensageria ou CQRS. Decisão consciente de *anti-overengineering*:
a complexidade arquitetural foi mantida proporcional ao domínio.

---

## Slide 1 — Stack tecnológica e justificativa

| Tecnologia | Versão | Por quê |
|---|---|---|
| Java | 21 LTS | Linguagem LTS, records, padrões modernos |
| Quarkus | 3.15.x | Startup rápido, baixo consumo, cloud/GraalVM-ready |
| PostgreSQL | 16 | Dados fortemente relacionais + ACID |
| Hibernate ORM Panache | 3.15 | ORM com padrão Repository |
| Flyway | — | Versionamento de schema |
| SmallRye JWT | — | Autenticação stateless (RSA-256) |
| SmallRye OpenAPI | — | Swagger (requisito do PDF) |
| JUnit 5 / Mockito / REST-Assured / JaCoCo | — | Testes + gate de cobertura |

**Justificativa do Quarkus (vs Spring Boot):** o enunciado é livre quanto ao framework.
Escolhemos Quarkus pelo perfil **cloud-native** do desafio (Docker + docker-compose, health
checks, baixo footprint). É a exceção justificada à preferência por Spring: o MVP é simples e o
ganho de startup/memória é alinhado à entrega conteinerizada.

---

## Slide 2 — Arquitetura (monólito em camadas)

**Fala:** O PDF exige explicitamente *"Back-end monolítico"* e *"Monolito utilizando arquitetura
em camadas"*. Entregamos exatamente isso, com 4 camadas:

```
domain/         → model (entidades ricas) + exception   (NÃO conhece framework de fora)
application/    → service (casos de uso) + dto
infrastructure/ → repository (Panache) + security (JWT) + validation
interfaces/     → rest (JAX-RS) + exception (mappers HTTP)
```

**Justificativa / regras respeitadas:**
- Controllers (`interfaces/rest`) **só chamam services**, nunca repositories.
- Domínio é **rico**, não anêmico — a regra de negócio mora na entidade (ver Slide 4).
- DTOs isolam o domínio da borda HTTP (nunca expomos entidade JPA).
- **Trade-off assumido:** mantivemos JPA annotations dentro de `domain/model` (não é hexagonal
  pura com ports/adapters). Decisão consciente: para um MVP CRUD + máquina-de-estados, a
  hexagonal completa seria overengineering; camadas + domínio rico entregam testabilidade
  suficiente sem fragmentação excessiva.

---

## Slide 3 — Modelo de domínio (DDD tático)

**Fala:** O *aggregate root* é a **`WorkOrder`** (Ordem de Serviço). Ela controla suas linhas de
peça (`WorkOrderPart`) e de serviço (`WorkOrderServiceItem`) — coleções `final`, expostas como
`unmodifiableList`, garantindo que ninguém mexa nos itens por fora do agregado.

- Entidades: `Client`, `Vehicle`, `Part` (+ `PartType`), `ServiceItem`, `WorkOrder`.
- Enums: `ClientType`, `PartType {PECA, INSUMO}`, `WorkOrderStatus`.

**Justificativa:** invariantes ficam protegidas no agregado. Ex.: só dá pra adicionar
peça/serviço com a OS em estado editável; o estoque é debitado **dentro** do `addPart`. Atende
a regra "regras de negócio pertencem ao domínio" e à *Linguagem Ubíqua* exigida no entregável DDD.

---

## Slide 4 — Máquina de estados da OS (coração do desafio)

**Fala:** O PDF lista 6 status: Recebida → Em diagnóstico → Aguardando aprovação → Em execução →
Finalizada → Entregue. Modelamos como **enum + state machine no domínio** (`WorkOrderStatus` +
métodos em `WorkOrder`):

```
RECEIVED → IN_DIAGNOSIS → AWAITING_APPROVAL → IN_EXECUTION → FINISHED → DELIVERED
                                  ↓ (reject/cancel)
                              CANCELLED
```

Cada transição valida o estado de origem via `requireStatus(...)`, lançando
`InvalidStatusTransitionException` quando inválida: `startDiagnosis()`, `sendForApproval()`,
`approve()`, `reject()`, `complete()`, `deliver()`, `cancel()`.

**Aderência ao PDF — "Alteração automática dos status conforme ações":** cada ação dispara a
transição correta e ainda **carimba timestamps** (`diagnosisStartedAt`, `approvedAt`,
`finishedAt`...), que depois alimentam a métrica de tempo médio.

**Regra de negócio relevante:** ao **rejeitar/cancelar**, o estoque das peças é **devolvido**
(`restoreStockOfAllParts()`); ao adicionar peça, é **debitado**. Liga a máquina de estados ao
controle de estoque de forma transacional e consistente.

---

## Slide 5 — Fluxo de criação da OS + orçamento automático

| Requisito PDF | Implementação |
|---|---|
| Identificação do cliente por CPF/CNPJ | `WorkOrderService.create` busca cliente por CPF/CNPJ normalizado |
| Cadastro de veículo (placa, marca, modelo, ano) | entidade `Vehicle` + validação de placa |
| Inclusão de serviços | `addService` (valida serviço ativo) |
| Inclusão de peças/insumos | `addPart` (debita estoque) |
| **Orçamento gerado automaticamente** | `recalculateTotalCost()` soma peças + serviços a cada alteração; exposto via `getBudget()` |
| Envio do orçamento p/ aprovação | `sendForApproval()` (recalcula e muda status) |

**Justificativa:** o orçamento **não é campo editável** — é derivado de peças×preço + serviços
(fonte única da verdade, elimina inconsistência). Há regra de integridade: o veículo precisa
pertencer ao cliente informado, senão `BusinessException`.

---

## Slide 6 — Acompanhamento pela API + canal público (cliente)

**Fala:** O PDF pede *"consulta por parte do cliente via API para acompanhar o progresso"* e
*"autorizar reparos adicionais via aplicativo"*. Resolvemos com um **bounded context de
Acompanhamento Público** (`PublicTrackingResource`, `@PermitAll`):

- `GET /public/work-orders/{orderNumber}/status` — consulta status **sem expor dados pessoais**
  (DTO anonimizado `PublicWorkOrderDto`).
- `POST .../approve` e `.../reject` — cliente aprova/rejeita o orçamento remotamente.

**Justificativa de segurança (decisão importante):** o canal público **exige CPF/CNPJ no corpo**
como prova de identidade. Se não bater com o cliente da OS, retorna **404 (mensagem genérica)** —
igual a "OS inexistente" — para **não vazar** se a OS existe. Mantivemos também um canal
**administrativo** (`/admin/.../approve`) para registrar aprovação presencial/telefônica, com
auditoria via JWT do operador.

---

## Slide 7 — Gestão administrativa (CRUDs + métricas)

| Requisito | Recurso REST |
|---|---|
| CRUD de clientes | `ClientResource` (`/admin/clients`) |
| CRUD de veículos | `VehicleResource` |
| CRUD de serviços | `ServiceCatalogResource` |
| CRUD de peças/insumos **com controle de estoque** | `PartResource` |
| Listagem/detalhamento de OS | `WorkOrderResource` |
| **Tempo médio de execução** | `MetricsResource` + `MetricsService` |

**Métrica de tempo médio:** calculada a partir dos timestamps
`executionStartedAt`/`finishedAt` (`getExecutionDurationMinutes`), agregada em `MetricsService`.
O endpoint também devolve total/abertas/finalizadas/canceladas, receita entregue e contagem de
itens com estoque baixo.

---

## Slide 8 — Controle de estoque (peças × insumos)

**Fala:** O PDF pede *"CRUD de peças e insumos, com controle de estoque"*. Implementamos:

- `Part.partType` (`PECA` | `INSUMO`) — atende explicitamente "peças **e** insumos" da
  Linguagem Ubíqua.
- `Part.minimumStock` + `Part.isLowStock()` — alerta de reposição por **estoque mínimo
  configurável por peça** (não um limite global fixo).
- `GET /admin/parts/low-stock` — lista o que precisa repor.
- Débito/crédito de estoque acoplado à máquina de estados da OS (Slide 4).
- Migration **`V2__add_stock_control_to_parts.sql`** adiciona as colunas + índice.

**Justificativa:** controle por mínimo individual reflete a realidade (cada item tem giro
diferente) e ataca diretamente a dor *"Falhas no controle de peças e insumos"*.

---

## Slide 9 — Segurança (JWT + validação de dados sensíveis)

**Autenticação/autorização:**
- **JWT RSA-256** (par de chaves 2048-bit), stateless, expiração de 8h configurável.
- **RBAC**: `@RolesAllowed({"ADMIN","MECHANIC"})` nas APIs admin; `cancel` da OS restrito a **ADMIN**.
- Senhas com **BCrypt**; comparação em **tempo constante** mesmo com usuário inexistente
  (`DUMMY_HASH`) — evita *timing attack* que vazaria usuários válidos.
- Mensagem de erro genérica ("Credenciais inválidas").
- **Seed sem senha hardcoded**: em produção gera senha aleatória e loga uma vez; recomenda desabilitar.

**Validação de dados sensíveis (CPF/CNPJ e placa):**
- `@ValidCpfCnpj` + `CpfCnpjValidator`/`CpfCnpjUtils` — valida **dígitos verificadores**, não só formato.
- `@ValidLicensePlate` — aceita formato antigo **e Mercosul**.
- Bean Validation nos DTOs (`@Valid` nos controllers).

**Justificativa:** atende OWASP (A01/A02/A03/A07) e a regra de validar entrada na borda, nunca
confiar só no frontend.

---

## Slide 10 — Tratamento de erros e exposição segura

**Fala:** `GlobalExceptionMapper` e `ValidationExceptionMapper` traduzem exceções de domínio em
HTTP correto (`ResourceNotFoundException`→404, `BusinessException`→400/409, validação→422/400).
Em erros 500, devolvemos um **`correlationId`** em vez de stack trace.

**Justificativa:** atende OWASP A04/A05 e a regra "nunca expor stacktrace ao cliente" — o
`correlationId` permite rastrear no log sem vazar internals.

---

## Slide 11 — Persistência e banco de dados (justificativa pedida no PDF)

**Fala:** O PDF exige justificar a escolha do banco. Escolhemos **PostgreSQL** porque o domínio é
fortemente relacional (Cliente → Veículo → OS → Peças/Serviços) com integridade referencial e
transações ACID, `DECIMAL(10,2)` exato para valores monetários, e ótima integração
Quarkus/Hibernate/Flyway.

- **Flyway** versiona o schema (`V1` schema inicial, `V2` controle de estoque) — `migrate-at-start=true`.
- Índices criados para os padrões de consulta reais (status da OS, CPF/CNPJ, placa, FKs).
- **Testes** usam **H2 em modo PostgreSQL** — mesmo dialeto, sem custo de infra no pipeline.

---

## Slide 12 — APIs RESTful + Swagger (requisito do PDF)

**Fala:** APIs JAX-RS documentadas via **SmallRye OpenAPI**:
- Swagger UI: `/swagger-ui`; OpenAPI JSON: `/openapi`.
- Anotações `@Operation`, `@Tag`, `@SecurityRequirement(bearerAuth)`.

**Justificativa de hardening (perfis):**
- `%prod` → **Swagger/OpenAPI desabilitados** (não expor superfície em produção).
- `%docker` → Swagger **habilitado** para a banca avaliar a demo localmente.
- `%test` → H2 + chaves de teste.

Satisfaz simultaneamente "documentar via Swagger" e a boa prática de não publicar o catálogo de
API em produção.

---

## Slide 13 — Conteinerização (Dockerfile + docker-compose)

**Dockerfile:** *multi-stage* (build Maven → runtime `temurin:21-jre-alpine`):
- **Usuário não-root** (`oficina`) — reduz superfície de ataque.
- `HEALTHCHECK` em `/q/health/live`.
- `JAVA_OPTS` com limites de heap; cache de dependências.

**docker-compose:** orquestra **PostgreSQL + app** com:
- `depends_on: condition: service_healthy` (app só sobe com banco saudável).
- Volumes persistentes (`postgres_data`, `jwt_keys`).
- Variáveis externalizadas com defaults.
- Perfil `docker` injetado via `QUARKUS_PROFILE`.

**Justificativa:** atende "Dockerfile para build" + "docker-compose.yml para orquestrar ambiente
completo" + execução local simples (README), tudo cloud-native.

---

## Slide 14 — Testes e cobertura (≥80% nos domínios críticos)

- **Unitários** dos services (`WorkOrderServiceTest`, `ClientServiceTest`, etc.) e do domínio
  (`PartTest`, `WorkOrderBudgetTest`, `CpfCnpjUtilsTest`).
- **Integração REST** com REST-Assured (`WorkOrderResourceTest`, `AuthResourceTest`,
  `PublicTrackingResourceTest`, `MetricsResourceTest`, `PartResourceTest`...).
- `DomainTestFixtures` para dados de teste reutilizáveis.
- **JaCoCo com gate de 80%** incluindo `domain.model`.

**Justificativa:** priorizamos cobrir os fluxos críticos do enunciado — criação/transições da OS,
orçamento, estoque e autenticação — alinhado à estratégia de testes por criticidade.

---

## Slide 15 — Relatório de vulnerabilidades (entregável obrigatório)

**Fala:** `docs/VULNERABILITY_REPORT.md` traz a análise **OWASP Top 10 (2021)** categoria por
categoria + análise de dependências:
- A01–A08, A10 → **Mitigado** (risco baixo/muito baixo).
- A09 (Logging/Monitoring) → **Parcial/atenção** (falta trilha de auditoria e monitoramento
  centralizado) — honestidade técnica, não inflamos o status.
- CVEs transitivos mitigados no `pom.xml` (overrides de `commons-lang3`, `plexus-utils`,
  Jackson BOM, JDBC PostgreSQL) — alinhados ao scan Mend.io.
- Recomendações priorizadas para produção (HTTPS no perímetro, secret manager, rate limiting,
  refresh tokens).

**Justificativa:** o PDF pede o relatório do scan no código; entregamos análise estática +
revisão manual com riscos residuais explícitos.

---

## Slide 16 — Checklist final de aderência ao PDF

| Requisito do PDF | Status |
|---|---|
| Back-end monolítico em camadas | ✅ |
| Criação de OS (CPF/CNPJ, veículo, serviços, peças, orçamento auto, envio p/ aprovação) | ✅ |
| 6 status + alteração automática + consulta via API pelo cliente | ✅ |
| CRUD clientes/veículos/serviços/peças+insumos com estoque | ✅ |
| Listagem/detalhe de OS + tempo médio de execução | ✅ |
| JWT nas APIs admin | ✅ |
| Validação de dados sensíveis (CPF/CNPJ, placa) | ✅ |
| Testes unitários e de integração (≥80%) | ✅ |
| Justificativa do banco | ✅ (PostgreSQL) |
| Swagger | ✅ |
| Dockerfile + docker-compose | ✅ |
| README explicativo | ✅ |
| Documentação DDD (Event Storming, diagramas, Linguagem Ubíqua) | ✅ no Miro (entregável separado) |
| Relatório de vulnerabilidades | ✅ |

---

## Slide 17 — Decisões, trade-offs e evolução futura (fechamento)

1. **Anti-overengineering proposital** — monólito em camadas, comunicação síncrona, sem
   eventos/CQRS. Justificado pelo escopo MVP.
2. **Trade-off hexagonal** — domínio rico com JPA inline em vez de ports/adapters completos;
   ganho de simplicidade sem perder testabilidade.
3. **Reparos adicionais pós-aprovação** ficaram **fora de escopo** (a máquina de estados só
   permite edição em `RECEIVED`/`IN_DIAGNOSIS`) — limite consciente do MVP.
4. **Evolução:** rate limiting no login, refresh tokens, trilha de auditoria (A09),
   observabilidade (Prometheus/Grafana), secret manager e HTTPS no perímetro.