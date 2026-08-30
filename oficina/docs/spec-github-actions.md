## 7. CI/CD (GitHub Actions)

### Dependências

Requer `spec-terraform-aws.md` (gate de aprovação de custo já cumprido — o job `terraform-apply` desta
pipeline não contorna esse gate, apenas automatiza a execução já aprovada; requer também o bootstrap do
backend S3 já feito, ver spec) e `spec-kubernetes.md` (manifestos a aplicar existem). Requer
`spec-test-unit.md` para o job `build-test` ter o que rodar.

### Skill de Apoio

`github-actions-docs` — consultar ao escrever o YAML final, especificamente para confirmar a sintaxe vigente
de triggers e `workflow_dispatch`. Sintaxe do GitHub Actions muda com frequência suficiente para que confiar
em memória de treinamento aqui seja risco real de pipeline quebrada por sintaxe obsoleta, não por erro de
lógica.

### Contexto — por que esta pipeline não é "dispara sozinha" em cada push

Diferente do desenho original (Azure, com OIDC), esta pipeline **não pode** autenticar contra a AWS de forma
totalmente automática. Dois motivos, ambos decorrentes da conta AWS Academy (detalhados em
`spec-terraform-aws.md`, seção "Contexto — restrições da conta"):

1. **Sem OIDC.** Federação OIDC exige criar um Identity Provider IAM (`aws_iam_openid_connect_provider`) e um
   role assumível por ele — ambos são criação de recurso IAM, bloqueada na conta Academy. A alternativa é
   autenticação por credenciais estáticas (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_SESSION_TOKEN`)
   armazenadas como GitHub Secrets.
2. **Credenciais expiram com a sessão.** As credenciais copiadas do painel "AWS Details" do Academy são
   temporárias (STS) e páram de funcionar quando a sessão termina (~4h ou "End Lab"). Diferente de uma
   credencial de IAM user tradicional, **não dá para configurar uma vez e esquecer** — precisam ser
   atualizadas nos GitHub Secrets a cada nova sessão em que a pipeline for disparada.

Consequência de design: **`build-test` roda automaticamente em todo push/PR** (não precisa de credencial
AWS). **Os demais jobs (`docker-build-push`, `terraform-apply`, `deploy-k8s`, `smoke-test`) só rodam via
`workflow_dispatch`** (disparo manual), nunca automaticamente em push — porque uma execução automática
encontraria, na maior parte do tempo, credenciais expiradas e falharia sem motivo real de código. Isso não é
uma limitação do desenho da pipeline, é reflexo direto da natureza sandbox/temporária da conta — documentado
aqui para não ser confundido com um pipeline "incompleto".

Isso também é suficiente para o critério de aceite do desafio: o PDF pede que o vídeo demonstre "Execução do
CI/CD" — uma execução disparada manualmente, com credenciais frescas, cumpre isso integralmente. Não é
exigido que a pipeline rode de forma autônoma e perene contra uma conta que, por natureza, não sustenta isso.

### Contrato

Arquivo `.github/workflows/ci-cd.yml`, jobs na seguinte ordem e dependência:

1. **build-test** (`on: push`, `pull_request` — automático): `mvn verify` — compila, roda testes, valida
   gate JaCoCo de 80%. Falha aqui interrompe a pipeline; nenhum job seguinte roda.
2. **docker-build-push** (`workflow_dispatch`): build da imagem multi-stage, push para o ECR provisionado em
   `spec-terraform-aws.md`. Autenticação via `aws-actions/configure-aws-credentials@v4` com
   `aws-access-key-id`/`aws-secret-access-key`/`aws-session-token` vindos de GitHub Secrets (colados pelo
   aluno a partir do "AWS Details" do Academy antes de disparar o workflow — ver checklist de sessão), depois
   `aws ecr get-login-password | docker login`.
3. **terraform-apply** (`workflow_dispatch`, nunca automático): `terraform init` (usa o backend S3 já
   bootstrapado, ver `spec-terraform-aws.md`) `/plan/apply` em `/infra`, mesmas credenciais temporárias do job
   anterior. Este job assume que o gate de custo de `spec-terraform-aws.md` já foi cumprido manualmente antes
   da primeira execução — a pipeline não decide orçamento, só executa o que já foi aprovado.
4. **deploy-k8s** (`workflow_dispatch`, `needs: [docker-build-push, terraform-apply]`): sem equivalente a
   `az aks get-credentials` — o kubeconfig do k3s é buscado via SSH na EC2 (`scp` de
   `/etc/rancher/k3s/k3s.yaml`, com o IP substituído pelo output `k3s_public_ip` do Terraform) e usado como
   `KUBECONFIG` para os `kubectl` seguintes. Regenera `ecr-registry-secret` (token de 12h, ver
   `spec-kubernetes.md`), aplica `secret.yaml` e regenera `oficina-jwt-keys` (par de chaves fixo, ver
   `spec-kubernetes.md` — necessário a cada sessão porque a EC2/cluster é recriado do zero) via `kubectl
   create secret --dry-run=client -o yaml | kubectl apply -f -`, populados a partir de GitHub Secrets — nunca
   commitados com valor real em nenhum momento do histórico do repositório. **Pré-requisito de rede:** o security group da EC2 (`spec-terraform-aws.md`)
   restringe a porta 6443 a `var.allowed_cidr` (IP do aluno) — runners hospedados do GitHub têm IP dinâmico e
   não alcançam a API do k3s por padrão. Antes de disparar este job, o aluno amplia temporariamente a regra
   (ex.: `terraform apply -var="allowed_cidr=0.0.0.0/0"` só para a janela do teste, ou restringe ao range de
   IPs do runner do momento) e reverte depois — documentar essa etapa manual no `README` do workflow, não
   escondê-la.
5. **smoke-test** (`workflow_dispatch`, `needs: deploy-k8s`): `curl` no health endpoint (via
   `kubectl port-forward` a partir do runner, mesma janela de rede do job anterior), validando que o rollout
   do deploy anterior está efetivamente respondendo.

### Critério de Aceite

- `build-test` roda automaticamente em push/PR; os demais jobs só via `workflow_dispatch`.
- Pipeline falha e interrompe (não segue para os próximos jobs) se `mvn verify` falhar.
- Nenhum secret real aparece em log de execução do workflow (validar com uma execução de teste e inspeção do
  log) — inclui `AWS_SESSION_TOKEN`, que por ser temporário costuma ser tratado com menos cuidado do que
  `AWS_SECRET_ACCESS_KEY`, mas é igualmente sensível enquanto válido.
- `terraform-apply` não dispara automaticamente em push para nenhuma branch, nem em pull request — só
  `workflow_dispatch` explícito.
- `deploy-k8s` aplica `secret.yaml` sem que o valor populado seja visível em texto plano no log do job.
- `smoke-test` falha a pipeline (status vermelho) se o health endpoint não responder 2xx dentro do timeout
  definido — não é um job decorativo, é gate de rollout.
- README do workflow documenta explicitamente o passo manual de credenciais (colar `AWS_ACCESS_KEY_ID`/
  `AWS_SECRET_ACCESS_KEY`/`AWS_SESSION_TOKEN` frescos antes de cada `workflow_dispatch`) e o passo manual de
  abertura temporária do security group para `deploy-k8s`/`smoke-test`.

### Fora de Escopo

- Rollback automático de deploy em caso de smoke-test falho — não está definido nesta spec; se for desejado,
  é uma spec própria, separada.
- Ambientes de staging/homologação — pipeline cobre apenas o fluxo direto para o ambiente único definido em
  `spec-terraform-aws.md`.
- Execução autônoma e perene de `terraform-apply`/`deploy-k8s` sem intervenção manual de credenciais — não é
  alcançável dentro das restrições da conta AWS Academy (ver Contexto acima); não é uma lacuna a fechar, é um
  limite estrutural da conta.
- Federação OIDC — bloqueada por restrição de IAM da conta Academy, ver `spec-terraform-aws.md`.

### Evolução futura (fora do escopo acadêmico atual)

Se este projeto migrar para uma conta AWS própria (não Academy) no futuro, os dois limites acima somem: OIDC
passa a ser viável (habilita `terraform-apply`/`deploy-k8s` automáticos em `main`) e um self-hosted runner
rodando na própria EC2 do k3s (IAM via instance profile, sem necessidade de colar credenciais, e sem precisar
abrir o security group para IPs de runner hospedado) resolveria o problema de rede do job `deploy-k8s` por
completo. Não implementado agora por não ser necessário — a conta Academy não sustenta esse modelo de qualquer
forma.
