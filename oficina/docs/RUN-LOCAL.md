# Rodar localmente

Dois caminhos, com propósitos diferentes. Escolha pelo que você precisa fazer:

|                | [**A — Stack 100% local**](#caminho-a--stack-100-local-docker-compose) | [**B — Front local contra o cluster**](#caminho-b--front-local-contra-o-cluster-na-aws) |
|----------------|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Precisa de AWS | ❌ não                                                                 | ✅ sim (sessão do Academy ativa)                                                        |
| Custo          | zero                                                                   | consome crédito enquanto a infra estiver de pé                                          |
| Banco          | PostgreSQL em container                                                | RDS da sessão                                                                           |
| E-mail         | Mailpit (captura tudo, com UI)                                         | SMTP real configurado no Secret                                                         |
| Quando usar    | desenvolver, testar API, gravar a demo das APIs                        | validar o front contra o ambiente publicado                                             |

Se a sua dúvida é "quero só rodar o backend e bater na API", é o **Caminho A**. Não há motivo para levantar a AWS para
isso.

---

# Caminho A — Stack 100% local (docker-compose)

## Pré-requisitos

| Ferramenta                                                  | Versão mínima |
|-------------------------------------------------------------|---------------|
| Docker + Docker Compose                                     | 24+ / 2.0+    |
| Java + Maven (só para `mvn quarkus:dev` ou rodar os testes) | 21+ / 3.9+    |

Nada mais. As chaves JWT são geradas pelo próprio container no primeiro start e persistem no volume
`oficina_jwt_keys`.

## Subir

```bash
cd oficina
docker compose up --build -d
docker compose ps          # os três serviços devem ficar "healthy"
```

Primeira execução leva alguns minutos (build da imagem). Depois, ~15s.

| Serviço    | Endereço                           | O que é                                                                  |
|------------|------------------------------------|--------------------------------------------------------------------------|
| API        | <http://localhost:8080>            | a aplicação                                                              |
| Swagger UI | <http://localhost:8080/swagger-ui> | contrato das APIs (perfil `docker` mantém habilitado)                    |
| Mailpit    | <http://localhost:8025>            | **caixa de entrada falsa** — todo e-mail enviado pela aplicação cai aqui |
| PostgreSQL | `localhost:5432`                   | `oficina` / `oficina123` / db `oficina_db`                               |

Acompanhar o boot:

```bash
docker compose logs -f app
```

## Credenciais

| Usuário    | Senha         | Papel                                 |
|------------|---------------|---------------------------------------|
| `admin`    | `admin123`    | ADMIN — acesso total                  |
| `mecanico` | `mecanico123` | MECHANIC — sem exclusões nem métricas |

São os defaults do `docker-compose.yml` (`APP_SEED_ADMIN_PASSWORD` / `APP_SEED_MECHANIC_PASSWORD`). Valem **apenas**
para desenvolvimento local.

## Passeio completo pela API

Sequência que exercita todos os requisitos da fase. Copie e cole bloco a bloco.

### 1. Login

```bash
BASE=http://localhost:8080
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
AUTH="Authorization: Bearer $TOKEN"
echo "${TOKEN:0:25}..."
```

### 2. Cadastros

O cliente **precisa de e-mail** — é por ele que sai o código de aprovação do orçamento.

```bash
CID=$(curl -s -X POST $BASE/admin/clients -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Ana Souza","cpfCnpj":"111.444.777-35","clientType":"PF","email":"ana@teste.local"}' \
  | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)

VID=$(curl -s -X POST $BASE/admin/vehicles -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"licensePlate\":\"ABC1D23\",\"brand\":\"Toyota\",\"model\":\"Corolla\",\"productionYear\":2020,\"clientId\":$CID}" \
  | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)

SID=$(curl -s -X POST $BASE/admin/services -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Troca de oleo","description":"Oleo + filtro","basePrice":120.00,"estimatedDurationMinutes":30}' \
  | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)

PID=$(curl -s -X POST $BASE/admin/parts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Oleo 5W30","description":"1L","unitPrice":45.90,"stockQuantity":50,"minimumStock":5,"unit":"L"}' \
  | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)

echo "cliente=$CID veiculo=$VID servico=$SID peca=$PID"
```

### 3. Abertura da OS (requisito: retorna a identificação única)

```bash
curl -s -X POST $BASE/admin/work-orders -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"clientCpfCnpj\":\"111.444.777-35\",\"vehicleId\":$VID,
       \"services\":[{\"serviceItemId\":$SID}],
       \"parts\":[{\"partId\":$PID,\"quantity\":2}]}" > /tmp/os.json

WID=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' /tmp/os.json | head -1)
ON=$(sed -n 's/.*"orderNumber":"\([^"]*\)".*/\1/p' /tmp/os.json)
echo "OS $ON (id=$WID)"
```

### 4. Consulta pública de status (sem autenticação)

```bash
curl -s $BASE/public/work-orders/$ON/status
```

Repare que a resposta **não** traz nome, CPF nem orçamento — só número, status e marcos temporais.

### 5. Diagnóstico e envio do orçamento

```bash
curl -s -X PATCH $BASE/admin/work-orders/$WID/start-diagnosis   -H "$AUTH" > /dev/null
curl -s -X PATCH $BASE/admin/work-orders/$WID/send-for-approval -H "$AUTH" | head -c 200
```

### 6. Pegar o código de autorização no e-mail

Abra <http://localhost:8025>. Deve haver um e-mail *"Orçamento da OS … aguardando sua aprovação"*, com uma linha
`Código de autorização: <43 caracteres>`.

Ou por linha de comando:

```bash
MSG=$(curl -s http://localhost:8025/api/v1/messages | tr ',' '\n' | grep -o '"ID":"[^"]*"' | head -1 | cut -d'"' -f4)
APTOK=$(curl -s http://localhost:8025/api/v1/message/$MSG | grep -o 'Código de autorização: [A-Za-z0-9_-]*' | sed 's/.*: //')
echo "código: $APTOK"
```

> Esse código **não aparece em nenhuma resposta da API** — nem para o admin autenticado. O e-mail é o
> único canal. É o que impede um terceiro de aprovar orçamento alheio só conhecendo o número da OS
> (sequencial) e o CPF.

### 7. Aprovação pelo canal público — inclusive os caminhos que devem falhar

```bash
# sem o código -> 400
curl -s -o /dev/null -w "sem codigo:     HTTP %{http_code}\n" -X POST $BASE/public/work-orders/$ON/approve \
  -H 'Content-Type: application/json' -d '{"clientCpfCnpj":"111.444.777-35"}'

# código chutado -> 404 (mesma resposta de OS inexistente, de propósito)
curl -s -o /dev/null -w "codigo errado:  HTTP %{http_code}\n" -X POST $BASE/public/work-orders/$ON/approve \
  -H 'Content-Type: application/json' -d '{"clientCpfCnpj":"111.444.777-35","approvalToken":"chutado"}'

# CPF de outro cliente -> 404
curl -s -o /dev/null -w "cpf errado:     HTTP %{http_code}\n" -X POST $BASE/public/work-orders/$ON/approve \
  -H 'Content-Type: application/json' -d "{\"clientCpfCnpj\":\"529.982.247-25\",\"approvalToken\":\"$APTOK\"}"

# correto -> 200, status vira IN_EXECUTION
curl -s -X POST $BASE/public/work-orders/$ON/approve \
  -H 'Content-Type: application/json' -d "{\"clientCpfCnpj\":\"111.444.777-35\",\"approvalToken\":\"$APTOK\"}" \
  | sed -n 's/.*"status":"\([^"]*\)".*/correto:        status=\1/p'

# reusar o mesmo código -> 422 (uso único)
curl -s -o /dev/null -w "reuso:          HTTP %{http_code}\n" -X POST $BASE/public/work-orders/$ON/approve \
  -H 'Content-Type: application/json' -d "{\"clientCpfCnpj\":\"111.444.777-35\",\"approvalToken\":\"$APTOK\"}"
```

Saída esperada: `400`, `404`, `404`, `status=IN_EXECUTION`, `422`.

### 8. Listagem ordenada e exclusão lógica

```bash
# fila ativa: ordenada por Execução > Aguardando > Diagnóstico > Recebida, mais antigas primeiro
curl -s -D /tmp/h -o /tmp/b $BASE/admin/work-orders -H "$AUTH"
grep -i x-total-count /tmp/h                 # total ignorando a paginação
grep -o '"orderNumber":"[^"]*"' /tmp/b

# conclui e entrega — a OS sai da fila ativa
curl -s -X PATCH $BASE/admin/work-orders/$WID/complete -H "$AUTH" > /dev/null
curl -s -X PATCH $BASE/admin/work-orders/$WID/deliver  -H "$AUTH" > /dev/null

curl -s $BASE/admin/work-orders -H "$AUTH" | grep -c "$ON"              # 0 -> saiu da fila
curl -s "$BASE/admin/work-orders?status=DELIVERED" -H "$AUTH" | grep -c "$ON"  # 1 -> continua consultável
```

Exclusão **lógica**: `FINISHED`, `DELIVERED` e `CANCELLED` somem da fila de trabalho, mas nenhum registro é apagado.

### 9. Health checks e métricas

```bash
curl -s $BASE/q/health/live
curl -s $BASE/q/health/ready      # inclui a conexão com o banco
curl -s $BASE/q/health/started
curl -s $BASE/q/metrics | grep -E "^(jvm_memory_used_bytes|http_server_requests)" | head -5
```

## Rodar os testes

```bash
cd oficina
mvn verify
```

Não precisa de Docker nem de banco: o perfil `test` usa H2 em memória, e o Mailpit é substituído pelo
`MockMailbox` do Quarkus (é de lá que os testes leem o código de autorização, como o cliente faria). Relatório de
cobertura em `target/site/jacoco/index.html`.

## Modo dev com hot-reload

```bash
cd oficina
docker compose up -d postgres mailpit     # só as dependências
./generate-keys.sh                        # gera keys/*.pem (uma vez; no Windows use Git Bash)
mvn quarkus:dev
```

O `quarkus:dev` usa `DB_USERNAME`/`DB_PASSWORD` = `postgres` por padrão, enquanto o compose sobe o banco com `oficina`/
`oficina123`. Aponte as variáveis antes de subir:

```bash
DB_USERNAME=oficina DB_PASSWORD=oficina123 mvn quarkus:dev
```

## Front local contra o backend local

```bash
cd oficina-front
npm install        # só na primeira vez
npx ng serve --port 3000
```

Abra <http://localhost:3000/login>. O front aponta fixo para `http://localhost:8080` e o CORS do backend já libera
`localhost:3000` — não precisa configurar nada.

## Derrubar

```bash
docker compose down          # para tudo, preserva os dados
docker compose down -v       # apaga também o banco e as chaves JWT (recomeça do zero)
```

## Problemas comuns

| Sintoma                                                                         | Causa                                                                                                                                                                                                                | Correção                                                                                                                                            |
|---------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `port is already allocated` em 5432/8080                                        | outro Postgres ou app ocupando a porta                                                                                                                                                                               | pare o serviço conflitante, ou mude o mapeamento no `docker-compose.yml`                                                                            |
| App fica `unhealthy` e o log mostra falha de conexão                            | o Postgres ainda não terminou de subir                                                                                                                                                                               | o compose já espera pelo healthcheck; se persistir, `docker compose logs postgres`                                                                  |
| `401` no login com a senha certa                                                | o banco já tem `admin` de uma execução anterior com outra senha — o seed só cria quem **não existe**                                                                                                                 | `docker compose down -v` e suba de novo                                                                                                             |
| Mailpit vazio depois do `send-for-approval`                                     | o cliente foi cadastrado **sem** `email`                                                                                                                                                                             | recadastre o cliente com e-mail; sem ele a aprovação pública é impossível (use o canal administrativo)                                              |
| `no such file or directory` no entrypoint                                       | `docker-entrypoint.sh` com CRLF                                                                                                                                                                                      | o Dockerfile já normaliza; se persistir, confira `core.autocrlf` do seu git                                                                         |
| `400` sem corpo ao criar registro com acento no `curl -d` (Git Bash no Windows) | O MSYS converte os argumentos de linha de comando de UTF-8 para CP1252 antes de entregá-los ao `curl.exe`; o JSON chega com bytes inválidos e o Jackson recusa. Não acontece em Linux/macOS, nem via Postman/Swagger | Use payloads sem acento (como os deste guia), ou mande o corpo por arquivo: `printf '%s' '<json>' > body.json && curl --data-binary @body.json ...` |

---

# Caminho B — Front local contra o cluster na AWS

Pré-condição: infra (Terraform) e pipeline **já rodaram com sucesso nesta sessão** — a imagem está no ECR e o Deployment
foi aplicado no k3s. Este guia **não toca o Terraform** (rodar `apply` geraria um RDS novo e você reconfiguraria tudo).
Ele abre acesso ao cluster, garante que o app aponta para o RDS atual e expõe o backend em `localhost:8080`.

> **Alternativa mais simples:** desde que o Ingress foi adicionado, a API também responde direto em
> `http://<ip-publico>/` — a porta 80 é liberada no security group para o CIDR em `allowed_cidr` (o seu IP).
> ```powershell
> cd oficina\infra ; terraform output -raw application_url
> curl.exe "$(terraform output -raw application_url)/q/health/live"
> ```
> Se isso responder `UP`, você **não precisa** do túnel nem do port-forward abaixo — basta apontar o front
> para essa URL. O procedimento com túnel continua aqui porque é o único caminho quando o seu IP mudou (Wi-Fi
> trocado, VPN) e o security group ainda tem o CIDR antigo.

## Pré-requisitos

- **Session Manager Plugin** instalado
- Credenciais do AWS Academy **atuais** em `~/.aws/credentials` (expiram a cada sessão)
- Deps do front instaladas: em `oficina-front`, `npm install`

> `AWS_DEFAULT_REGION` **não persiste entre janelas** do PowerShell. Cada janela abaixo começa setando ela —
> não pule essa linha, é o que causa o erro `NoRegion`.

## Três janelas do PowerShell

As Janelas 1 e 2 ficam **abertas e ocupadas de propósito** (túnel e port-forward). Não feche nenhuma.

### JANELA 1 — Túnel para a API do cluster (deixe aberta)

```powershell
$env:AWS_DEFAULT_REGION = "us-east-1"
aws sts get-caller-identity   # InvalidClientTokenId => credenciais expiraram, recopie de "AWS Details"

$id = aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=running" --query "Reservations[0].Instances[0].InstanceId" --output text
Write-Host "Instance: $id"

# Confirme que a instância está registrada no SSM (tem que imprimir "Online"; se vier vazio, espere ~1 min)
aws ssm describe-instance-information --query "InstanceInformationList[?InstanceId=='$id'].PingStatus" --output text

aws ssm start-session --target $id --document-name AWS-StartPortForwardingSession --parameters "portNumber=6443,localPortNumber=6443"
```

**Espere** aparecer `Waiting for connections...` / `Port 6443 opened for sessionId ...`. Enquanto essa mensagem estiver
na tela, o túnel está de pé. **Não feche esta janela.**

> A porta 6443 nunca é aberta no security group — nem para o seu IP. Este túnel é o único caminho de
> acesso à API do Kubernetes, e é o mesmo que a pipeline usa.

### JANELA 2 — Kubeconfig, apontar o app para o RDS atual e expor o backend

```powershell
$env:AWS_DEFAULT_REGION = "us-east-1"
cd C:\Users\victo\Desktop\Projetos\tech-challenge-fiap\oficina\infra
$id = aws ec2 describe-instances --filters "Name=tag:Project,Values=oficina" "Name=instance-state-name,Values=running" --query "Reservations[0].Instances[0].InstanceId" --output text

# 1) Baixar o kubeconfig via SSM e reescrever para usar o túnel local (127.0.0.1:6443)
$cmd = aws ssm send-command --instance-ids $id --document-name AWS-RunShellScript --parameters "commands=cat /home/ec2-user/.kube/config" --query "Command.CommandId" --output text
do { Start-Sleep 3; $st = aws ssm get-command-invocation --command-id $cmd --instance-id $id --query "Status" --output text } while ($st -eq "Pending" -or $st -eq "InProgress")
aws ssm get-command-invocation --command-id $cmd --instance-id $id --query "StandardOutputContent" --output text | Out-File -Encoding ascii kubeconfig-raw
(Get-Content kubeconfig-raw) -replace 'server: https://[^:]*:6443','server: https://127.0.0.1:6443' | Set-Content kubeconfig
$env:KUBECONFIG = "$PWD\kubeconfig"
kubectl get nodes   # tem que aparecer "Ready"; se der "connection refused 127.0.0.1:6443", a Janela 1 não está de pé

# 2) Garantir que o DB_HOST no cluster é o RDS desta sessão (idempotente; não mexe no Terraform)
$rds = terraform output -raw rds_endpoint
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($rds))
"{`"data`":{`"DB_HOST`":`"$b64`"}}" | Set-Content patch.json -Encoding ascii
kubectl patch secret oficina-secrets -n oficina --type=merge --patch-file patch.json

# 3) Reiniciar o app e esperar ficar pronto (Flyway roda no boot; RDS novo = banco vazio, seeds são criados)
kubectl rollout restart deployment/oficina-app -n oficina
kubectl rollout status deployment/oficina-app -n oficina --timeout=300s

# 4) Expor o backend em localhost:8080 (deixe esta janela aberta rodando)
kubectl port-forward -n oficina svc/oficina-service 8080:80
```

### JANELA 3 — Validar o backend e subir o front

```powershell
curl.exe http://localhost:8080/q/health/live    # espera {"status":"UP",...}

cd C:\Users\victo\Desktop\Projetos\tech-challenge-fiap\oficina-front
npx ng serve --port 3000
```

Abra **http://localhost:3000/login**.

- Usuário `admin` — senha = valor do secret `APP_SEED_ADMIN_PASSWORD`
- Usuário `mecanico` — senha = valor do secret `APP_SEED_MECHANIC_PASSWORD`

O front aponta fixo para `http://localhost:8080` (o port-forward da Janela 2), e o CORS do backend libera
`localhost:3000`.

> **Aprovação de orçamento neste ambiente:** não há Mailpit. O código de autorização vai para o SMTP real
> configurado nos secrets `MAILER_*` — é preciso ter acesso à caixa do e-mail cadastrado no cliente. Sem
> SMTP funcionando, use o canal administrativo (`PATCH /admin/work-orders/{id}/approve`), que registra a
> decisão tomada presencialmente e não exige código.

## Observar o cluster

```powershell
kubectl get pods,hpa,svc,ingress -n oficina
kubectl top pods -n oficina        # se retornar erro, o metrics-server não está pronto e o HPA não escala
kubectl get hpa -n oficina -w      # acompanhar o autoscaling
```

## Se algo falhar (erros que já aconteceram)

| Erro                                                                        | Causa                                                                                       | Correção                                                                                           |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `NoRegion: You must specify a region`                                       | `AWS_DEFAULT_REGION` não setado nesta janela                                                | Rode `$env:AWS_DEFAULT_REGION = "us-east-1"` no topo da janela                                     |
| `InvalidClientTokenId` / STS 403                                            | Credenciais do Academy expiraram                                                            | Recopie as 3 credenciais de "AWS Details", confirme com `aws sts get-caller-identity`              |
| `kubectl`: `connection refused 127.0.0.1:6443`                              | Túnel da Janela 1 caiu ou não subiu                                                         | Volte à Janela 1, confirme `Port 6443 opened`; se voltou ao prompt, rode o `start-session` de novo |
| `kubectl`: `timeout <IP público>:6443`                                      | kubeconfig apontando pro IP público (não pro túnel)                                         | Confirme o `-replace ... 127.0.0.1:6443` e o `$env:KUBECONFIG` na Janela 2                         |
| `TargetNotConnected` no start-session                                       | Agente SSM ainda registrando após o apply                                                   | Espere ~1 min; confirme `describe-instance-information` imprime `Online`                           |
| Pod em `CrashLoopBackOff`                                                   | DB_HOST/segredo errado                                                                      | Repita os passos 2+3 da Janela 2; cheque `kubectl logs -n oficina -l app=oficina-app --tail=50`    |
| Pod não inicia: `container has runAsNonRoot and image has non-numeric user` | Imagem antiga, anterior ao `USER 1001` numérico                                             | Rebuild e push da imagem pela pipeline                                                             |
| Curl no IP público dá timeout                                               | Seu IP mudou e não bate mais com `allowed_cidr`                                             | `terraform apply -var="allowed_cidr=<seu-ip>/32"`, ou use o túnel + port-forward                   |
| Login no front: `NetworkError` / CORS bloqueado, preflight `403`            | ConfigMap no cluster sem `localhost:3000` em `CORS_ALLOWED_ORIGINS`                         | Ver bloco "CORS" abaixo                                                                            |
| Login: `401` mesmo com a senha certa do secret                              | `admin`/`mecanico` já existem com hash de senha **antiga** — o seed só cria quem não existe | Ver bloco "Resetar senha" abaixo                                                                   |

### CORS (front local ↔ backend no cluster)

O manifesto [`k8s/configmap.yaml`](../k8s/configmap.yaml) já inclui `http://localhost:3000`. Se o ConfigMap **rodando no
cluster** for de uma versão anterior (preflight volta `403`), corrija na Janela 2:

```powershell
'{"data":{"CORS_ALLOWED_ORIGINS":"https://oficina.example.com,http://localhost:3000"}}' | Set-Content cors-patch.json -Encoding ascii
kubectl patch configmap oficina-config -n oficina --type=merge --patch-file cors-patch.json
kubectl rollout restart deployment/oficina-app -n oficina
kubectl rollout status deployment/oficina-app -n oficina --timeout=300s
```

Depois do rollout, **reabra o port-forward** (ele cai quando os pods são recriados):

```powershell
kubectl port-forward -n oficina svc/oficina-service 8080:80
```

### Resetar senha do admin/mecanico (401 mesmo com o secret certo)

O [`DataSeeder`](../src/main/java/br/com/oficina/infrastructure/security/DataSeeder.java) só cria
`admin`/`mecanico` quando eles **não existem**. Se já foram seedados num boot anterior com outra senha, corrigir
`APP_SEED_ADMIN_PASSWORD` e reiniciar o pod **não faz nada** — o código vê que o usuário existe e sai sem tocar na
senha. Sintoma: `POST /auth/login` devolve `401` mesmo com a senha do secret conferida.

Diagnóstico (não muda nada):

```powershell
kubectl logs -n oficina -l app=oficina-app --tail=100 | Select-String -Pattern "Usuário inicial|SEED"
```

Nenhuma linha = o boot atual não seedou nada, os usuários já existiam.

Correção — apagar os usuários e deixar o app recriá-los com a senha **atual** do secret. Não precisa de SSH nem `psql`
local: sobe um pod descartável dentro do cluster (mesma VPC do RDS).

```powershell
$rdsEndpoint = terraform output -raw rds_endpoint
kubectl run psql-tmp --rm -it --restart=Never --namespace oficina --image=postgres:16-alpine -- psql -h $rdsEndpoint -U oficina_admin -d oficina_db
```

Pede senha — **é a senha do banco (`TF_VAR_db_password` / secret `DB_PASSWORD`), não a
`APP_SEED_ADMIN_PASSWORD`** (são credenciais diferentes, apesar do nome parecido). No prompt `oficina_db=>`:

```sql
DELETE
FROM app_users
WHERE username IN ('admin', 'mecanico');
\q
```

> A tabela é `app_users`, no plural (`V1__initial_schema.sql`). `app_user` no singular devolve
> `relation "app_user" does not exist`.

Reinicie o app para rodar o seed de novo:

```powershell
kubectl rollout restart deployment/oficina-app -n oficina
kubectl rollout status deployment/oficina-app -n oficina --timeout=300s
kubectl logs -n oficina -l app=oficina-app --tail=50 | Select-String -Pattern "Usuário inicial|SEED"
```

Espere `Usuário inicial criado: admin (ADMIN)` e `... mecanico (MECHANIC)`. Se aparecer
`[SEED] Senha não configurada`, o campo está vazio no secret — confira com:

```powershell
$b64 = kubectl get secret oficina-secrets -n oficina -o jsonpath='{.data.APP_SEED_ADMIN_PASSWORD}'
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64))
```

Depois do restart, reabra o `port-forward` (cai quando o pod é recriado).

---

## Fim de sessão (custo!)

Só se aplica ao **Caminho B**. O Caminho A não gera custo nenhum.

Antes de "End Lab", em `oficina\infra`:

```powershell
terraform destroy -auto-approve
```

Checklist completo de recursos órfãos em [`SESSION-GUIDE.md`](SESSION-GUIDE.md).
