## 5. Kubernetes (`/k8s`)

### Dependências

Requer `spec-terraform-aws.md` para a existência da EC2 com k3s, do ECR e do RDS PostgreSQL (esta spec não
provisiona infraestrutura, só consome — a EC2/cluster e o banco já existem quando estes manifestos são
aplicados). Requer `spec-api.md` 2.5 para saber quais variáveis de SMTP de produção precisam existir no
`Secret`.

### Contexto

Banco de dados roda como RDS PostgreSQL gerenciado, provisionado via Terraform — não como pod no cluster.
Decisão já fechada (ver `spec-terraform-aws.md`), não reaberta aqui. O cluster em si é k3s single-node
(não EKS) — implicação prática para esta spec: **não há autoscaler de nó**. Se um pod não couber (CPU/memória
insuficiente no nó único), ele fica `Pending` indefinidamente, não é resolvido automaticamente como seria com
um node pool elástico. Os `requests`/`limits` abaixo precisam ser validados com `kubectl top nodes` /
`kubectl top pods` logo após o primeiro deploy, antes de confiar neles para o vídeo demonstrativo.

### Contrato

Manifestos necessários em `/k8s`:

- `namespace.yaml`
- `configmap.yaml`: variáveis não sensíveis — perfil Quarkus ativo, origens CORS permitidas, issuer JWT,
  `MAILER_HOST`/`MAILER_PORT` de produção (host/porta do provedor SMTP real, não Mailpit).
- `secret.yaml`: placeholders (sem valor real commitado) para connection string do RDS e credenciais SMTP
  (usuário/senha). Valores reais entram em runtime via CI/CD (ver `spec-github-actions.md`). **Não** contém
  chaves JWT — ver `oficina-jwt-keys` abaixo, motivo é estrutural, não estilístico.
- `ecr-registry-secret`: **não** é um arquivo estático versionado como os demais — é gerado sob demanda via
  `kubectl create secret docker-registry ecr-registry-secret --docker-server=<ecr_repository_url>
  --docker-username=AWS --docker-password=$(aws ecr get-login-password --region us-east-1) --namespace
  oficina --dry-run=client -o yaml | kubectl apply -f -`, porque o token do ECR expira em 12h — um `secret.yaml`
  commitado com esse valor ficaria obsoleto na sessão seguinte. Documentar o comando no README, não o valor.
- `oficina-jwt-keys`: **não** é gerado a partir de `JWT_PUBLIC_KEY_LOCATION`/`JWT_PRIVATE_KEY_LOCATION` como
  env vars em `secret.yaml` — é um `Secret` montado como **volume** em `/app/keys` no `deployment.yaml`. Motivo:
  `docker-entrypoint.sh` gera um par de chaves RSA novo a cada contêiner que sobe **se não encontrar chaves
  existentes** em `/app/keys`. Com `secret.yaml` como env var (ou sem esse secret), cada pod geraria uma chave
  própria — um token emitido pelo pod A seria inválido no pod B assim que o balanceamento de carga alternasse
  entre réplicas. O par de chaves é gerado uma vez (`generate-keys.sh` ou os mesmos comandos `openssl` do
  entrypoint) e vira `kubectl create secret generic oficina-jwt-keys --from-file=privateKey.pem
  --from-file=publicKey.pem -n oficina` — a chave privada nunca é commitada nem passa por `secret.yaml`.
- `deployment.yaml`: `readinessProbe` e `livenessProbe` apontando para `/q/health/ready` e `/q/health/live`
  respectivamente; `requests`/`limits` de CPU e memória definidos (pré-requisito funcional do HPA — sem
  `requests` de CPU, o HPA não tem base de cálculo para escalar); `imagePullSecrets` referenciando
  `ecr-registry-secret`; `volumeMounts`/`volumes` montando `oficina-jwt-keys` (read-only) em `/app/keys`;
  imagem publicada no ECR (URL vinda do output `ecr_repository_url` do Terraform, não ACR).
- `service.yaml`: `ClusterIP` para o app. Exposição pública não é necessária para o critério de aceite —
  `kubectl port-forward` cobre a demonstração em vídeo. Se exposição externa for desejada, k3s já inclui
  Traefik como ingress controller por padrão (equivalente ao App Routing addon do AKS que constava na versão
  anterior desta spec), mas isso é opcional e fora do critério de aceite.
- `hpa.yaml`: `HorizontalPodAutoscaler` (`autoscaling/v2`) com **duas métricas de recurso, CPU e memória**,
  ambas com target de 70% de utilização — não só CPU. O PDF pede escalonamento "conforme consumo de
  CPU/memória", e `autoscaling/v2` permite múltiplas métricas na mesma HPA; o controller escala pelo maior
  valor calculado entre elas, então a presença de ambas cobre o requisito literalmente, mesmo que num teste
  de carga específico só uma delas acabe sendo a que dispara o scale-out. **min 2 réplicas / max 4**. Máximo
  reduzido de 6 (valor da versão anterior desta spec, pensada para node pool elástico) para 4: em nó único
  `t3.medium` (4GB RAM), 4 réplicas com request moderado de memória cabem com folga junto dos addons do k3s
  (CoreDNS, metrics-server, Traefik); 6 réplicas simultâneas ficariam apertadas sem um segundo nó para
  absorver o excesso. Ajustar para cima só se `kubectl top nodes` confirmar headroom real após o primeiro
  deploy.

### Critério de Aceite

- `kubectl apply -f k8s/` aplica sem erro contra o cluster k3s provisionado por `spec-terraform-aws.md`.
- `kubectl get pods,hpa,svc -n oficina` mostra todos os pods em `Running` e prontos (`READY` = total de
  réplicas mínimas do HPA) — nenhum pod em `Pending` por falta de recurso no nó (ver nota de capacidade em
  Contexto).
- `kubectl get pods` confirma que os probes de readiness/liveness não estão reiniciando o pod em loop (sinal
  de healthcheck mal configurado).
- Teste de carga (`hey` ou `k6`) gerado **de dentro do cluster** — um `Job`/pod temporário chamando
  `http://oficina-service.oficina.svc.cluster.local` — não via `kubectl port-forward`. O `port-forward` é um
  único túnel TCP proxied pelo `kubectl`; ele vira gargalo do lado cliente antes de gerar CPU/memória
  suficiente nos pods para estourar 70%, mascarando o resultado do teste. O número de réplicas reportado por
  `kubectl get hpa` deve aumentar sob carga e retornar ao mínimo após a carga cessar. Este teste é o material
  que o vídeo demonstrativo precisa capturar.
- Nenhum valor sensível real aparece em `secret.yaml` versionado no repositório — apenas placeholders ou
  referência a `kubectl create secret --dry-run=client` (ver `spec-github-actions.md`).
- `ecr-registry-secret` é gerado via comando documentado, nunca commitado com valor real.
- Um token emitido via `/auth/login` num pod é aceito por qualquer outra réplica (valida que
  `oficina-jwt-keys` está montado e sendo usado, não gerado por pod) — testável fazendo login, anotando o
  pod que respondeu (`kubectl get pods -o wide`), e chamando um endpoint autenticado várias vezes até a
  resposta vir de um pod diferente.

### Fora de Escopo

- Provisionamento da EC2/k3s, ECR ou banco — isso é `spec-terraform-aws.md`.
- Configuração de SMTP local/dev (Mailpit) — isso é `spec-docker.md`. Este arquivo trata exclusivamente do
  ambiente de produção no cluster k3s.
- Ingress/TLS público via Traefik — mencionado como opção disponível no k3s, mas não é critério de aceite
  desta fase.
