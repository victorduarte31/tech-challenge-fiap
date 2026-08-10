# Infraestrutura (Terraform) — Oficina Mecânica (AWS Academy)

## Recursos provisionados

| Recurso            | Nome               | Tipo / observação                                                        |
|--------------------|--------------------|---------------------------------------------------------------------------|
| VPC                | `oficina-vpc`      | 1 subnet pública + 2 privadas, sem NAT Gateway                            |
| Security groups    | `oficina-k3s-sg`, `oficina-rds-sg` | k3s: SSH (22) e HTTP (80) restritos ao `allowed_cidr`; RDS: 5432 só do SG do k3s |
| Cluster K8s        | `oficina-k3s`      | k3s single-node, EC2 `t3.medium`, disco gp3 **criptografado**, IMDSv2 obrigatório |
| Banco de dados     | `oficina-postgres` | RDS PostgreSQL `db.t4g.micro`, single-AZ, **`storage_encrypted`**         |
| Registro de imagem | `oficina-app`      | ECR privado (`force_delete` para o destroy não travar com imagem dentro)  |
| Chave SSH          | `oficina-k3s-key`  | Gerada pelo próprio Terraform (`tls_private_key`)                         |
| Elastic IP         | `oficina-k3s-eip`  | IP estável para o kubeconfig e para o `--tls-san` do k3s                  |

Todos os recursos recebem `Project=oficina`, `ManagedBy=terraform` e `Environment=lab` via
`default_tags` no provider — é o que faz os comandos de conferência de recursos órfãos (mais abaixo)
encontrarem tudo.

Justificativa completa das escolhas (por que k3s e não EKS, por que sem NAT, por que state em S3) em
[`../docs/spec-terraform-aws.md`](../docs/spec-terraform-aws.md). O trade-off de alta disponibilidade
está declarado no [README principal](../README.md#alta-disponibilidade--o-que-esta-entrega-faz-e-o-que-não-faz).

## Decisões de segurança (e o que é valor de laboratório)

| Item                              | Valor       | Por quê                                                                              |
|-----------------------------------|-------------|---------------------------------------------------------------------------------------|
| `storage_encrypted` (RDS)         | `true`      | O banco guarda CPF/CNPJ, e-mail e telefone. Custo zero com a chave padrão do serviço  |
| `encrypted` (EBS root)            | `true`      | O disco do node contém o etcd do k3s — ou seja, todos os Secrets do cluster           |
| `http_tokens = "required"` (IMDSv2)| `required` | Sem isso, uma SSRF na aplicação lê as credenciais do instance profile com um GET      |
| `use_lockfile` (backend S3)       | `true`      | Trava de state nativa: impede `apply` concorrente da pipeline e da máquina local      |
| `multi_az`                        | `false`     | **Laboratório.** Em produção `true` — dobra o custo do banco                          |
| `backup_retention_period`         | `0`         | **Laboratório.** A infraestrutura é destruída a cada sessão. Em produção 7–35 dias     |
| `deletion_protection`             | `false`     | **Laboratório.** Precisa permitir `terraform destroy`. Em produção `true`              |

A porta **6443 (API do Kubernetes) não é exposta em nenhum momento** — nem para o seu IP. O acesso
administrativo, local e da pipeline, passa por túnel do SSM Session Manager.

## Pré-requisitos

- Terraform **>= 1.10** (o backend usa `use_lockfile`, introduzido nessa versão)
- AWS CLI configurado com as credenciais temporárias da sessão AWS Academy ("AWS Details" no painel do
  Academy → `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN`, como variáveis de ambiente
  ou em `~/.aws/credentials`) — expiram com a sessão, precisam ser renovadas a cada "Start Lab"
- `TF_VAR_db_password` definida, com **no mínimo 16 caracteres** (nunca commitar em `.tfvars`)
- `TF_VAR_allowed_cidr` com o seu IP público no formato `x.x.x.x/32`. `0.0.0.0/0` é rejeitado por
  validação — abriria SSH e a aplicação para a internet inteira

## Bootstrap do backend remoto — execução única, antes do primeiro `terraform init`

Nomes de bucket S3 são únicos globalmente (todas as contas AWS do mundo) — escolha um sufixo que não
colida, ex. seu nome ou RM:

```bash
aws s3 mb s3://victor-duarte-mendonca-oficina-tfstate --region us-east-1
aws s3api put-bucket-versioning --bucket victor-duarte-mendonca-oficina-tfstate \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket victor-duarte-mendonca-oficina-tfstate \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-public-access-block --bucket victor-duarte-mendonca-oficina-tfstate \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

Versionamento e criptografia não são opcionais: o state contém a senha do RDS e a chave privada da EC2
em texto claro. Depois, edite `backend.tf` e troque o `bucket` pelo nome escolhido. Esse bucket **não** é
destruído pelo `terraform destroy` de cada sessão — persiste pelo curso inteiro.

## Como aplicar

```bash
cd infra
terraform init
terraform fmt -check -recursive   # o mesmo gate que a pipeline aplica
terraform validate
terraform plan -out=tfplan
# Revisar o plano manualmente antes de prosseguir — gate de custo cumprido antes deste passo
terraform apply tfplan
```

Ao final:

```bash
terraform output application_url      # URL pública da API (porta 80, via Ingress/Traefik)
terraform output rds_endpoint         # DB_HOST do Secret da aplicação
terraform output ecr_repository_url   # imagem do Deployment
```

> Na pipeline esses três valores são lidos automaticamente como outputs do job `terraform-apply` e
> passados ao deploy — não existem mais como GitHub Secrets atualizados à mão.

## Como destruir — obrigatório ao final de CADA sessão, não só ao final do projeto

```bash
cd infra
terraform destroy -auto-approve
```

Depois, confirmar ausência de recursos órfãos:

```bash
aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=pending,running,stopping,stopped" --query 'Reservations[].Instances[].[InstanceId,State.name]' --output table
aws rds describe-db-instances --query 'DBInstances[?contains(DBInstanceIdentifier,`oficina`)].[DBInstanceIdentifier,DBInstanceStatus]' --output table
aws ec2 describe-addresses --query 'Addresses[?AssociationId==`null`].[PublicIp,AllocationId]' --output table
```

Se qualquer comando retornar linhas, o `destroy` não terminou — investigar antes de encerrar a sessão. A
conta AWS Academy só para automaticamente instâncias EC2 ao encerrar a sessão; RDS continua cobrando até ser
destruído explicitamente.

## Buscar o kubeconfig (sem equivalente a `az aks get-credentials` — k3s não é gerenciado)

Via SSM, sem depender de SSH nem de regra de firewall (mesmo caminho da pipeline):

```bash
INSTANCE_ID=$(aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" \
  "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)

aws ssm start-session --target "$INSTANCE_ID" \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["6443"],"localPortNumber":["6443"]}' &

aws ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript \
  --parameters 'commands=["cat /home/ec2-user/.kube/config"]' --query 'Command.CommandId' --output text
# ...e recuperar o conteúdo com "aws ssm get-command-invocation", trocando o host por 127.0.0.1
```

Alternativa por SSH (requer que o seu IP esteja em `allowed_cidr`):

```bash
terraform output -raw ssh_private_key > k3s-key.pem && chmod 600 k3s-key.pem
scp -i k3s-key.pem ec2-user@$(terraform output -raw k3s_public_ip):/home/ec2-user/.kube/config ./kubeconfig
export KUBECONFIG=./kubeconfig
kubectl get nodes
```

`k3s-key.pem`, `kubeconfig`, `tfplan` e qualquer `*.tfstate` estão no `.gitignore` — conferido com
`git ls-files`, nenhum deles é versionado.

## Custo estimado

Ver tabela completa e atualizada em [`../docs/spec-terraform-aws.md`](../docs/spec-terraform-aws.md).
Resumo: ≈ US$0,061/hora combinado (EC2 + RDS + storage); o orçamento é único para o curso inteiro
(US$50, não mensal) — revalidar preços se muito tempo tiver passado desde a última estimativa.
