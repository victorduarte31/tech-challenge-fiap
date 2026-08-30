## 1. Refactor arquitetural — Hexagonal formal

### Contexto

`Client`, `Vehicle`, `Part`, `ServiceItem` em `domain/model/` são hoje `@Entity` JPA. O domínio depende de
framework de persistência, o que viola a separação de camadas exigida pelo desafio. `WorkOrder` já segue o
padrão correto (POJO + `rehydrate()` + mapper + entity em `infrastructure.persistence`) e serve de referência
de migração — não de cópia mecânica, de **padrão a respeitar**.

### Contrato

Ao final do refactor, para cada uma das quatro entidades (`Client`, `Vehicle`, `Part`, `ServiceItem`):

- A classe em `domain/model/` não pode importar nenhum pacote `jakarta.persistence.*` nem qualquer
  anotação de framework (JPA, Panache, Hibernate). É um POJO de negócio puro.
- Deve existir um construtor de negócio (validações de invariante no momento da criação) e um método estático
  `rehydrate(...)` para reconstrução a partir de persistência, sem repetir validação de invariante de criação.
- Deve existir uma classe `XxxEntity` em `infrastructure/persistence/` carregando as anotações JPA que saíram
  do domínio.
- Deve existir um `XxxMapper` com `toDomain(XxxEntity): Xxx` e `toEntity(Xxx): XxxEntity`, sem lógica de
  negócio dentro do mapper — só tradução de dados.
- O repositório Panache passa a operar exclusivamente sobre `XxxEntity`. Um adapter
  (`infrastructure/adapters/out/XxxRepositoryAdapter`) implementa o port `XxxRepositoryPort` (definido em
  `domain/ports/out/`), e é o único ponto do sistema que conhece tanto o domínio quanto a entity.
- Nenhuma classe fora de `infrastructure/persistence/` e `infrastructure/adapters/out/` pode importar
  `XxxEntity` diretamente. Em particular, `application/service/` e `interfaces/rest/` só conhecem o port e o
  modelo de domínio.

**Critério para criação de port `in`:** só cria interface de caso de uso quando há regra de negócio não-trivial
associada (`CreateWorkOrderUseCase`, `ChangeWorkOrderStatusUseCase`, `ApproveBudgetUseCase`,
`ListWorkOrdersUseCase`). CRUD simples de `Client`/`Vehicle`/`Part`/`ServiceItem` não ganha port `in` — o
service concreto injetado direto no controller já cumpre esse papel; uma interface sem segunda implementação
real é abstração vazia, e abstração vazia é dívida técnica disfarçada de boa prática.

### Estrutura de pacotes alvo

```
domain/
  model/        WorkOrder, Client, Vehicle, Part, ServiceItem (POJOs puros)
  ports/
    out/        ClientRepositoryPort, VehicleRepositoryPort, PartRepositoryPort,
                ServiceItemRepositoryPort, WorkOrderRepositoryPort, NotificationGatewayPort
infrastructure/
  persistence/  ClientEntity, VehicleEntity, PartEntity, ServiceItemEntity (+ mappers), repositórios Panache
  adapters/out/ XxxRepositoryAdapter implements XxxRepositoryPort
  adapters/out/notification/ EmailNotificationAdapter implements NotificationGatewayPort
application/
  ports/
    in/         casos de uso com regra de negócio (ver critério acima) — ports `in` vivem na camada de
                aplicação por serem contratos de orquestração de caso de uso, não de domínio puro
  service/      implementam os ports in (quando existem) ou são a porta de entrada de fato (CRUD simples);
                dependem apenas de ports out, nunca de Panache ou Entity diretamente
```

`NotificationGatewayPort` é definido **aqui**, não na spec de API — a spec de API (seção 2.5) consome esse
port como pré-requisito já satisfeito; não o redeclara.

### Critério de Aceite

- Build não compila se qualquer classe em `domain/model/` importar `jakarta.persistence.*` — validável com
  busca estática (`grep -r "jakarta.persistence" src/main/java/.../domain/model/` deve retornar vazio).
- Suíte de testes existente (anterior ao refactor) passa sem alteração de asserts — só ajustes de
  construção de objeto se a mudança de tipo vazar para o teste (não deveria).
- Para cada entidade migrada, existe teste de mapper (`toDomain`/`toEntity` ida e volta preserva os dados).
- Cobertura JaCoCo dos pacotes `domain.model` e `infrastructure.adapters.out` permanece ≥ 80%.

### Ordem de execução

Client → Vehicle → Part → ServiceItem, uma de cada vez. Suíte verde obrigatória antes de seguir para a
próxima. Não paralelizar — o risco de regressão é por entidade, paralelizar mistura causa-efeito se algo
quebrar.

### Dependências

Nenhuma — esta é a spec base. Specs de API, testes e docker dependem dela (consomem `NotificationGatewayPort`
e os ports `out` aqui definidos).

### Skill de Apoio

`clean-ddd-hexagonal` — consultar antes de iniciar a migração de cada entidade, em especial para validar o
desenho de port `out` e o limite entre `application/service` e `domain/model`.

### Fora de Escopo

- Mudança de `WorkOrder` (já está no padrão correto, não é tocado aqui).
- Criação de port `in` para CRUDs simples (decisão explícita: não criar — ver critério acima).
- Qualquer mudança em `interfaces/rest/` além do necessário para não vazar `XxxEntity` (se o DTO já isola
  isso, esta seção não tem trabalho a fazer).

### Rollback

Migração é por entidade e committed isoladamente. Se uma migração quebrar a suíte de forma não trivial,
reverter o commit daquela entidade especificamente — não impacta as anteriores, que já estão validadas e
mergeadas.