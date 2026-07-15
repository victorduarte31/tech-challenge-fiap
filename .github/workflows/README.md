# CI/CD — GitHub Secrets necessários

`build-test` roda automático em todo push/PR, sem segredo nenhum. Os demais jobs (`docker-build-push`,
`terraform-apply`, `deploy-k8s`, `smoke-test`) só rodam via `workflow_dispatch` e exigem os secrets abaixo —
ver `oficina/spec-github-actions.md` para o porquê desse desenho (conta AWS Academy não sustenta OIDC nem
credenciais permanentes).

## Renovar a cada sessão (credenciais temporárias, expiram com o "Start Lab")

| Secret                  | Origem                              |
|-------------------------|-------------------------------------|
| `AWS_ACCESS_KEY_ID`     | Painel "AWS Details" do AWS Academy |
| `AWS_SECRET_ACCESS_KEY` | Painel "AWS Details" do AWS Academy |
| `AWS_SESSION_TOKEN`     | Painel "AWS Details" do AWS Academy |

## Atualizar a cada `terraform apply` (a EC2/RDS/ECR são recriados do zero por sessão — outputs mudam)

| Secret                | Origem                                     |
|-----------------------|--------------------------------------------|
| `K3S_PUBLIC_IP`       | `terraform output -raw k3s_public_ip`      |
| `K3S_SSH_PRIVATE_KEY` | `terraform output -raw ssh_private_key`    |
| `DB_HOST`             | `terraform output -raw rds_endpoint`       |
| `ECR_REPOSITORY_URL`  | `terraform output -raw ecr_repository_url` |
| `ALLOWED_CIDR`        | Seu IP público atual, formato `x.x.x.x/32` |

## Estáveis (definir uma vez, só trocar se decidir mudar)

| Secret                                                   | Observação                                                                                                                                                                                                                |
|----------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DB_PASSWORD`                                            | Mesma senha usada em `TF_VAR_db_password`                                                                                                                                                                                 |
| `DB_USERNAME`                                            | `oficina_admin`                                                                                                                                                                                                           |
| `MAILER_HOST` / `MAILER_USERNAME` / `MAILER_PASSWORD`    | Provedor SMTP real (não Mailpit)                                                                                                                                                                                          |
| `APP_SEED_ADMIN_PASSWORD` / `APP_SEED_MECHANIC_PASSWORD` | Só usados se `APP_SEED_ENABLED=true` no `configmap.yaml`                                                                                                                                                                  |
| `JWT_PRIVATE_KEY_PEM` / `JWT_PUBLIC_KEY_PEM`             | Conteúdo de `keys/privateKey.pem`/`keys/publicKey.pem` (gerados via `generate-keys.sh`) — precisa ser o **mesmo par** usado localmente, senão tokens emitidos fora da pipeline ficam inválidos nos pods aplicados por ela |

## Passo manual antes de `deploy-k8s`/`smoke-test`

O security group da EC2 restringe a porta 6443 (API do k3s) ao seu IP (`allowed_cidr`) — runners hospedados
do GitHub têm IP dinâmico e não alcançam por padrão. Antes de disparar esses dois jobs:

```bash
cd oficina/infra
terraform apply -var="allowed_cidr=0.0.0.0/0" -auto-approve
```

E reverter depois (voltar para o seu IP) assim que a execução terminar — não deixar a API do k3s aberta ao
mundo fora da janela do teste.
