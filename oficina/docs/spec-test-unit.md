## 3. Testes automatizados

### Dependências

Requer `spec-hexagonal.md` e `spec-api.md` concluídas — testa o comportamento que elas definem, não pode ser
escrita (nem executada com sentido) antes.

### Contexto

A Fase 1 já entrega 80% de cobertura em pacotes críticos. Esta seção cobre exclusivamente o que é **novo**
nesta fase: não é uma reescrita da suíte existente.

### Contrato

- `WorkOrderServiceTest` ganha os cenários definidos em `spec-api.md` 2.1 (criação unificada) e 2.5
  (notificação mockada nas três transições: `AWAITING_APPROVAL`, `FINISHED`, `DELIVERED`), incluindo o
  cenário de falha do mock não propagando exceção.
- Novo teste de adapter cobrindo a ordenação customizada de `spec-api.md` 2.4, com os 4 status seedados.
- Para `Client`, `Vehicle`, `Part`, `ServiceItem`: a suíte pré-existente (`ClientServiceTest` etc.) não é
  reescrita — passa inalterada após a migração de `spec-hexagonal.md`. Se algum teste precisar de ajuste, é
  sinal de vazamento de camada e deve ser tratado como defeito da migração, não como atualização de teste.

### Critério de Aceite

- Gate JaCoCo de 80% mantido no projeto como um todo.
- `<includes>` do JaCoCo no `pom.xml` estendido para `domain.ports` e `infrastructure.adapters` **somente se**
  esses pacotes acumularem lógica não trivial (branches, validação) — getters/setters e classes anêmicas não
  justificam inclusão forçada de cobertura.
- `mvn verify` local passa de ponta a ponta após cada etapa de refactor de `spec-hexagonal.md` — este é o gate
  de avanço entre entidades, não apenas verificação final.
- Nenhum teste novo depende de SMTP real, rede externa ou Mailpit rodando — notificação é sempre mockada em
  teste unitário; a validação com Mailpit real é manual (ver `spec-api.md` 2.5), não faz parte da suíte
  automatizada.

### Fora de Escopo

- Testes de carga/performance (ficam para a etapa de validação de HPA, fora do `mvn verify`).
- Testes de integração contra AWS real (Terraform/EC2+k3s) — fora do escopo de teste automatizado desta fase.