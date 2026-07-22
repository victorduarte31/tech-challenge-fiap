# Deploy no k3s — Oficina Mecânica

Pré-requisito: `KUBECONFIG` apontando para o cluster k3s (ver `../infra/README.md`, seção "Buscar o
kubeconfig").

## 1. Base (namespace + config não sensível)

```bash
kubectl apply -f k8s/namespace.yaml -f k8s/configmap.yaml
```

## 2. Secret do ECR (token de 12h — regenerar se a sessão passar disso)

```bash
kubectl create secret docker-registry ecr-registry-secret \
  --namespace oficina --docker-server=$(terraform -chdir=../infra output -raw ecr_repository_url) \
  --docker-username=AWS --docker-password="$(aws ecr get-login-password --region us-east-1)"
```

No PowerShell (Windows), equivalente:
```powershell
$ecrUrl = terraform -chdir=../infra output -raw ecr_repository_url
$ecrPassword = aws ecr get-login-password --region us-east-1
kubectl create secret docker-registry ecr-registry-secret --namespace oficina --docker-server=$ecrUrl --docker-username=AWS --docker-password=$ecrPassword
```

## 3. Chaves JWT (uma vez por sessão, compartilhadas entre réplicas)

Gera localmente (nunca commitar `keys/*.pem`, já estão no `.gitignore` da raiz do projeto):

```bash
cd .. && ./generate-keys.sh && cd k8s
```

```powershell
kubectl create secret generic oficina-jwt-keys --namespace oficina --from-file=privateKey.pem=keys\privateKey.pem --from-file=publicKey.pem=keys\publicKey.pem
```

## 4. Secret da aplicação (DB + SMTP + seed)

```bash
kubectl create secret generic oficina-secrets --namespace oficina \
  --from-literal=DB_HOST=$(terraform -chdir=../infra output -raw rds_endpoint) \
  --from-literal=DB_USERNAME=oficina_admin \
  --from-literal=DB_PASSWORD='<mesma senha do TF_VAR_db_password>' \
  --from-literal=MAILER_HOST='<host SMTP>' \
  --from-literal=MAILER_USERNAME='<usuario SMTP>' \
  --from-literal=MAILER_PASSWORD='<senha SMTP>' \
  --from-literal=APP_SEED_ADMIN_PASSWORD='<senha do admin>' \
  --from-literal=APP_SEED_MECHANIC_PASSWORD='<senha da atendente>'
```

> Sem provedor SMTP configurado ainda? Pode aplicar com placeholders — falha de envio de e-mail não quebra a
> aplicação (`notifySafely`, ver `spec-api.md` 2.5), só o e-mail não sai. Ajuste depois.

## 5. Deployment, service e HPA

```bash
sed "s#CHANGE_ME_ECR_REPOSITORY_URL#$(terraform -chdir=../infra output -raw ecr_repository_url)#" k8s/deployment.yaml \
  | kubectl apply -f -
kubectl apply -f k8s/service.yaml -f k8s/hpa.yaml
```

## 6. Validar

```bash
kubectl get pods,hpa,svc -n oficina
kubectl port-forward -n oficina svc/oficina-service 8080:80
curl http://localhost:8080/q/health/live
```

Teste de carga (material do vídeo demonstrativo — HPA escalando e voltando ao mínimo). Roda **de dentro do
cluster**, não via `port-forward`: o túnel do `kubectl port-forward` é um único fluxo TCP e vira gargalo do
lado cliente antes de gerar CPU/memória suficiente nos pods, mascarando o teste.

```bash
kubectl run load-test -n oficina --rm -it --restart=Never --image=williamyeh/hey -- \
  -z 60s -c 50 http://oficina-service.oficina.svc.cluster.local/public/work-orders/OS-000001/status
```

Em outro terminal, acompanhar o HPA escalar (usa CPU e memória, o que estourar 70% primeiro dispara o
scale-out — ver `spec-kubernetes.md`):

```bash
kubectl get hpa -n oficina -w
```
