# Infraestrutura (Terraform) — Oficina Mecânica (AWS Academy)

## Recursos provisionados

| Recurso            | Nome               | Tipo                                           |
|--------------------|--------------------|------------------------------------------------|
| VPC                | `oficina-vpc`      | 1 subnet pública + 2 privadas, sem NAT Gateway |
| Cluster K8s        | `oficina-k3s`      | k3s single-node, EC2 `t3.medium`               |
| Banco de dados     | `oficina-postgres` | RDS PostgreSQL `db.t4g.micro`, single-AZ       |
| Registro de imagem | `oficina-app`      | ECR privado                                    |

Justificativa completa das escolhas (por que k3s e não EKS, por que sem NAT, por que state em S3) em
[`../spec-terraform-aws.md`](../spec-terraform-aws.md).

## Pré-requisitos

- Terraform >= 1.7
- AWS CLI configurado com as credenciais temporárias da sessão AWS Academy ("AWS Details" no painel do
  Academy → `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN`, como variáveis de ambiente
  ou em `~/.aws/credentials`) — expiram com a sessão, precisam ser renovadas a cada "Start Lab"
- Variável `TF_VAR_db_password` definida (nunca commitar em `.tfvars`)
- Variável `TF_VAR_allowed_cidr` definida com o seu IP público no formato `x.x.x.x/32`

## Bootstrap do backend remoto — execução única, antes do primeiro `terraform init`

Nomes de bucket S3 são únicos globalmente (todas as contas AWS do mundo) — escolha um sufixo que não
colida, ex. seu nome ou RM:

```bash
aws s3 mb s3://victor-duarte-mendonca-oficina-tfstate --region us-east-1
aws s3api put-bucket-versioning --bucket victor-duarte-mendonca-oficina-tfstate --versioning-configuration Status=Enabled
```

Depois, edite `backend.tf` e troque `CHANGE_ME-oficina-tfstate` pelo nome escolhido. Esse bucket **não** é
destruído pelo `terraform destroy` de cada sessão — persiste pelo curso inteiro.

## Como aplicar

```bash
cd infra
terraform init
terraform plan -out=tfplan
# Revisar o plano manualmente antes de prosseguir — gate de custo cumprido antes deste passo
terraform apply tfplan
```

## Como destruir — obrigatório ao final de CADA sessão, não só ao final do projeto

```bash
cd infra
terraform destroy -auto-approve
```

Depois, confirmar ausência de recursos órfãos:

```bash
aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=pending,running,stopping,stopped" --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output table
aws rds describe-db-instances --query 'DBInstances[?contains(DBInstanceIdentifier,`oficina`)].[DBInstanceIdentifier,DBInstanceStatus]' --output table
aws ec2 describe-addresses --query 'Addresses[?AssociationId==`null`].[PublicIp,AllocationId]' --output table
```

Se qualquer comando retornar linhas, o `destroy` não terminou — investigar antes de encerrar a sessão. A
conta AWS Academy só para automaticamente instâncias EC2 ao encerrar a sessão; RDS continua cobrando até ser
destruído explicitamente.

## Buscar o kubeconfig (sem equivalente a `az aks get-credentials` — k3s não é gerenciado)

```bash
terraform output -raw ssh_private_key > k3s-key.pem && chmod 600 k3s-key.pem
scp -i k3s-key.pem ec2-user@$(terraform output -raw k3s_public_ip):/home/ec2-user/.kube/config ./kubeconfig
export KUBECONFIG=./kubeconfig
kubectl get nodes
```

## Custo estimado

Ver tabela completa e atualizada em [`../spec-terraform-aws.md`](../spec-terraform-aws.md). Resumo:
≈ US$0,061/hora combinado (EC2 + RDS + storage); orçamento é único para o curso inteiro (US$50, não mensal) —
revalidar preços via skill `aws-cost-operations` se muito tempo tiver passado desde a última estimativa.
