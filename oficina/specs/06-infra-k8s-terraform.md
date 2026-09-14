# E2 — Infraestrutura Kubernetes (Terraform)

> Repositório **`oficina-infra-k8s`** (repo 2 de 4). Atende os itens "Cluster Kubernetes com
> escalabilidade" e "Terraform para provisionamento" de R6, e a rede que todos os outros repositórios
> consomem.

## 1. Dependências

Nenhuma técnica — é a base. Depende de [08](08-repositorios-cicd.md) apenas para o repositório existir.
**É o primeiro `terraform apply` de toda sessão**, porque publica a VPC que os demais consomem.

## 2. Escopo do repositório

Herda o conteúdo atual de `oficina/infra`, **menos** o RDS (que vai para
[07](07-infra-db-terraform.md)):

```
vpc.tf              VPC, IGW, subnet pública, 2 subnets privadas, route table
security_groups.tf  SG do k3s (SSH /32 do aluno, HTTP 80 das faixas do API Gateway)
ec2.tf              key pair, EIP, instância com k3s via user_data, IMDSv2, EBS criptografado
ecr.tf              repositório de imagem da aplicação
data.tf             LabInstanceProfile, AMI AL2023 via SSM, AZs
ssm_outputs.tf      publica o contrato para os outros repositórios  (novo)
observability.tf    helm_release do nri-bundle                       (novo)
backend.tf          state S3, key = oficina/infra-k8s/<workspace>.tfstate
```

## 3. Contrato publicado (SSM Parameter Store)

Parâmetros *standard* são **gratuitos** (até 10.000) e legíveis por qualquer identidade da conta — é o
que permite aos outros três repositórios descobrirem os endereços sem acessar o state alheio.

| Parâmetro | Consumidor |
|---|---|
| `/oficina/<env>/vpc_id` | infra-database, auth-lambda |
| `/oficina/<env>/private_subnet_ids` | infra-database (subnet group), auth-lambda (VPC config) |
| `/oficina/<env>/k3s_security_group_id` | infra-database (regra de ingresso 5432) |
| `/oficina/<env>/k3s_eip` | auth-lambda (integração HTTP do gateway) |
| `/oficina/<env>/ecr_repository_url` | oficina-app (build e deploy) |
| `/oficina/<env>/application_url` | oficina-app (smoke test), documentação |

**Por que não `terraform_remote_state`:** ele exige acesso de leitura ao bucket de state, e o state de
`infra-database` contém a **senha do RDS em texto**. Dar acesso ao state para publicar um endpoint é
trocar um contrato de 6 strings por um vazamento de credencial. SSM entrega exatamente o necessário.

## 4. Ambientes

**Decidido: workspaces Terraform `hml` e `prod`, stacks fisicamente separadas.**

| | `hml` | `prod` |
|---|---|---|
| EC2 | `t3.small` (2 GiB) | `t3.medium` (4 GiB) |
| HPA | 1→2 réplicas | 2→4 réplicas |
| Volume | 20 GiB gp3 | 20 GiB gp3 |
| Agentes New Relic | `lowDataMode`, sem prometheus-agent | completo |
| Custo/hora | US$0,0208 + IPv4 | US$0,0416 + IPv4 |

Branch `homolog` → `terraform apply` no workspace `hml`; branch `main` → workspace `prod`. Atende
literalmente "deploy automático das branches de homologação e produção".

### 4.1 `hml` sobe sob demanda, não em toda sessão

A pipeline é automática; o controle de custo é **quando você faz merge em `homolog`**. Regra:
homologação sobe quando há **mudança de infraestrutura ou de schema para ensaiar** antes de produção —
security group, `user_data` do k3s, parameter group do RDS, versão do Terraform, migration nova. Para
mudança que só toca código da aplicação, o ciclo local (`RUN-LOCAL.md`) já cobre, e `hml` fica parada.

Custo quando sobe: t3.small + RDS + IPv4 ≈ US$0,045/h → **US$0,18 numa sessão de 4h**. Não é o custo
que limita a frequência; é o tempo de bootstrap (+~8 min, dominados pela criação do RDS) dentro de uma
janela de 4 horas.

### 4.2 Por que não namespace no mesmo cluster

A alternativa barata foi avaliada e rejeitada por quatro motivos concretos, não por preferência:

1. **Não ensaia infraestrutura** — que é a única razão de existir homologação neste projeto. Namespace
   não valida mudança de SG, de `user_data`, de parameter group nem de versão do k3s: essas iriam
   direto para produção sem ensaio, e são a classe de erro mais cara.
2. **Colisão de roteamento.** O `ingress.yaml` não define `host:` (casa com qualquer Host header). Dois
   Ingress com path `/` no mesmo Traefik disputam a rota; contornar exigiria host distinto
   (`hml.<eip>.nip.io`), mudando URL e coleção Postman.
3. **Disputa do node único.** Com o `nri-bundle` instalado sobram ~1,2 GiB no `t3.medium`. Hospedar
   `hml` junto obrigaria a reduzir o `maxReplicas` de produção — degradando exatamente a demonstração
   de escalabilidade que o vídeo precisa mostrar.
4. **Mesmo RDS, mesmo teto de conexões e mesmos créditos de CPU.** Um teste de carga em homologação
   derrubaria o p95 de produção. E `kubectl get secrets -A`, num cluster single-node com kubeconfig
   admin único, enxergaria os segredos dos dois ambientes.

Decisão e alternativa rejeitada ficam registradas no ADR 0015.

### 4.3 Nota de capacidade

`t3.small` com 2 GiB **não** comporta o `nri-bundle` completo junto de 2 réplicas da aplicação; por
isso a coluna de agentes em `hml` é reduzida. Se homologação precisar da observabilidade completa para
validar dashboards antes de produção, `hml` também vira `t3.medium` naquela sessão específica
(variável por workspace, não mudança de código).

## 5. Segurança (mantida da Fase 2 + ajuste desta fase)

Já implementado e que **não deve regredir** no split: IMDSv2 obrigatório com `hop_limit=1`, EBS
criptografado, porta 6443 nunca exposta (acesso por túnel SSM), `default_tags` para rastrear órfãos,
validação que proíbe `0.0.0.0/0` em `allowed_cidr`.

Novo nesta fase: a porta 80 deixa de ser exclusiva do `/32` do aluno e passa a aceitar também as faixas
do API Gateway (`data "aws_ip_ranges"`), conforme [03](03-api-gateway.md) §5. A validação que proíbe
`0.0.0.0/0` continua valendo — as faixas vêm de um data source, não de variável.

## 6. Pipeline

| Job | Gatilho | O que faz |
|---|---|---|
| `validate` | todo push e PR | `terraform fmt -check`, `validate`, `tflint`, `trivy config .` (IaC scan) |
| `plan` | PR | `terraform plan` e comentário do plano no PR — é o gate de revisão humana antes de gastar |
| `apply` | push em `homolog` / `main` | `apply` no workspace correspondente, publica os parâmetros SSM, imprime o resumo com custo estimado |
| `destroy` | `workflow_dispatch` com confirmação digitada | `terraform destroy` + varredura de órfãos por tag |

O job `destroy` manual em cada repositório de infraestrutura é item de **controle de custo**, não
conveniência: é o que torna o encerramento de sessão um clique em vez de um ritual de terminal.

## 7. Critério de aceite

- `terraform apply` no workspace `prod` cria a stack e os 6 parâmetros SSM.
- `kubectl get nodes` via túnel SSM responde `Ready`.
- `terraform destroy` remove tudo; `aws resourcegroupstaggingapi get-resources --tag-filters Key=Project,Values=oficina`
  volta vazio.
- `tflint` e `trivy config` sem achado de severidade alta.
- README do repositório com diagrama da infraestrutura, pré-requisitos, comandos e a tabela de custo.

## 8. Fora de escopo

- EKS, node group, Cluster Autoscaler, Multi-AZ do cluster: justificativa de custo em
  [00](00-visao-geral.md) §3.
- NAT Gateway e VPC endpoints (US$32 e US$7,20/mês respectivamente).
- Bootstrap do bucket de state (feito uma vez, manualmente, fora do módulo — como já é hoje).
