## 2. Evolução das APIs de negócio

### Dependências

Requer `spec-hexagonal.md` concluída: assume `WorkOrderRepositoryPort`, `NotificationGatewayPort` e os ports
`out` das demais entidades já existentes e implementados. Não redeclara nenhum port aqui.

---

### 2.1 Abertura de OS unificada

**Contexto:** hoje a criação de OS referencia client/vehicle existentes e serviços/peças são adicionados em
chamadas separadas (`POST /admin/work-orders/{id}/services` e `/parts`). O desafio exige receber dados do
cliente, veículo, serviços e peças em uma única abertura.

**Contrato:** o endpoint de criação de OS aceita opcionalmente uma lista de serviços e uma lista de peças no
mesmo payload. Quando presentes, são associadas à OS na mesma transação da criação, reaproveitando as regras
de negócio já existentes (não duplicar validação de service/part). Quando ausentes ou vazias, o comportamento
é idêntico ao atual — criar a OS vazia, associar depois via endpoints existentes.

**Critério de Aceite:**

- Dado um payload de criação com `services` e `parts` preenchidos, a OS resultante contém exatamente os itens
  enviados, em uma única chamada, sem necessidade de chamadas subsequentes.
- Dado um payload sem `services`/`parts` (ou com listas vazias), a OS é criada sem itens — comportamento
  idêntico ao pré-existente, validado por teste de regressão.
- Se um `serviceItemId` ou `partId` inexistente for enviado, a transação inteira falha (não cria OS parcial) e
  retorna erro 4xx identificando qual item é inválido.
- Teste cobrindo: payload completo, payload vazio, payload com item inválido (rollback de transação).

**Fora de Escopo:** mudança nos endpoints `/services` e `/parts` existentes — continuam funcionando como hoje
para adições posteriores a uma OS já criada.

---

### 2.2 Consulta de status (label em português)

**Contexto:** os endpoints já existem e funcionam (`GET /public/work-orders/{orderNumber}/status`,
`GET /admin/work-orders/{id}`). O desafio pede que o status seja exibido em português: Recebida, Diagnóstico,
Aguardando Aprovação, Execução, Finalizada, Entregue.

**Contrato:** a resposta do DTO público expõe um label de exibição em português, sem alterar o enum interno
`WorkOrderStatus` (que continua em inglês/código, é contrato interno de domínio, não de apresentação).

**Critério de Aceite:**

- Cada valor de `WorkOrderStatus` mapeia para exatamente um label em português, sem ambiguidade.
- Teste parametrizado cobrindo os 6 valores do enum e seu label esperado.
- Nenhum teste existente que dependa do enum interno quebra (label é campo adicional, não substituição).

**Fora de Escopo:** internacionalização genérica (i18n com múltiplos idiomas) — não foi pedido, não entra.

---

### 2.3 Aprovação de orçamento

**Contexto:** já existe via `POST /public/work-orders/{orderNumber}/approve|reject`, validando CPF/CNPJ. Não
há mudança de comportamento — apenas lacuna de documentação.

**Contrato:** README/Swagger documenta explicitamente que esses endpoints são o ponto de entrada para
integrações externas (incluindo o link que será enviado por e-mail no item 2.5).

**Critério de Aceite:** Swagger/OpenAPI gerado mostra descrição não vazia nesses dois endpoints, explicando o
fluxo de uso por terceiros. Nenhuma mudança de código de produção é necessária para esta seção — é puramente
documentação.

---

### 2.4 Listagem com ordenação por prioridade e exclusão lógica

**Contexto:** mudança real necessária no `WorkOrderRepositoryPort`/adapter.

**Contrato:** a listagem de OS (`GET /admin/work-orders`, sem filtro de status explícito) retorna ordenada por
prioridade operacional — `IN_EXECUTION` antes de `AWAITING_APPROVAL` antes de `IN_DIAGNOSIS` antes de
`RECEIVED` — e dentro do mesmo status, mais antigas primeiro. OS em `FINISHED`/`DELIVERED` ficam de fora da
listagem por padrão, mas continuam consultáveis explicitamente via filtro `status=`.

A ordenação é responsabilidade do port/adapter; a forma de implementação (query JPQL, `Comparator` em memória,
ou outra) fica a critério de quem implementa, desde que o contrato de ordem seja respeitado e a performance
não degrade com volume de dados real do domínio (centenas a poucos milhares de registros — não é um requisito
de escala que justifique índice dedicado nesta fase).

**Critério de Aceite:**

- Teste de adapter com dados seedados em 4 status diferentes mais um par dentro do mesmo status com
  `createdAt` distintos: a ordem retornada respeita a prioridade e, dentro do mesmo status, a ordem temporal.
- Sem filtro de status, OS `FINISHED`/`DELIVERED` não aparecem na resposta.
- Com filtro `status=FINISHED` explícito, aparecem normalmente.

**Fora de Escopo:** exclusão física de qualquer registro — não existe nesta spec nem em nenhuma outra do
conjunto.

---

### 2.5 Notificação por e-mail em transições de status

**Contexto:** não existe hoje (`quarkus-mailer` ausente do `pom.xml`).

**Contrato:**

- `NotificationGatewayPort` (definido em `spec-hexagonal.md`) ganha implementação real:
  `EmailNotificationAdapter` em `infrastructure/adapters/out/notification/`, usando `Mailer` do Quarkus.
- `WorkOrderService` dispara notificação ao transicionar para `AWAITING_APPROVAL` (pedir aprovação) e para
  `FINISHED`/`DELIVERED` (avisar conclusão/entrega).
- Falha de envio (SMTP indisponível, timeout) não pode quebrar a transição de status em si — é
  responsabilidade não-funcional, tratada e logada, nunca propagada como exceção que reverte a transação de
  domínio.
- Ambiente local: Mailpit no `docker-compose.yml` (ver `spec-docker.md`) com `MAILER_HOST=mailpit`,
  `MAILER_PORT=1025` — fonte de verdade dessas duas variáveis é esta seção; `spec-docker.md` consome, não
  redefine.
- Produção (k3s): SMTP real via `Secret` Kubernetes (ver `spec-kubernetes.md`).

**Critério de Aceite:**

- Teste unitário com `NotificationGatewayPort` mockado: transição para `AWAITING_APPROVAL` chama `notify(...)`
  exatamente uma vez com o evento correto; mesma verificação para `FINISHED` e `DELIVERED`.
- Teste simulando falha do mock (lança exceção): a transição de status é persistida normalmente, e a exceção
  do envio é capturada e logada — não propaga.
- `docker compose up` local: ao disparar uma transição via API, o e-mail correspondente aparece na UI do
  Mailpit (`:8025`) — validação manual documentada no checklist de entrega, não automatizável sem dependência
  externa no CI.

**Fora de Escopo:** retry de envio, fila de notificação, templates HTML ricos de e-mail — texto simples é
suficiente para o escopo acadêmico.