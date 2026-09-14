# Fase 3 — Visão geral e plano de execução

> Índice das specs desta fase. Cada spec é uma etapa executável, com contrato, critério de aceite e
> escopo fechado. Nada aqui altera código — é o plano que precede a implementação.

## 1. O que a Fase 3 pede (extraído do PDF `13SOAT - Fase 3 - Tech Challenge.pdf`)

| # | Requisito obrigatório | Spec | Repositório destino |
|---|---|---|---|
| R1 | API Gateway (AWS API Gateway, Kong, Traefik ou outro) para controle e roteamento | [03](03-api-gateway.md) | `oficina-auth-lambda` |
| R2 | Proteger rotas sensíveis da aplicação com autenticação via CPF | [02](02-auth-cpf-lambda.md), [04](04-app-rbac-cliente.md) | lambda + app |
| R3 | Function Serverless: validar CPF, consultar existência/status do cliente na base, gerar e devolver JWT | [02](02-auth-cpf-lambda.md) | `oficina-auth-lambda` |
| R4 | Quatro repositórios separados, cada um com CI/CD e deploy automático | [08](08-repositorios-cicd.md) | todos |
| R5 | `main` protegida, PR obrigatório, deploy automático de homologação e produção | [08](08-repositorios-cicd.md) | todos |
| R6 | Infra: API Gateway + Function Serverless + Banco gerenciado + Cluster K8s escalável + Terraform | [03](03-api-gateway.md), [06](06-infra-k8s-terraform.md), [07](07-infra-db-terraform.md) | infra + lambda |
| R7 | Observabilidade: latência, CPU/memória do K8s, healthcheck/uptime, alertas de falha em OS, logs JSON correlacionados, 3 dashboards | [05](05-observabilidade.md) | app + lambda + infra |
| R8 | Documentação: diagrama de componentes, diagramas de sequência, RFCs, ADRs, justificativa do banco + ER | [09](09-documentacao-arquitetural.md) | todos |
| R9 | Melhorar e documentar a modelagem do banco (consistência e performance) | [01](01-modelagem-banco.md) | app + `oficina-infra-database` |

Requisitos de entrega **fora do escopo deste plano** (responsabilidade do usuário, ao final):
documento PDF de entrega, vídeo de até 15 minutos, e a inclusão do usuário `soat-architecture` nos
repositórios. A aplicação `oficina-front/` também está fora de escopo nesta fase.

## 2. Ponto de partida (o que já existe e permanece)

Levantado por inspeção direta do repositório atual:

- **Aplicação** Quarkus 3.15 / Java 21, hexagonal formal (`domain` sem dependência de framework, ports
  `in`/`out`, mappers, adapters), 200+ testes com gate JaCoCo de 80% em pacotes críticos.
- **Segurança** JWT RS256 via SmallRye (`mp.jwt.verify.publickey.location`), RBAC com `ADMIN`/`MECHANIC`,
  BCrypt com comparação em tempo constante.
- **Infra** Terraform: VPC (1 subnet pública + 2 privadas sem NAT), EC2 `t3.medium` com k3s, RDS
  PostgreSQL 16 `db.t4g.micro` privado, ECR, security groups mínimos, state em S3 com `use_lockfile`.
- **Kubernetes**: Deployment com probes/startupProbe, HPA (CPU 70%, 2→4), Ingress Traefik,
  NetworkPolicy, PDB, `readOnlyRootFilesystem`, `runAsNonRoot`, Secret de chaves JWT montado em volume.
- **CI/CD**: pipeline única com build+testes, `terraform fmt/validate`, build+scan Trivy+push ECR,
  `terraform apply`, deploy via túnel SSM (6443 nunca exposta), smoke test e rollback automático.
- **Observabilidade parcial**: `/q/metrics` (Micrometer/Prometheus), `/q/health/*`, log JSON em `prod`.

**Consequência para o plano:** a Fase 3 é majoritariamente *adição* (auth serverless, gateway,
observabilidade externa, split de repositórios) e não reescrita. O único refactor estrutural é o split
dos repositórios ([08](08-repositorios-cicd.md)).

## 3. Decisões estruturantes propostas

Cada decisão vira um ADR em [09](09-documentacao-arquitetural.md). Aqui está o resumo com o motivo dominante.

| Decisão | Escolha proposta | Motivo dominante | Alternativa rejeitada |
|---|---|---|---|
| Nuvem | AWS (Academy Learner Lab) | Continuidade da Fase 2; todo o Terraform já existe | Azure/GCP — reescrita sem ganho |
| API Gateway | **AWS API Gateway HTTP API (v2)** | US$1,00/milhão de requisições, **sem custo por hora**; integra Lambda e HTTP nativamente | Kong/Traefik como gateway: exigiria uma EC2 a mais ou não integra Lambda de forma limpa |
| Kubernetes | **k3s single-node em EC2** (mantido) | Control plane do EKS custa US$0,10/h = US$73/mês e **não para com o "End Lab"**; consumiria o orçamento inteiro em ~20 dias | EKS gerenciado |
| Banco | **RDS PostgreSQL** (mantido) | Invariantes transacionais de OS, relacionamentos, ACID; é "gerenciado" como o PDF exige | Aurora Serverless v2 (piso de 0,5 ACU ≈ US$43/mês); DynamoDB (modelo relacional do domínio) |
| Function serverless | **Lambda Java 21 + SnapStart, dentro da VPC** | SnapStart é gratuito e reduz cold start de ~3s para ~400ms; dentro da VPC alcança o RDS privado sem NAT | Lambda fora da VPC (não alcança RDS privado); NAT Gateway (US$32/mês) |
| Segredos do Lambda | Variáveis de ambiente (KMS `aws/lambda`) | VPC sem NAT não alcança a API do Secrets Manager; VPC endpoint custa US$7,20/mês por interface | Secrets Manager / SSM via VPC endpoint |
| Observabilidade | **New Relic** | Free tier **perpétuo** de 100 GB/mês com APM + logs + infra + dashboards + alertas | Datadog: free tier só cobre infraestrutura (sem APM/logs); APM só no trial de 14 dias |
| Contrato entre repositórios | **SSM Parameter Store** (parâmetros standard, gratuitos) | Desacopla os repos sem dar acesso ao state alheio | `terraform_remote_state`: exporia o state inteiro (que contém a senha do RDS) a todos os repos |
| Comunicação | Síncrona REST | Domínio pequeno, sem necessidade de desacoplamento temporal | Mensageria (SQS/SNS): complexidade e custo sem requisito que a justifique |
| Ambientes | **Workspaces `hml` e `prod` como stacks separadas**, com `hml` subindo sob demanda | Só uma stack separada ensaia mudanças de Terraform (SG, `user_data`, parameter group) antes de produção; custo da separação é US$0,18 por sessão de 4h | Namespace no mesmo cluster: colide no Traefik (Ingress sem `host`), disputa o node único e deixa as branches `homolog` dos repos de infra sem `apply` |
| Autorização de borda | **Lambda authorizer no gateway + RBAC na aplicação** | É o que torna "proteger rotas sensíveis" verdadeiro no perímetro; tráfego sem token válido não chega ao cluster | Só a aplicação validar: o gateway viraria proxy puro e o requisito ficaria atendido apenas na letra |
| Visibilidade dos repositórios | **Públicos, em organização GitHub gratuita** | No plano Free, rulesets, environments com revisor e organization secrets **só existem em repositório público** — e minutos de Actions são ilimitados | Repositórios privados: R5 exigiria plano pago e os 4 repos dividiriam 2.000 min/mês |

### Decisões fechadas em 2026-09-09

As quatro decisões que estavam em aberto foram resolvidas com o usuário e já estão refletidas nas specs:

1. **Homologação:** stacks separadas por workspace, com `hml` subindo **apenas quando há mudança de
   infraestrutura para validar** — a pipeline é automática, o controle é o merge na branch `homolog`.
   Ver [06](06-infra-k8s-terraform.md) §4.
2. **Linguagem do Lambda:** **Java 21 + SnapStart**, ZIP com Maven Shade. Ver [02](02-auth-cpf-lambda.md) §3.
3. **Autorização:** Lambda authorizer no gateway, **fora da VPC** (só precisa da chave pública — sem
   ENI, sem cold start de VPC, sem contato com o banco), somado ao RBAC e à checagem de posse na
   aplicação. Ver [03](03-api-gateway.md) §4.
4. **Repositórios:** públicos, sob organização GitHub gratuita. Pré-condição bloqueante: limpar
   credenciais do histórico no split. Ver [08](08-repositorios-cicd.md) §1 e §5.

## 4. Ordem de execução

Dependências reais, não preferência. Etapas na mesma linha podem ser paralelizadas.

```
E0  Split dos repositórios + proteção de branch + pipelines vazias .... spec 08
     │
E1  Modelagem do banco (V5) — clients.status é pré-requisito do Lambda  spec 01
     │
     ├── E2  Infra K8s (Terraform, repo 2) ......................... spec 06
     │        │
     │        └── E3  Infra DB (Terraform, repo 3) ................. spec 07
     │                 │
E4  App: role CLIENT, ownership, correlação, métricas de negócio ... spec 04
     │                 │
     └─────────────────┴── E5  Lambda de autenticação por CPF ....... spec 02
                                │
                           E6  API Gateway + authorizer ............. spec 03
                                │
                           E7  Observabilidade fim-a-fim ............ spec 05
                                │
                           E8  Documentação arquitetural ............ spec 09
                                │
                           E9  Runbook de sessão e custos ........... spec 10
```

`E1` e `E4` mexem no mesmo repositório (aplicação) e podem ser feitas na mesma branch, mas são specs
separadas porque têm critérios de aceite independentes.

## 5. Orçamento

Crédito disponível: **~US$48, único para o curso, não recarrega**.

Custo por sessão de 4 horas com a stack de produção inteira de pé (us-east-1, preços de referência):

| Recurso | Preço | 4h |
|---|---|---|
| EC2 `t3.medium` (k3s) | US$0,0416/h | US$0,166 |
| RDS `db.t4g.micro` | US$0,016/h | US$0,064 |
| Storage RDS gp3 20 GB | US$0,115/GB-mês | US$0,013 |
| IPv4 público / EIP | US$0,005/h | US$0,020 |
| API Gateway HTTP API | US$1,00/milhão req | < US$0,01 |
| Lambda (invocações da demo) | US$0,20/milhão + GB-s | < US$0,01 |
| ECR + S3 (state) + CloudWatch Logs | — | ~US$0,03 |
| New Relic | free tier 100 GB/mês | US$0,00 |
| **Total** | | **≈ US$0,31** |

A stack de homologação, quando sobe, acrescenta ≈ US$0,18 (t3.small + RDS + IPv4 por 4h) — mas ela
**não sobe em toda sessão**: só quando há mudança de infraestrutura para ensaiar antes de produção.
O orçamento suporta ~90 sessões completas — **desde que a stack seja destruída ao final de cada uma**.

O risco real não é o custo por hora, é o esquecimento: o "End Lab" do Academy **para apenas instâncias
EC2**. RDS, EIP e API Gateway continuam existindo e cobrando. Um RDS esquecido custa US$13,70/mês e
consome o orçamento inteiro em ~3,5 meses de inatividade total. Por isso `terraform destroy` é item de
checklist obrigatório — ver [10](10-runbook-sessao-custos.md).

## 6. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Conta Academy bloquear `apigateway:*` | R1/R6 inviáveis como projetados | Validar com um `terraform plan` mínimo na primeira sessão (spec 03 §8). Fallback: Traefik como gateway + Lambda Function URL |
| `LabRole` sem trust policy para `lambda.amazonaws.com` ou sem permissão de ENI na VPC | Lambda não sobe | Validar `aws iam get-role --role-name LabRole` na primeira sessão. Fallback: Lambda fora da VPC consultando a API da aplicação |
| Footprint do agente New Relic no node único | Pod da aplicação em `Pending` no meio da demo do HPA | Medir com `kubectl top nodes` após instalar; teto do HPA para 3 ou EC2 `t3.large` (+US$0,17/sessão) |
| Cold start do Lambda em VPC na gravação do vídeo | Percepção ruim de performance | SnapStart + um *warm-up* manual antes da demo |
| Split de 4 repositórios multiplica o tempo de bootstrap por sessão | Sessão de 4h insuficiente | Runbook com ordem fixa e workflows `workflow_dispatch` encadeáveis (spec 10) |
| Material de credencial sobrevivendo ao split para repositório **público** (`infra/k3s-key.pem`, `kubeconfig`, `terraform.tfstate`) | Incidente de segurança na publicação | Remoção no próprio `git filter-repo` + varredura do histórico antes de tornar público — gate bloqueante da spec 08 |
| Limite de conexões do `db.t4g.micro` (~112) vs 4 pods × pool 20 + Lambda | Erro de conexão sob carga, justamente na demo do HPA | Reduzir pool para 8 por pod (spec 01 §6) |

## 7. Fora de escopo

- Documento PDF de entrega e vídeo demonstrativo (responsabilidade do usuário).
- `oficina-front/` — não faz parte desta fase.
- Mensageria, CQRS, event sourcing, service mesh, multi-região, Multi-AZ do RDS: nenhum requisito
  funcional ou não-funcional desta fase os justifica, e todos têm custo incompatível com o orçamento.
- Migração para EKS ou para outro provedor de nuvem.
