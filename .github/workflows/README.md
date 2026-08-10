# CI/CD — gatilhos e GitHub Secrets

## Quando cada job roda

| Job                 | Todo push/PR | Tag `v*` | `workflow_dispatch` | Toca a AWS |
|---------------------|:------------:|:--------:|:-------------------:|:----------:|
| `build-test`        | ✅           | ✅       | ✅                  | não        |
| `terraform-check`   | ✅           | ✅       | ✅                  | não        |
| `docker-build-push` | —            | ✅       | ✅                  | **sim**    |
| `terraform-apply`   | —            | ✅       | ✅                  | **sim**    |
| `deploy-k8s`        | —            | ✅       | ✅                  | **sim**    |
| `smoke-test`        | —            | ✅       | ✅                  | **sim**    |

Entrega automática por marco de release:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Push comum na `master` roda só build, testes e validação estática do Terraform — de propósito:
cada `apply` recria EC2 e RDS numa conta AWS Academy de crédito limitado. Ver
[`../../oficina/docs/spec-github-actions.md`](../../oficina/docs/spec-github-actions.md) para o
raciocínio completo (a conta do Academy não sustenta OIDC nem credenciais permanentes).

## Secrets

### Renovar a cada sessão (credenciais temporárias, expiram no "Start Lab")

| Secret                  | Origem                              |
|-------------------------|-------------------------------------|
| `AWS_ACCESS_KEY_ID`     | Painel "AWS Details" do AWS Academy |
| `AWS_SECRET_ACCESS_KEY` | Painel "AWS Details" do AWS Academy |
| `AWS_SESSION_TOKEN`     | Painel "AWS Details" do AWS Academy |

### Estáveis (definir uma vez)

| Secret                                                   | Observação                                                                                                                                                            |
|----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ALLOWED_CIDR`                                           | Seu IP público atual, formato `x.x.x.x/32`. Libera SSH (22) e a aplicação (80). `0.0.0.0/0` é **rejeitado** por uma validação do Terraform                            |
| `DB_PASSWORD`                                            | Mesma senha usada em `TF_VAR_db_password`. Mínimo de 16 caracteres (validado no plan)                                                                                 |
| `DB_USERNAME`                                            | `oficina_admin`                                                                                                                                                       |
| `MAILER_HOST` / `MAILER_USERNAME` / `MAILER_PASSWORD`    | Provedor SMTP real (não Mailpit). **Necessário para a aprovação pública funcionar** — o código de autorização de uso único só chega ao cliente por e-mail             |
| `APP_SEED_ADMIN_PASSWORD` / `APP_SEED_MECHANIC_PASSWORD` | Só usados enquanto `APP_SEED_ENABLED=true` no `configmap.yaml`                                                                                                        |
| `JWT_PRIVATE_KEY_PEM` / `JWT_PUBLIC_KEY_PEM`             | Conteúdo de `keys/privateKey.pem` / `keys/publicKey.pem` (gerados por `generate-keys.sh`) — precisa ser o **mesmo par** usado localmente, senão tokens emitidos fora da pipeline ficam inválidos nos pods |

### O que **não** é mais secret

`DB_HOST`, `ECR_REPOSITORY_URL`, `K3S_PUBLIC_IP` e `K3S_SSH_PRIVATE_KEY` foram removidos. Os três
primeiros agora saem dos **outputs do job `terraform-apply`** e são passados direto ao `deploy-k8s`;
eram a principal fonte de deploy quebrado, porque a infraestrutura é recriada do zero a cada sessão e
esses endereços mudam. O acesso ao node não usa mais SSH: é SSM Run Command, que dispensa distribuir a
chave privada como secret.

## Acesso ao cluster: nenhum passo manual

Versões anteriores deste documento pediam para abrir a porta 6443 ao mundo
(`terraform apply -var="allowed_cidr=0.0.0.0/0"`) antes do deploy, porque o runner do GitHub tem IP
dinâmico. **Isso não é mais necessário e não deve ser feito.** O pipeline usa a composite action
[`../actions/k3s-kubeconfig`](../actions/k3s-kubeconfig/action.yml), que busca o kubeconfig por SSM Run
Command e abre um túnel do Session Manager para a porta 6443 — a API do Kubernetes nunca é exposta no
security group, em nenhum momento.

Pré-requisito: a EC2 precisa do `LabInstanceProfile` (já anexado pelo Terraform) para que o agente do
SSM se registre. O job falha com mensagem explícita se não encontrar instância `Project=oficina` em
execução.
