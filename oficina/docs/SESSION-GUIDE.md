# Guia de Sessão — AWS Academy (Oficina Mecânica)

> Runbook executável de uma sessão completa: subir a infraestrutura, publicar a aplicação, validar,
> demonstrar e destruir. Os comandos são **PowerShell** (o shell padrão no Windows) — as armadilhas
> documentadas na seção de problemas conhecidos são específicas dele.
>
> Documentos relacionados: [`../infra/README.md`](../infra/README.md) (Terraform em detalhe),
> [`../k8s/README.md`](../k8s/README.md) (manifestos em detalhe),
> [`../../.github/workflows/README.md`](../../.github/workflows/README.md) (pipeline e secrets),
> [`PASSO-A-PASSO-GRAVACAO.md`](PASSO-A-PASSO-GRAVACAO.md) (o que mostrar no vídeo).

---

## ⚠️ Leia antes de tudo

### 1. "End Lab" não destrói nada

O AWS Academy apenas **para instâncias EC2** ao encerrar a sessão. RDS, ECR, Elastic IP e volumes EBS
**continuam existindo e cobrando**. A única forma de parar a cobrança é `terraform destroy` executado
**antes** de clicar "End Lab". O orçamento (US$50) é único para o curso inteiro e não recarrega.

### 2. Escolha UM caminho de Terraform e não misture

| | Caminho A — Pipeline | Caminho B — Manual |
|---|---|---|
| Quem roda o `apply` | GitHub Actions, Terraform **1.10.5** | Sua máquina, Terraform **1.15.8** |
| Demonstra CI/CD fazendo deploy | ✅ sim (requisito do enunciado) | ❌ não |
| Velocidade | ~8 min | ~6 min |

**Misturar os dois corrompe o fluxo.** O state guarda a versão do Terraform que o escreveu; se você
aplicar localmente com 1.15.8, o job do CI (1.10.5) passa a falhar com *"state snapshot was created by
Terraform v1.15.8, which is newer than current v1.10.5"*.

- **Para a gravação do vídeo, use o Caminho A.** É o que demonstra o requisito de CD.
- Se preferir o Caminho B, alinhe antes o `terraform_version` do
  [`ci-cd.yml`](../../.github/workflows/ci-cd.yml) para `1.15.8` nos dois jobs que o declaram.

Confira quem escreveu o state atual antes de decidir:

```powershell
aws s3 cp s3://victor-duarte-mendonca-oficina-tfstate/oficina/terraform.tfstate - | Select-String terraform_version
```

---

## O que persiste entre sessões x o que é recriado

| Persiste (não refazer) | Recriado a cada `apply` |
|---|---|
| Código no git (`.tf`, `k8s/*.yaml`, docs) | EC2 + k3s — **IP público novo toda vez** |
| Bucket S3 do state (bootstrap único) | RDS — **endpoint novo toda vez** |
| `backend.tf` com o bucket já preenchido | ECR (repositório e imagens — precisa novo build/push) |
| Chaves JWT locais em `keys/*.pem` | Chave SSH (`k3s-key.pem`, regenerada pelo Terraform) |
| Secrets estáveis no GitHub | Todos os Secrets do k8s (o namespace some com o cluster) |

Cada sessão nova provisiona praticamente tudo do zero. Isso é intencional — é o que torna o `destroy`
completo e seguro.

---

## Pré-requisitos (uma vez só, já validados nesta máquina)

| Ferramenta | Versão detectada | Para quê |
|---|---|---|
| AWS CLI | 2.35.22 | tudo |
| Terraform | 1.15.8 | Caminho B |
| kubectl | 1.36.1 | acesso ao cluster |
| Docker Desktop | 29.6.2 | build da imagem (Caminho B) |
| `session-manager-plugin` | 1.2.835.0 | túnel para a API do k3s |
| `ssh` / `scp` | OpenSSH do Windows | alternativa ao SSM |

Se o `session-manager-plugin` faltar em outra máquina:
<https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html>

**Bootstrap do bucket S3 do state:** execução única, já feita. Comandos em
[`../infra/README.md`](../infra/README.md), seção *"Bootstrap do backend remoto"*.

---

# Parte 1 — Início de sessão (comum aos dois caminhos)

## 1.1 Start Lab

No AWS Academy: **Start Lab** → aguarde o círculo ficar verde → **AWS Details** → copie as três
credenciais temporárias para `~/.aws/credentials` (ou exporte como variáveis de ambiente).

## 1.2 Confirmar que a credencial está válida

```powershell
aws sts get-caller-identity
```

Se responder `ExpiredToken`, a sessão não foi iniciada ou já expirou — repita 1.1.

## 1.3 Variáveis da sessão

Não persistem entre terminais; repita a cada sessão e em cada janela nova que for rodar Terraform.

```powershell
$env:TF_VAR_allowed_cidr = "$((Invoke-RestMethod https://api.ipify.org))/32"
$env:TF_VAR_db_password  = Read-Host "Senha do RDS (min. 16 chars, sem / @ aspas ou espaco)"
$env:TF_VAR_allowed_cidr
```

> A senha do RDS **rejeita** `/`, `@`, `"` e espaço (restrição do próprio RDS) e precisa de no mínimo
> 16 caracteres (validação no `plan`). `allowed_cidr` recusa `0.0.0.0/0` por validação.

---

# Parte 2A — Caminho PIPELINE (recomendado para o vídeo)

## 2A.1 Renovar os secrets da sessão no GitHub

`Settings → Secrets and variables → Actions`. Só estes três mudam a cada sessão:

| Secret | Origem |
|---|---|
| `AWS_ACCESS_KEY_ID` | painel *AWS Details* |
| `AWS_SECRET_ACCESS_KEY` | painel *AWS Details* |
| `AWS_SESSION_TOKEN` | painel *AWS Details* |

Confira também `ALLOWED_CIDR` se o seu IP mudou (valor impresso em 1.3).

## 2A.2 Disparar o workflow

GitHub → aba **Actions** → workflow **CI/CD** → **Run workflow** → branch `master`.

Alternativa por tag, se quiser um marco de release:

```powershell
git tag v1.0.0; git push origin v1.0.0
```

## 2A.3 Acompanhar os 6 jobs

| # | Job | O que faz |
|---|---|---|
| 1 | `build-test` | `mvn verify` — 257 testes + gate JaCoCo |
| 2 | `terraform-check` | `fmt -check` e `validate`, sem tocar na AWS |
| 3 | `docker-build-push` | build, **scan Trivy** e só então push ao ECR |
| 4 | `terraform-apply` | provisiona VPC, EC2/k3s, RDS, ECR; exporta os endereços como outputs |
| 5 | `deploy-k8s` | cria os Secrets e aplica todos os manifestos; `rollout undo` se falhar |
| 6 | `smoke-test` | health pelo Service e pelo Ingress; reverte em falha |

O job `terraform-apply` leva ~5 min (o RDS é o gargalo). Depois do `apply`, o `user_data` ainda instala
o k3s em background por ~1–2 min — o `deploy-k8s` já lida com isso.

## 2A.4 Pegar a URL publicada

No *Summary* da execução, seção **"Ambiente publicado"**. Siga para a **Parte 3**.

---

# Parte 2B — Caminho MANUAL

> Só se você **não** for demonstrar o CI/CD fazendo deploy. Exige o alinhamento de versão descrito no
> aviso 2 lá em cima.

## 2B.1 Provisionar

```powershell
cd oficina\infra
terraform init
terraform plan -out=tfplan
```

Revise o plano — deve ter **~17 recursos, zero `aws_iam_role`, zero NAT Gateway**. Só então:

```powershell
terraform apply "tfplan"
```

Aguarde 1–2 min após o término: o `user_data` instala o k3s em background.

```powershell
terraform output application_url
terraform output rds_endpoint
terraform output ecr_repository_url
```

## 2B.2 Build e push da imagem

Docker Desktop precisa estar aberto (`docker info` sem erro de pipe).

```powershell
cd ..
$ecrUrl   = terraform -chdir=infra output -raw ecr_repository_url
$registry = $ecrUrl.Split('/')[0]
$ecrPassword = aws ecr get-login-password --region us-east-1
docker login --username AWS --password $ecrPassword $registry
docker build -t oficina-app .
docker tag oficina-app:latest "${ecrUrl}:latest"
docker push "${ecrUrl}:latest"
```

> Use `--password $variavel`, **não** `--password-stdin` com pipe: o pipe do PowerShell corrompe o
> token e o `docker login` devolve `400 Bad Request`.

## 2B.3 Acesso ao cluster

Faça a **Parte 3** agora e volte para 2B.4.

## 2B.4 Secrets do cluster

```powershell
kubectl apply -f k8s\namespace.yaml -f k8s\configmap.yaml
```

```powershell
# Credencial do ECR (token de 12h — recriar se a sessao passar disso)
$ecrPassword = aws ecr get-login-password --region us-east-1
kubectl create secret docker-registry ecr-registry-secret --namespace oficina --docker-server=$ecrUrl --docker-username=AWS --docker-password=$ecrPassword --dry-run=client -o yaml | kubectl apply -f -
```

```powershell
# Chaves JWT — o MESMO par para todas as replicas.
# Reaproveite keys\*.pem se ja existir; senao gere com .\generate-keys.sh (Git Bash).
kubectl create secret generic oficina-jwt-keys --namespace oficina --from-file=privateKey.pem=keys\privateKey.pem --from-file=publicKey.pem=keys\publicKey.pem --dry-run=client -o yaml | kubectl apply -f -
```

```powershell
# Secret da aplicacao. Nenhum valor real neste arquivo: tudo vem de variavel.
$rdsEndpoint = terraform -chdir=infra output -raw rds_endpoint
$smtpHost = Read-Host "MAILER_HOST (deixe vazio se nao for usar SMTP)"
$smtpUser = Read-Host "MAILER_USERNAME"
$smtpPass = Read-Host "MAILER_PASSWORD"
$seedAdmin = Read-Host "Senha do usuario admin"
$seedMech  = Read-Host "Senha do usuario mecanico"

kubectl create secret generic oficina-secrets --namespace oficina `
  --from-literal=DB_HOST=$rdsEndpoint `
  --from-literal=DB_USERNAME=oficina_admin `
  --from-literal=DB_PASSWORD=$env:TF_VAR_db_password `
  --from-literal=MAILER_HOST=$smtpHost `
  --from-literal=MAILER_USERNAME=$smtpUser `
  --from-literal=MAILER_PASSWORD=$smtpPass `
  --from-literal=APP_SEED_ADMIN_PASSWORD=$seedAdmin `
  --from-literal=APP_SEED_MECHANIC_PASSWORD=$seedMech `
  --dry-run=client -o yaml | kubectl apply -f -
```

> **O `DataSeeder` não atualiza senha de usuário já existente.** Se o banco sobreviveu a um deploy
> anterior e você trocar `APP_SEED_ADMIN_PASSWORD`, o login continua com a senha antiga. Mantenha a
> mesma senha durante a sessão.

## 2B.5 Aplicar os manifestos

**Cinco** arquivos além do namespace/configmap — `ingress.yaml` e `networkpolicy.yaml` fazem parte:

```powershell
(Get-Content k8s\deployment.yaml) -replace 'CHANGE_ME_ECR_REPOSITORY_URL', $ecrUrl | kubectl apply -f -
kubectl apply -f k8s\service.yaml -f k8s\ingress.yaml -f k8s\hpa.yaml -f k8s\networkpolicy.yaml
kubectl -n oficina rollout status deployment/oficina-app --timeout=300s
```

---

# Parte 3 — Acesso ao cluster (`kubectl`)

Necessário nos dois caminhos — inclusive no A, para demonstrar o HPA.

**A porta 6443 nunca é exposta no security group.** O acesso passa por túnel do SSM, exatamente como a
pipeline faz.

## 3.1 Descobrir a instância

```powershell
$instanceId = aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=running" --query "Reservations[0].Instances[0].InstanceId" --output text
$instanceId
```

Se voltar `None`, a EC2 não está no ar — rode o `apply` antes.

## 3.2 Baixar o kubeconfig via SSM Run Command

> **Por que um arquivo JSON e não o parâmetro inline:** o PowerShell 5.1 remove as aspas ao passar
> argumentos para executáveis nativos, e escapá-las com barra invertida faz o argumento **quebrar no
> espaço** de `cat /home/...` (verificado: chega como `commands=["cat`). O `file://` contorna as duas
> armadilhas de uma vez.

```powershell
'{"commands":["cat /home/ec2-user/.kube/config"]}' | Out-File -Encoding ascii ssm-params.json
$cmdId = aws ssm send-command --instance-ids $instanceId --document-name AWS-RunShellScript --parameters file://ssm-params.json --query "Command.CommandId" --output text
Start-Sleep -Seconds 6
aws ssm get-command-invocation --command-id $cmdId --instance-id $instanceId --query "StandardOutputContent" --output text | Out-File -Encoding ascii kubeconfig
(Get-Content kubeconfig) -replace 'server: https://[^:]+:6443', 'server: https://127.0.0.1:6443' | Set-Content -Encoding ascii kubeconfig
$env:KUBECONFIG = "$PWD\kubeconfig"
Remove-Item ssm-params.json
```

Confira que o arquivo veio íntegro antes de seguir:

```powershell
Select-String -Path kubeconfig -Pattern "server:","clusters:" | Select-Object -First 2
```

## 3.3 Abrir o túnel — **em uma segunda janela do PowerShell, e deixe aberta**

Bloco autocontido: redescobre a instância, porque `$instanceId` não existe na janela nova.

```powershell
$instanceId = aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=running" --query "Reservations[0].Instances[0].InstanceId" --output text
aws ssm start-session --target $instanceId --document-name AWS-StartPortForwardingSession --parameters '{\"portNumber\":[\"6443\"],\"localPortNumber\":[\"6443\"]}'
```

> As barras invertidas aqui **são necessárias** — sem elas o PowerShell 5.1 entrega
> `{portNumber:[6443]}` (sem aspas) e o AWS CLI rejeita. Como não há espaço dentro das aspas, o escape
> funciona neste caso, ao contrário do 3.2.

## 3.4 Confirmar

Na janela original:

```powershell
kubectl get nodes
```

Deve aparecer um node `Ready`. Se travar sem erro, o túnel caiu ou o IP mudou — veja a tabela de
problemas conhecidos.

### Alternativa por SSH (se o seu IP estiver em `allowed_cidr`)

```powershell
cd oficina\infra
cmd /c "terraform output -raw ssh_private_key > k3s-key.pem"
Get-Item k3s-key.pem | Select-Object Length     # confira ~3-4 KB
icacls k3s-key.pem /inheritance:r
icacls k3s-key.pem /grant:r "$($env:USERNAME):(R)"
$k3sIp = terraform output -raw k3s_public_ip
scp -i k3s-key.pem ec2-user@${k3sIp}:/home/ec2-user/.kube/config kubeconfig
$env:KUBECONFIG = "$PWD\kubeconfig"
kubectl get nodes
```

> `>` e `|` do PowerShell corrompem o PEM (encoding/CRLF) — o `cmd /c` é obrigatório aqui.

---

# Parte 4 — Validar o ambiente

```powershell
kubectl get pods,hpa,svc,ingress -n oficina
```

```powershell
kubectl logs -n oficina -l app=oficina-app --tail=50
```

Procure nos logs: conexão com o RDS, migrações do Flyway e `Profile prod activated`.

**Pelo Ingress, de fora do cluster** — a porta 80 é liberada apenas para o `allowed_cidr`:

```powershell
$appUrl = terraform -chdir=infra output -raw application_url
curl.exe "$appUrl/q/health/live"
curl.exe "$appUrl/q/health/ready"
```

> Em perfil `prod` o **Swagger fica desabilitado** por hardening. Para consumir a API no ambiente
> publicado, use a collection Postman apontando `baseUrl` para `$appUrl`.

---

# Parte 5 — Demonstrar o autoscaling

## 5.1 Confirmar que há métrica

```powershell
kubectl top pods -n oficina
kubectl get hpa -n oficina
```

> **Se a coluna de métrica vier `<unknown>`, pare e espere.** O HPA não escala sem o metrics-server e
> não haverá o que mostrar. Leva 1–2 min após o deploy.

## 5.2 Criar uma OS no ambiente cloud

Pelo Postman, com `baseUrl` = `$appUrl`: cliente → veículo → serviço → peça → OS.
**Anote o `orderNumber` real** — na cloud a numeração não recomeça em `OS-000001`.

> Para aprovar o orçamento na cloud, use o **canal administrativo**
> `PATCH /admin/work-orders/{id}/approve`. O canal público exige o código de uso único, que só chega
> por SMTP real. O fluxo do e-mail é demonstrado no ambiente local, com o Mailpit.

## 5.3 Observar e gerar carga

Janela 1:

```powershell
kubectl get hpa,pods -n oficina -w
```

Janela 2 — carga **de dentro do cluster** (`port-forward` é fluxo TCP único e vira gargalo antes de
gerar CPU):

```powershell
kubectl run load-test -n oficina --rm -it --restart=Never --image=williamyeh/hey -- -z 120s -c 50 http://oficina-service.oficina.svc.cluster.local/public/work-orders/<ORDER_NUMBER>/status
```

> Se a imagem falhar por rate limit do Docker Hub, troque o alvo por
> `http://oficina-service.oficina.svc.cluster.local/q/health/ready` — faz query no banco e gera CPU
> equivalente.

Esperado: CPU cruza 70% → HPA vai a 3 e depois 4 réplicas → pods novos em `Running`. O scale-in
acontece após a janela de 180s.

---

# Parte 6 — Debug direto no banco (opcional)

O RDS é privado (`publicly_accessible = false`) e só é alcançável de dentro da VPC. Para usar
DataGrip/psql da sua máquina, abra um túnel pela EC2 e deixe a janela aberta:

```powershell
$k3sIp = terraform -chdir=infra output -raw k3s_public_ip
$rds   = terraform -chdir=infra output -raw rds_endpoint
ssh -i infra\k3s-key.pem -L 5432:${rds}:5432 ec2-user@$k3sIp
```

No cliente, conecte em `localhost:5432` — database `oficina_db`, usuário `oficina_admin`, senha do
`TF_VAR_db_password`.

---

# Parte 7 — Fim de sessão (SEMPRE, antes de "End Lab")

```powershell
cd oficina\infra
terraform destroy -auto-approve
```

## Conferir que não sobrou órfão

```powershell
aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=pending,running,stopping,stopped" --query "Reservations[].Instances[].[InstanceId,State.Name]" --output table
```

```powershell
aws rds describe-db-instances --query "DBInstances[?contains(DBInstanceIdentifier,'oficina')].[DBInstanceIdentifier,DBInstanceStatus]" --output table
```

```powershell
aws ec2 describe-addresses --query 'Addresses[?AssociationId==`null`].[PublicIp,AllocationId]' --output table
```

> Aspas **simples** e os backticks em volta de `null`: sem os backticks o JMESPath lê `null` como nome
> de campo em vez do literal; com aspas duplas, o PowerShell consumiria o backtick como escape.

Varredura geral por tag — pega qualquer coisa que os três acima não cubram:

```powershell
aws resourcegroupstaggingapi get-resources --tag-filters Key=Project,Values=oficina --query "ResourceTagMappingList[].ResourceARN" --output table
```

**Se qualquer comando retornar linhas, não encerre a sessão.** Investigue e destrua manualmente antes
de clicar "End Lab". O bucket S3 do state é a única exceção — ele persiste de propósito.

---

# Secrets do GitHub

## Renovar a cada sessão

| Secret | Origem |
|---|---|
| `AWS_ACCESS_KEY_ID` | painel *AWS Details* |
| `AWS_SECRET_ACCESS_KEY` | painel *AWS Details* |
| `AWS_SESSION_TOKEN` | painel *AWS Details* |

## Estáveis (definir uma vez)

| Secret | Observação |
|---|---|
| `ALLOWED_CIDR` | seu IP público `/32`; atualize se mudar |
| `DB_USERNAME` | `oficina_admin` |
| `DB_PASSWORD` | a mesma de `TF_VAR_db_password`, mínimo 16 caracteres |
| `MAILER_HOST` / `MAILER_USERNAME` / `MAILER_PASSWORD` | SMTP real — **sem isso a aprovação pública não funciona na cloud** |
| `APP_SEED_ADMIN_PASSWORD` / `APP_SEED_MECHANIC_PASSWORD` | usados enquanto `APP_SEED_ENABLED=true` no ConfigMap |
| `JWT_PRIVATE_KEY_PEM` / `JWT_PUBLIC_KEY_PEM` | conteúdo de `keys/*.pem`, o mesmo par usado localmente |

## O que **não** é mais secret

`DB_HOST`, `ECR_REPOSITORY_URL`, `K3S_PUBLIC_IP` e `K3S_SSH_PRIVATE_KEY` foram removidos. Os dois
primeiros saem dos **outputs do job `terraform-apply`** e são passados direto ao `deploy-k8s` — eram a
principal fonte de deploy quebrado, já que a infraestrutura é recriada do zero e esses endereços mudam
a cada sessão. O acesso ao node não usa mais SSH: é SSM, que dispensa distribuir a chave privada.

---

# Problemas conhecidos e soluções

| Sintoma | Causa | Solução |
|---|---|---|
| `terraform init`: bucket S3 não existe | Bootstrap do backend não foi feito | `aws s3 mb` + versionamento + criptografia — ver [`../infra/README.md`](../infra/README.md) |
| CI: *"state snapshot was created by Terraform v1.15.8, which is newer than current v1.10.5"* | `apply` local com Terraform mais novo que o do workflow | Escolher um caminho só (aviso 2 no topo) ou alinhar `terraform_version` no `ci-cd.yml` |
| `ingress.0.description doesn't comply with restrictions` | Acento em descrição de security group | Descrições de SG só aceitam ASCII sem acento |
| `MasterUserPassword is not a valid password` | Senha do RDS com `/`, `@`, `"` ou espaço | Trocar por ASCII permitido, mínimo 16 caracteres |
| SSH: `Load key "k3s-key.pem": invalid format` | `>` / `\|` do PowerShell corrompem o PEM | Usar `cmd /c "... > k3s-key.pem"` |
| SSH: `Permission denied (publickey...)` | `k3s-key.pem` é de uma sessão anterior — cada `apply` gera par novo | Regerar a chave (Parte 3.4, alternativa SSH) |
| `cmd /c "terraform output ... > k3s-key.pem"`: *Acesso negado* | `icacls .../grant:r "...(R)"` de sessão anterior deixou o arquivo só leitura | `icacls k3s-key.pem /grant:r "$($env:USERNAME):(F)"`, regravar, e reaplicar `/inheritance:r` + `:(R)` |
| `kubectl`: erro de certificado TLS (`not <IP público>`) | Cert do k3s sem o Elastic IP | Já corrigido via `--tls-san` no `user_data.sh.tpl` |
| `kubectl` trava sem erro | Túnel SSM caiu, ou o IP mudou e o `kubeconfig` local está velho | Conferir `terraform output -raw k3s_public_ip`; refazer a Parte 3 |
| `docker login`: `400 Bad Request` | Pipe do PowerShell corrompeu o token | `--password $variavel` em vez de `--password-stdin` via pipe |
| `kubectl create secret docker-registry --docker-password-stdin`: unknown flag | Flag não existe no `kubectl` (só no `docker login`) | Usar `--docker-password=$valor` |
| `docker build`: falha ao conectar no pipe do daemon | Docker Desktop fechado | Abrir o Docker Desktop antes de qualquer `docker` |
| Pod em `ContainerCreating` — `FailedMount ... secret "oficina-jwt-keys" not found` | O Secret das chaves JWT não foi criado | Refazer o passo das chaves JWT em 2B.4 |
| Pods sobem mas o login falha com a senha nova | `DataSeeder` não atualiza usuário existente | Manter a mesma senha na sessão, ou recriar o banco |
| HPA com métrica `<unknown>` e nunca escala | metrics-server ainda coletando | Esperar 1–2 min; conferir `kubectl top pods -n oficina` |
| Aprovação pública devolve erro / código nunca chega | `MAILER_*` não configurados; falha de SMTP é engolida de propósito | Usar o canal administrativo na cloud; demonstrar o e-mail localmente com Mailpit |
| CI `deploy-k8s`: `ssh: connect ... port 22: Connection timed out` | Runners do GitHub **não têm IP de saída estável** — abrir o SG para um IP não funciona | Já resolvido: o pipeline usa SSM Run Command + túnel, sem abrir porta nenhuma |

---

# Higiene do repositório

- **Nunca commitar state, plano ou chaves.** O `.gitignore` cobre `**/*.tfstate`, `**/tfplan`,
  `**/*.tfvars`, `infra/*.pem` e `**/kubeconfig` em qualquer diretório. Antes de um `git add .`,
  confira `git status`.
- **Nenhuma senha real neste documento.** Os comandos leem tudo de variável de ambiente ou `Read-Host`.
  Se você colar uma senha aqui para "facilitar", ela vai para o git — e para o vídeo.
- **Gate de testes: `mvn verify`.** Cobre **257 testes** e o gate JaCoCo de 80% por pacote em
  `domain.model`, `domain.valueobject`, `application.service`, `application.validation`,
  `infrastructure.security`, `infrastructure.adapters.out.notification` e `interfaces.exception`. Se o
  gate reclamar, é teste faltando — não baixe o mínimo.
- **Branch default é `master`.** Se mudar, atualize o trigger em `ci-cd.yml`, senão o pipeline para de
  disparar.
