## 6. Terraform (`/infra`) — AWS (AWS Academy Learner Lab)

### Dependências

Nenhuma técnica (é a base de infraestrutura). Possui um **gate de processo que precede qualquer outra
dependência**: ver seção "Gate de Aprovação" abaixo. `spec-kubernetes.md` e `spec-github-actions.md` dependem
desta spec estar aplicada (EC2 com k3s, ECR e RDS existindo) antes de fazer sentido executá-las contra AWS
real.

### Skill de Apoio

`aws-cost-operations` — usar para revalidar o preço vigente de EC2, RDS e ECR antes de qualquer `apply` real.
Os preços neste documento foram levantados em **2026-07-14**; se a sessão de `apply` ocorrer muito depois
disso, revalidar antes de prosseguir — preço e disponibilidade de tipo de instância mudam com frequência
maior que o ciclo de atualização de conhecimento estático.

### Contexto — restrições da conta AWS Academy Learner Lab

Esta não é uma conta AWS comum, e o desenho de infraestrutura abaixo existe **por causa** dessas restrições,
não apesar delas. Validado via pesquisa em 2026-07-14:

- **Região fixa: `us-east-1`.** Não há variável de região neste módulo — é hardcoded de propósito, para não
  criar a ilusão de portabilidade que a conta não suporta.
- **IAM bloqueado para criação.** A conta não permite `iam:CreateRole`, `iam:CreatePolicy` nem
  `iam:CreateUser` — só é permitido **anexar policies a roles já existentes**. O Academy provisiona duas
  identidades prontas: `LabRole` (role genérica, assumível por serviços) e `LabInstanceProfile` (instance
  profile para EC2, encapsula a `LabRole`). Todo recurso Terraform que precisaria de um IAM role novo
  referencia essas duas via `data source` — em nenhum lugar deste módulo existe `resource "aws_iam_role"` ou
  `resource "aws_iam_policy"`.
- **Orçamento é único para o curso inteiro, não mensal.** US$50 é o total — não recarrega. Se esgotar, a
  conta é desativada. Isso muda o cálculo de risco: não é "quanto gasto por mês", é "quanto sobra para o
  resto do curso".
- **"End Lab" só para instâncias EC2 — não para os demais recursos.** Ao encerrar a sessão (botão "End Lab")
  ou ao expirar o timer de 4h, a AWS Academy para automaticamente instâncias EC2 em execução. **RDS, NAT
  Gateway e qualquer control plane gerenciado (ex.: EKS) continuam rodando e cobrando mesmo com a sessão
  encerrada** — não há parada automática para eles. Essa é a razão central por trás de duas decisões deste
  documento: (a) evitar ao máximo recursos não-EC2 que ficam "sempre ligados" (daí a rejeição de EKS e de NAT
  Gateway abaixo), e (b) tratar `terraform destroy` ao final de cada sessão como **obrigatório**, não como
  boa prática — é a única proteção real contra cobrança residual.
- **Credenciais são temporárias (STS) e renovadas a cada "Start Lab".** Não é possível criar um Identity
  Provider OIDC para GitHub Actions (é criação de recurso IAM, bloqueada). Isso é tratado em
  `spec-github-actions.md`, não aqui, mas o motivo raiz é este.

### Decisão de arquitetura — k3s self-managed em EC2 única (não EKS)

Decisão fechada com o usuário (ver `ready-to-go.md`), com base no contexto acima:

- **Kubernetes:** k3s single-node em 1 EC2, não Amazon EKS.
- **Motivo financeiro:** o control plane do EKS cobra **US\$0,10/hora fixo, independente de uso**, e — pela
  restrição de "End Lab" descrita acima — **não para quando a sessão do Academy termina**. Um `terraform
  destroy` esquecido ou que falhe parcialmente pode consumir o orçamento único de US\$50 em cerca de 20 dias
  de EKS rodando sozinho, sem contar os demais recursos. A EC2 do k3s, em contraste, **é parada
  automaticamente pelo "End Lab"** — uma rede de segurança que o EKS não tem.
- **Cobertura do requisito do desafio:** o PDF (`14SOAT - Fase 2 - Tech challenge-1.pdf`) pede Terraform
  "para provisionamento do cluster Kubernetes (**local ou cloud**)" — não exige um serviço gerenciado. k3s é
  uma distribuição Kubernetes conformante (CNCF-certified), com HPA funcional via `metrics-server` (incluso
  por padrão), o que satisfaz integralmente o requisito de "escalabilidade dinâmica" e o vídeo demonstrativo.
- **Trade-off aceito conscientemente:** sem alta disponibilidade de control plane — se a EC2 cair, o cluster
  inteiro cai junto (single point of failure no nível do nó). Isso é uma extensão proporcional do trade-off
  que a spec já assumia mesmo com EKS ("cluster temporário, custo mínimo, sem SLA de produção") — não é uma
  concessão nova, é a mesma lógica levada ao componente que ainda restava caro.

### Contrato — decisões de recurso

| Recurso            | Escolha                                                                                                                                        | Justificativa                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Cluster K8s        | k3s single-node em 1 EC2 `t3.medium` (2 vCPU / 4GB), Amazon Linux 2023, `LabInstanceProfile`                                                   | Sem taxa fixa de control plane. `t3.medium` dá margem para 2-4 réplicas do app (250m/384Mi cada) + addons do k3s (CoreDNS, metrics-server, Traefik, local-path-provisioner) sem pressão de memória.                                                                                                                                                                                                                                 |
| Rede               | VPC dedicada, 1 subnet pública (EC2) + 2 subnets privadas em AZs distintas (exigência de DB Subnet Group do RDS), **sem NAT Gateway**          | RDS não precisa de saída à internet — só recebe conexão da EC2 via rede interna da VPC. NAT Gateway é o item de custo mais silencioso (US\$0,045/h + US\$0,045/GB) e não tem função aqui.                                                                                                                                                                                                                                           |
| Banco de dados     | RDS PostgreSQL `db.t4g.micro`, single-AZ, storage `gp3` 20GB                                                                                   | Atende o requisito de recurso gerenciado real via IaC; single-AZ é aceitável (ambiente descartável, não produtivo), evita Postgres em PVC dentro do cluster.                                                                                                                                                                                                                                                                        |
| Registro de imagem | ECR privado (1 repositório)                                                                                                                    | Autenticação via instance profile da EC2 (`LabInstanceProfile`) — token de 12h obtido com `aws ecr get-login-password`, sem credenciais estáticas.                                                                                                                                                                                                                                                                                  |
| IAM                | Nenhum role/policy novo — `data "aws_iam_role"` e `data "aws_iam_instance_profile"` referenciando `LabRole`/`LabInstanceProfile` já existentes | A conta bloqueia criação de IAM; só permite anexar/usar o que já existe.                                                                                                                                                                                                                                                                                                                                                            |
| Terraform state    | Remoto em S3 (bucket dedicado, sem DynamoDB)                                                                                                   | **Não** é sobre tamanho de equipe — é porque a pipeline de CI/CD (`spec-github-actions.md`) roda em runner efêmero: sem state compartilhado, um `apply` disparado pelo GitHub Actions não veria os recursos já criados localmente e tentaria recriá-los. S3 puro (sem lock do DynamoDB) é suficiente porque apply concorrente é improvável num projeto solo/pequeno grupo — se isso mudar, adicionar DynamoDB é a evolução natural. |

### Estrutura de arquivos

```
infra/
  providers.tf       # provider aws, região fixa us-east-1
  backend.tf           # backend "s3" — bucket criado uma única vez fora deste módulo (ver seção de bootstrap)
  data.tf                # data.aws_iam_role.lab_role, data.aws_iam_instance_profile.lab_instance_profile,
                          # data.aws_ssm_parameter (AMI Amazon Linux 2023 mais recente, evita hardcode)
  variables.tf            # allowed_cidr (IP do aluno p/ SSH e API k3s), db_password (sensitive), project_name
  vpc.tf                    # VPC, 1 subnet pública + 2 privadas, IGW, route tables (sem NAT)
  security_groups.tf         # sg_k3s (22 e 6443 restritos a var.allowed_cidr), sg_rds (5432 só do sg_k3s)
  ec2.tf                       # aws_instance (t3.medium, LabInstanceProfile) + aws_eip + aws_key_pair (SSH,
                                # gerado via provider tls) + user_data
  user_data.sh.tpl               # bootstrap: instala k3s + aws-cli, prepara kubeconfig
  rds.tf                           # aws_db_subnet_group + aws_db_instance
  ecr.tf                             # aws_ecr_repository
  outputs.tf                          # k3s_public_ip, rds_endpoint, ecr_repository_url, ssh_private_key
                                        # (sensitive; senha do RDS nunca em output)
  README.md                            # recursos provisionados, como aplicar/destruir, custo estimado
```

### Bootstrap do backend remoto — execução única, fora do ciclo apply/destroy

O bucket S3 que guarda o state **não** é criado pelo próprio módulo `/infra` (problema clássico de
ovo-e-galinha: `terraform init` precisa do bucket antes de existir qualquer state) e **não** é destruído nos
`terraform destroy` de cada sessão — ele persiste pelo curso inteiro, junto com a conta Academy (que também é
persistente entre sessões, não é recriada a cada "Start Lab"). Criar uma única vez:

```bash
aws s3 mb s3://oficina-tfstate-<sufixo-unico> --region us-east-1
aws s3api put-bucket-versioning --bucket oficina-tfstate-<sufixo-unico> --versioning-configuration Status=Enabled
```

Referenciar o nome escolhido em `backend.tf`. Custo: poucos KB de arquivo de state, bem abaixo do free tier —
desprezível mesmo somado ao longo do curso todo.

**Esboço de `user_data.sh.tpl`** (bootstrap da EC2, referência para implementação):

```bash
#!/bin/bash
set -euxo pipefail

dnf install -y aws-cli

# --tls-san: sem isso, o certificado autoassinado do k3s só cobre os IPs conhecidos no momento da
# instalação (IP privado, 127.0.0.1) — o Elastic IP é associado por recurso Terraform separado, então
# fica de fora do certificado e todo acesso externo via kubectl falha com erro de TLS.
curl -sfL https://get.k3s.io | sh -s - --write-kubeconfig-mode 644 --tls-san ${k3s_public_ip}

mkdir -p /home/ec2-user/.kube
cp /etc/rancher/k3s/k3s.yaml /home/ec2-user/.kube/config
sed -i "s/127.0.0.1/${k3s_public_ip}/" /home/ec2-user/.kube/config
chown -R ec2-user:ec2-user /home/ec2-user/.kube
```

> `$${k3s_public_ip}` é injetado via `templatefile()` a partir do `aws_eip` criado em `ec2.tf` — sem
> dependência circular, já que o EIP é alocado independentemente da instância (`aws_eip.k3s.public_ip` existe
> antes do `aws_instance` subir; só a associação do EIP à instância acontece depois, via
> `aws_eip_association`). `dnf install aws-cli` é redundante em algumas AMIs Amazon Linux 2023 (já vem
> pré-instalado), mas é mantido explícito no bootstrap para não depender de uma suposição que pode mudar entre
> variantes da AMI.

### Acesso SSH (necessário para o job `deploy-k8s` buscar o kubeconfig)

O par de chaves SSH é gerado pelo próprio Terraform (`resource "tls_private_key"` + `resource
"aws_key_pair"`), não importado de uma chave que o aluno já teria — evita a precondição "já ter uma chave SSH
configurada localmente" em cada sessão nova. A chave privada sai como `output` sensível (`terraform output
-raw ssh_private_key > k3s-key.pem && chmod 600 k3s-key.pem`) e é usada tanto para depuração manual quanto
para o job `deploy-k8s` da pipeline buscar `/home/ec2-user/.kube/config` via `scp` (`spec-github-actions.md`).
Como a EC2 é recriada a cada sessão (destruída no `terraform destroy`), a chave também é — não há problema de
rotação, ela simplesmente não existe mais depois do destroy.

### Autenticação do k3s no ECR (sem credencial estática)

O k3s usa `containerd` internamente. Em vez de configurar autenticação de registry no nível do nó (mais
frágil de depurar dentro de uma sessão de 4h), a autenticação ao ECR acontece no nível do Kubernetes: um
`Secret` do tipo `kubernetes.io/dockerconfigjson`, gerado a partir do token do `aws ecr get-login-password`
(válido por 12h, usando a permissão já presente na `LabRole` via `LabInstanceProfile` da EC2) e referenciado
via `imagePullSecrets` no `deployment.yaml` (`spec-kubernetes.md`). Precisa ser regenerado se a sessão passar
de 12h contínuas — no fluxo normal (sessão de poucas horas, destruído ao final), isso não chega a ser um
problema prático.

### Gate de Aprovação — não-negociável

Esta etapa **não pode ser executada** (nenhum `terraform apply` real contra AWS) sem que, antes:

1. Seja apresentada estimativa de custo por recurso (ver tabela abaixo) com base na documentação oficial de
   preços vigente — cumprido neste documento com preços de 2026-07-14; revalidar via skill `aws-cost-operations`
   se a sessão de apply ocorrer muito depois desta data.
2. O total estimado seja comparado explicitamente contra o crédito disponível (**US\$50, único para todo o
   curso, não mensal** — ver seção de restrições da conta acima).
3. Se qualquer recurso ultrapassar o orçamento, alternativas de menor custo sejam apresentadas — já
   incorporadas nesta escolha de arquitetura (k3s em vez de EKS, sem NAT Gateway, `t4g.micro` no RDS); se
   ainda assim for necessário cortar mais, o próximo corte é `t3.small` no lugar de `t3.medium` (aceitando
   menos margem para réplicas simultâneas do HPA).
4. Aprovação explícita seja dada para o plano de custo, separadamente da aprovação do código Terraform em si.

`terraform plan` pode ser revisado e discutido livremente (não tem custo). `terraform apply` é o ato que
requer o gate acima cumprido.

### Estimativa de custo (validada em 2026-07-14, região `us-east-1`)

| Recurso                      | Preço unitário                                | Observação                                                               |
|------------------------------|-----------------------------------------------|--------------------------------------------------------------------------|
| EC2 `t3.medium`              | US\$0,0416/hora                               | On-demand, Linux                                                         |
| EBS `gp3` 20GB (root da EC2) | US\$0,08/GB-mês (≈US\$0,0022/h)               | Prorateado por hora                                                      |
| RDS `db.t4g.micro`           | US\$0,016/hora                                | Instância; + storage `gp3` 20GB ≈ US\$0,023/GB-mês (≈US\$0,0003/h)       |
| ECR (storage)                | US\$0,10/GB-mês                               | Uma imagem JVM Quarkus (~200-300MB) → desprezível (<US\$0,03/mês)        |
| Elastic IP                   | Grátis enquanto associado a instância rodando | Cobra só se ficar órfão — não ocorre aqui, pois é destruído junto da EC2 |
| NAT Gateway                  | Não utilizado                                 | US\$0 — decisão de arquitetura                                           |
| S3 (state remoto)            | US\$0,023/GB-mês                              | Arquivo de state de poucos KB → desprezível (<US\$0,01/mês)              |

**Combinado (EC2 + RDS + storage): ≈ US\$0,061/hora ≈ US\$1,46/dia se esquecido rodando 24h.**

- Se esquecido rodando **30 dias corridos seguidos**: ≈ US\$44 — quase todo o orçamento único de US\$50. Esse
  é o cenário que o comando de destroy (seção seguinte) existe para prevenir.
- Uso realista (sessões de 2-3h, destruindo ao final de cada uma): ≈ US\$0,15–0,20 por sessão. Mesmo 20-30
  sessões ao longo do semestre somam US\$3-6, com folga confortável dentro dos US\$50 para o resto do curso.

### Comando de destroy — obrigatório ao final de cada sessão

```bash
cd infra
terraform destroy -auto-approve
```

`terraform destroy` só remove o que está no state. Rodar sempre em seguida a verificação de órfãos (cobre o
cenário de um `apply` parcial ou um recurso criado manualmente fora do Terraform durante a sessão):

```bash
aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output table

aws rds describe-db-instances \
  --query 'DBInstances[?contains(DBInstanceIdentifier,`oficina`)].[DBInstanceIdentifier,DBInstanceStatus]' \
  --output table

aws ec2 describe-addresses \
  --query 'Addresses[?AssociationId==`null`].[PublicIp,AllocationId]' --output table
```

Se qualquer um dos três comandos retornar linhas, o `destroy` não completou de ponta a ponta — investigar e
remover manualmente antes de encerrar a sessão do Academy (`End Lab`), já que — como descrito na seção de
restrições — RDS órfão continua cobrando mesmo depois da sessão terminar.

### Critério de Aceite

- `terraform plan` executa sem erro e o diff é revisado manualmente antes de qualquer `apply`.
- Após `apply`: `aws ec2 describe-instances`, `aws rds describe-db-instances`, `aws ecr describe-repositories`
  confirmam os três recursos no estado esperado (EC2 `running` com k3s respondendo em `kubectl get nodes`,
  RDS `available`, repositório ECR criado).
- `outputs.tf` expõe `k3s_public_ip`, `rds_endpoint`, `ecr_repository_url` e `ssh_private_key` (marcado
  `sensitive = true`) — a senha do RDS nunca aparece em output, mesmo mascarada.
- Inspeção do plano confirma **zero** recursos `aws_iam_role`/`aws_iam_policy`/`aws_iam_user` — só
  `data source` referenciando `LabRole`/`LabInstanceProfile`.
- Inspeção do `vpc.tf`/console confirma que não existe NAT Gateway provisionado.
- Security group da EC2 restringe as portas 22 e 6443 a `var.allowed_cidr` — nunca `0.0.0.0/0`.
- `terraform init` usa o backend S3 do bootstrap (não há `terraform.tfstate` local commitado nem `.gitignore`
  precisando escondê-lo — o state nunca chega a existir no disco do repositório).
- `terraform destroy` executa de ponta a ponta sem deixar recurso órfão (validado pelos três comandos `aws
  cli` da seção anterior).

### Fora de Escopo

- Amazon EKS ou qualquer control plane Kubernetes gerenciado — decisão explícita, ver seção "Decisão de
  arquitetura" acima.
- Alta disponibilidade de control plane / multi-node — trade-off aceito conscientemente para este contexto
  acadêmico descartável.
- Lock de state via DynamoDB — apply concorrente é improvável num projeto solo/pequeno grupo; adicionar se
  isso mudar. O bucket S3 em si **não** está fora de escopo — é necessário pela statefulness exigida pela
  pipeline de CI/CD (ver tabela de decisões de recurso e seção de bootstrap).
- Qualquer recurso de produção real (multi-AZ, redundância, SLA) — fora do propósito acadêmico desta fase.
- Manifestos Kubernetes em si (aplicados depois, sobre o cluster aqui criado) — isso é `spec-kubernetes.md`.
