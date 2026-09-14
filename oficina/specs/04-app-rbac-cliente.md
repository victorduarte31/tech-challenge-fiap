# E4 — Aplicação: papel CLIENT, posse do recurso, correlação e métricas de negócio

> Atende a metade "aplicação" de R2, prepara R7 (métricas de negócio e correlação de logs) e o fluxo de
> "abertura de ordem de serviço" do diagrama de sequência de R8. Repositório: **`oficina-app`**.

## 1. Dependências

- [01](01-modelagem-banco.md): `clients.status` e `ClientStatus` no domínio.
- [02](02-auth-cpf-lambda.md): contrato dos claims (`groups: ["CLIENT"]`, `clientId`).
- [03](03-api-gateway.md): header `X-Gateway-Key`.

## 2. Contexto — o que muda e o que não muda

A aplicação já valida JWT RS256 com `mp.jwt.verify.publickey.location` e faz RBAC com
`@RolesAllowed`. Como o Lambda assina com **a mesma chave e o mesmo `iss`**, o token do cliente é
aceito sem nenhuma mudança de configuração de segurança. O que falta é: (a) um papel `CLIENT` com
regras próprias, (b) a garantia de que um cliente só enxerga o que é dele, (c) rastreabilidade de
requisição, (d) as métricas que alimentam os dashboards.

**O que não muda:** estrutura hexagonal, portas existentes, fluxo de status da OS, autenticação de
funcionário por usuário/senha, canal público de aprovação por token de uso único.

## 3. Papel CLIENT e superfície de rotas

Novo recurso `MeResource` (`@Path("/me")`), todo ele `@RolesAllowed("CLIENT")`:

| Método | Rota | Caso de uso | Regra |
|---|---|---|---|
| `GET` | `/me` | dados do próprio cadastro | id vem do token, nunca do path |
| `GET` | `/me/vehicles` | veículos do cliente | filtrado por `clientId` do token |
| `GET` | `/me/work-orders` | OSs do cliente, paginadas | filtrado por `clientId` do token |
| `GET` | `/me/work-orders/{id}` | detalhe de uma OS | 404 (não 403) se a OS for de outro cliente |
| `POST` | `/me/work-orders` | **abertura de OS pelo cliente** | veículo precisa pertencer ao cliente |
| `POST` | `/me/work-orders/{id}/approval` | aprovar/recusar orçamento | só em `AWAITING_APPROVAL` |

**404 e não 403 para recurso de outro cliente:** 403 confirmaria que a OS `N` existe, entregando
enumeração do volume de ordens da oficina. Diferença registrada no ADR de autorização.

**Onde a regra de posse vive.** Não no controller e não repetida em cada método de serviço:
um `OwnershipGuard` na camada de aplicação (`application/security/`), recebendo o `AuthenticatedCaller`
(record com `clientId`, `role`) e o agregado, com métodos `assertOwns(WorkOrder)` /
`assertOwns(Vehicle)`. Os casos de uso de `/me` recebem o caller como parâmetro explícito — o domínio
continua sem conhecer `SecurityContext`, `JsonWebToken` ou qualquer coisa do Quarkus. O adapter REST
traduz `JsonWebToken` → `AuthenticatedCaller`. Essa fronteira é o que mantém os testes de caso de uso
sem `@QuarkusTest`.

**Abertura de OS pelo cliente** reaproveita `CreateWorkOrderUseCase`; muda apenas quem chama e a
validação prévia de posse do veículo. Nenhum caso de uso novo — evitar duplicar a máquina de estados
é mais importante do que ter um caso de uso "de cliente".

## 4. Filtro do gateway

`GatewayKeyFilter` (`ContainerRequestFilter`, prioridade `AUTHENTICATION - 100`):

- Aplica-se a `/admin/*` e `/me/*`. Não se aplica a `/q/health/*` (o kubelet chama direto no pod) nem
  a `/q/metrics` (scrape interno).
- Compara `X-Gateway-Key` com `app.gateway.key` em **tempo constante** (`MessageDigest.isEqual`).
- Ausente ou divergente → 403 com corpo genérico.
- Se `app.gateway.key` estiver vazio (perfis `dev`/`test`/`docker`), o filtro é desativado — senão todo
  desenvolvimento local passa a exigir o header, e a configuração acabaria copiada com valor real para
  a máquina do desenvolvedor.

## 5. Correlação de requisições

Exigência literal de R7: "logs estruturados (JSON), incluindo correlação entre requisições".

`CorrelationIdFilter` (request + response):

1. Lê `X-Correlation-Id`; se ausente, usa `X-Amzn-Request-Id` (posto pelo API Gateway); se ausente,
   gera um UUID.
2. Põe em `MDC` como `correlationId`, junto de `clientId`/`username` quando houver token.
3. Devolve o valor em `X-Correlation-Id` na resposta — é o que o usuário do vídeo copia para achar a
   requisição no New Relic.
4. Limpa o MDC no `filter` de resposta (obrigatório: thread pool reaproveita threads e o valor
   vazaria para a próxima requisição).

Com `quarkus-opentelemetry` ativo, `traceId`/`spanId` entram no MDC automaticamente e o log JSON passa
a carregar os três identificadores. Isso conecta log ↔ trace no New Relic sem configuração extra.

Ajuste em `application.properties`: garantir que o MDC seja serializado no JSON de produção
(`quarkus.log.console.json.additional-field.*` para campos fixos como `service.name` e `env`).

## 6. Métricas de negócio

Os dashboards de R7 precisam de dados que hoje não existem como métrica. Instrumentação com
Micrometer (já no projeto), exposta em `/q/metrics` e coletada conforme [05](05-observabilidade.md):

| Métrica | Tipo | Tags | Alimenta |
|---|---|---|---|
| `oficina_work_orders_created_total` | Counter | `origin` (`client`/`staff`) | Volume diário de OS |
| `oficina_work_order_status_transitions_total` | Counter | `from`, `to` | Funil de status |
| `oficina_work_order_status_duration_seconds` | Timer | `status` | **Tempo médio por status** (Diagnóstico, Execução, Finalização) |
| `oficina_work_order_transition_errors_total` | Counter | `reason` (`invalid_transition`, `stock`, `persistence`) | **Alerta de falha no processamento de OS** |
| `oficina_integration_failures_total` | Counter | `integration` (`smtp`, `database`) | Erros nas integrações |
| `oficina_auth_denied_total` | Counter | `reason` | Tentativas de acesso negadas |

O `Timer` de duração é registrado **na transição**, calculando `now - timestamp_do_status_anterior` a
partir das colunas `*_at` que a OS já mantém. Sem tabela nova e sem job de agregação.

**Onde instrumentar:** em `WorkOrderService` (camada de aplicação), não no domínio. `WorkOrder`
continua um POJO sem dependência de Micrometer — instrumentar o domínio quebraria a regra de "domínio
não conhece framework" por conveniência de telemetria.

## 7. Ajustes de infraestrutura da aplicação

- **NetworkPolicy**: liberar egresso TCP 443 (exportador OTLP para o New Relic). Hoje só 53, 5432 e 587
  — com a política ativa em um CNI que a implemente, a telemetria simplesmente não sairia.
- **ConfigMap**: `APP_GATEWAY_KEY` (via Secret), `OTEL_*`, `NEW_RELIC_*`, `LAMBDA_RO_PASSWORD`
  (placeholder do Flyway), `QUARKUS_DATASOURCE_JDBC_MAX_SIZE=8`.
- **Overlay por ambiente**: como `hml` e `prod` são **clusters distintos**
  ([06](06-infra-k8s-terraform.md) §4), o namespace continua sendo `oficina` nos dois — o que muda é o
  overlay `kustomize` (contagem de réplicas, limites do HPA, valores de ConfigMap, `APP_SEED_ENABLED`).
  Os manifestos passam a ser parametrizados por `kustomize` em vez de `sed` no YAML: o `sed` do
  pipeline atual já é um remendo e não sobrevive a dois ambientes.
- **`APP_SEED_ENABLED`**: hoje `true` no ConfigMap "para a gravação do vídeo". Passa a `false` em
  produção e `true` só em homologação; a demonstração usa credenciais criadas uma vez.

## 8. Testes

- Matriz RBAC como teste parametrizado: para cada rota × papel (`ANON`, `CLIENT`, `MECHANIC`, `ADMIN`),
  o status esperado. É o teste que pega regressão de anotação — o modo mais comum de abrir uma rota
  por acidente.
- `OwnershipGuardTest`: cliente A não lê OS do cliente B (404), lê a própria (200).
- `GatewayKeyFilterTest`: sem header → 403; header errado → 403; perfil sem chave → passa.
- `CorrelationIdFilterTest`: gera quando ausente, propaga quando presente, devolve no header, limpa MDC.
- Testes de métrica: após uma transição, `/q/metrics` contém o counter e o timer esperados.
- Gate JaCoCo: incluir `br.com.oficina.application.security` e `br.com.oficina.interfaces.filter` nos
  pacotes com mínimo de 80%.

## 9. Critério de aceite

- Token emitido pelo Lambda acessa `/me/work-orders` e recebe **apenas** as OSs do próprio CPF.
- Token de `MECHANIC` em `/me/*` → 403; token de `CLIENT` em `/admin/*` → 403.
- Toda resposta traz `X-Correlation-Id`; o mesmo valor aparece no log JSON do pod.
- Abertura de OS por cliente incrementa `oficina_work_orders_created_total{origin="client"}`.
- `mvn verify` verde com o gate JaCoCo ampliado.
- Coleção Postman atualizada com o fluxo completo (token por CPF → abrir OS → acompanhar → aprovar).

## 10. Fora de escopo

- Autocadastro de cliente pela API pública (o cliente precisa existir na base — é premissa do fluxo de
  autenticação por CPF).
- Alteração de dados cadastrais pelo próprio cliente.
- Substituir o canal público por token de uso único (`/public/work-orders`): continua existindo, agora
  como alternativa para quem não autentica. Convergir os dois canais é evolução futura, registrada no ADR.
