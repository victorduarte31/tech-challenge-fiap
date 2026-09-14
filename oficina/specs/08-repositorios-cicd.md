# E0 — Quatro repositórios, proteção de branch e CI/CD

> Atende R4 e R5. É a **primeira** etapa executada: todas as demais assumem que o repositório de
> destino já existe com pipeline e proteção configuradas.

## 1. Os quatro repositórios

| # | Repositório | Conteúdo | Origem no monorepo atual |
|---|---|---|---|
| 1 | `oficina-auth-lambda` | Função de autenticação por CPF + API Gateway (Terraform) + authorizer | novo |
| 2 | `oficina-infra-k8s` | VPC, EC2/k3s, ECR, SGs, agentes de observabilidade (Terraform) | `oficina/infra/` (menos RDS) |
| 3 | `oficina-infra-database` | RDS PostgreSQL, subnet group, parameter group, SG (Terraform) | `oficina/infra/rds.tf` |
| 4 | `oficina-app` | Aplicação Quarkus, Dockerfile, manifestos K8s, migrations | `oficina/` (menos `infra/`) |

O monorepo `tech-challenge-fiap` permanece como **arquivo histórico**, com README apontando para os
quatro novos e um aviso de que não recebe mais commits. `oficina-front/` continua nele — está fora do
escopo desta fase.

### Split preservando histórico

O histórico importa: é a evidência de evolução das fases 1 e 2. `git filter-repo` (preferido) ou
`git subtree split` (disponível em qualquer instalação do Git):

```bash
# Exemplo para a aplicação — repetir por repositório com o --path adequado
git clone https://github.com/victorduarte31/tech-challenge-fiap.git oficina-app
cd oficina-app
git filter-repo --path oficina/ --path-rename oficina/:            # sobe o conteúdo à raiz
git filter-repo --invert-paths --path infra/                       # remove o que vai para outro repo
git remote add origin https://github.com/<org>/oficina-app.git
git push -u origin master:main
```

Cuidados: `git filter-repo` recusa rodar em clone com remote configurado (por isso `remote add` vem
depois); e a chave `infra/k3s-key.pem` e o `kubeconfig` **que estão versionados hoje** devem ser
removidos do histórico no mesmo passo (`--invert-paths --path infra/k3s-key.pem --path infra/kubeconfig`)
— são credenciais de uma EC2 já destruída, mas material de credencial não deve sobreviver ao split.
`terraform.tfstate` e `tfplan` na raiz também saem.

Renomear a branch padrão para `main` no split, já que o PDF fala em "main/master protegida" e `main` é
o padrão atual do GitHub.

## 2. Estrutura de branches

```
main       produção   — protegida, só recebe merge de PR, deploy automático
homolog    homologação— protegida, só recebe merge de PR, deploy automático
feature/*  trabalho   — livre
```

Fluxo: `feature/x` → PR para `homolog` → valida em homologação → PR de `homolog` para `main`.

## 3. Proteção de branch (GitHub Rulesets, em `main` e `homolog`)

- Bloquear push direto e force-push; bloquear exclusão da branch.
- Exigir Pull Request com **1 aprovação**; descartar aprovações obsoletas a cada novo push.
- Exigir *status checks* aprovados antes do merge — por repositório:
  - `oficina-app`: `build-test`, `docker-build`
  - `oficina-auth-lambda`: `build-test`, `terraform-check`
  - `oficina-infra-*`: `validate`, `plan`
- Exigir que a branch esteja atualizada com a base.
- Exigir histórico linear (merge por squash) — mantém o histórico legível depois do split.
- **Sem bypass para administradores.** Numa equipe de um só, "administrador pode furar a regra"
  significa que a regra não existe.

### Visibilidade: os quatro repositórios são públicos

Decidido, e não é preferência estética — é o que torna R5 exequível sem plano pago. No plano Free do
GitHub, a linha de corte é a visibilidade do repositório:

| Recurso | Repo público (Free) | Repo privado (Free) |
|---|---|---|
| Rulesets / proteção de branch (**R5**) | ✅ | ❌ exige plano pago |
| Environments com *required reviewer* | ✅ | ❌ exige plano pago |
| Organization secrets | ✅ | ❌ exige Team/Enterprise |
| Minutos de GitHub Actions | ilimitados | 2.000/mês divididos entre os 4 repos |

Com 4 repositórios, builds Maven, `terraform plan` em cada PR e scan de imagem, 2.000 minutos/mês
apertariam antes do fim da fase. Público resolve proteção de branch, segredos compartilhados, minutos
e o acesso do avaliador `soat-architecture` de uma vez.

**Pré-condição bloqueante:** o histórico precisa estar limpo antes de qualquer `git push` (§1).
Repositório público com `k3s-key.pem` ou `kubeconfig` no histórico é incidente de segurança, mesmo
com a EC2 correspondente já destruída. Verificação obrigatória antes de publicar:

```bash
git log --all --full-history --name-only --pretty=format: \
  | sort -u | grep -Ei 'key\.pem|kubeconfig|\.tfstate|tfplan|\.env' || echo "histórico limpo"
```

## 4. Deploy automático por ambiente

| Branch | GitHub Environment | Alvo |
|---|---|---|
| `homolog` | `homologacao` | workspace `hml` → cluster de homologação, overlay `hml` |
| `main` | `producao` | workspace `prod` → cluster de produção, overlay `prod` |

`producao` com **required reviewer** (o próprio autor aprova no momento do deploy). Isso não fere
"deploy automático": o gatilho é automático; a aprovação é o freio de custo que impede um merge
noturno de subir uma stack AWS que ficará ligada sem ninguém olhando. A justificativa vai no README.

Cada Environment guarda seus próprios segredos — a senha do banco de homologação nunca alcança um job
de produção, e vice-versa.

## 5. Segredos e o problema das credenciais temporárias

A conta AWS Academy emite credenciais STS que **expiram a cada sessão** e precisam ser recolocadas em
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN`. Com 4 repositórios × 2 ambientes,
são 24 valores a atualizar por sessão se feito à mão — erro garantido, e tempo caro numa sessão de 4h.

**Decidido: organização GitHub gratuita**, com os 4 repositórios públicos dentro dela. Segredos de
organização são definidos **uma vez** e visíveis para os 4 repositórios — grátis, porque os repos são
públicos (§3). Não há OIDC possível aqui: criar um Identity Provider é
`iam:CreateOpenIDConnectProvider`, bloqueado no Academy.

A organização não é imprescindível — o script abaixo já resolve a maior parte da dor mesmo com
segredos por repositório. O que ela agrega: ponto único de atualização das credenciais STS, permissão
do avaliador concedida uma vez no nível da organização, e uma apresentação mais coerente dos quatro
repositórios como um sistema só.

Script de apoio (roda em segundos, com ou sem organização):

```bash
# Cola as credenciais da sessão em todos os repositórios/ambientes
for repo in oficina-app oficina-auth-lambda oficina-infra-k8s oficina-infra-database; do
  for env in homologacao producao; do
    gh secret set AWS_ACCESS_KEY_ID     --repo "$ORG/$repo" --env "$env" --body "$AWS_ACCESS_KEY_ID"
    gh secret set AWS_SECRET_ACCESS_KEY --repo "$ORG/$repo" --env "$env" --body "$AWS_SECRET_ACCESS_KEY"
    gh secret set AWS_SESSION_TOKEN     --repo "$ORG/$repo" --env "$env" --body "$AWS_SESSION_TOKEN"
  done
done
```

Matriz de segredos:

| Segredo | Nível | Repositórios |
|---|---|---|
| `AWS_ACCESS_KEY_ID` / `_SECRET_ACCESS_KEY` / `_SESSION_TOKEN` | organização (ou env) | todos |
| `ALLOWED_CIDR` | organização | infra-k8s |
| `DB_PASSWORD`, `DB_USERNAME` | environment | infra-database, app |
| `LAMBDA_RO_PASSWORD` | environment | app (placeholder Flyway), auth-lambda |
| `JWT_PRIVATE_KEY_PEM` / `JWT_PUBLIC_KEY_PEM` | environment | app, auth-lambda |
| `GATEWAY_KEY` | environment | app, auth-lambda |
| `NEW_RELIC_LICENSE_KEY` | organização | app, infra-k8s, auth-lambda |
| `MAILER_*`, `APP_SEED_*` | environment | app |

Chaves JWT e `GATEWAY_KEY` aparecem em dois repositórios porque são um **contrato compartilhado** —
emissor e validador precisam do mesmo material. Gerá-los uma vez por sessão com `generate-keys.sh` e
distribuí-los pelo script acima.

## 6. Pipelines por repositório

Todas herdam os padrões já provados na Fase 2: `permissions: contents: read`, `concurrency` por ref,
cache do Maven, upload de relatórios de teste, scan Trivy antes do push, rollback automático.

**`oficina-app`**

```
build-test         → mvn verify + JaCoCo gate            (PR e push)
docker-build       → build + Trivy (CRITICAL, fixed) + push ECR   (homolog/main)
deploy             → kustomize build overlay | kubectl apply via túnel SSM
                     rollout status + rollback automático
smoke-test         → /q/health via Service e via Ingress; login e uma rota protegida
                     via API Gateway (valida a cadeia inteira)
```

**`oficina-auth-lambda`**: `build-test` (unit + Testcontainers) → `terraform-check` → `package` →
`deploy` (apply + publish-version + alias) → `smoke` (invoca com CPF válido e inválido).

**`oficina-infra-k8s` / `oficina-infra-database`**: `validate` → `plan` (comenta no PR) → `apply`
(por branch) → `destroy` (`workflow_dispatch`).

A composite action de acesso ao cluster via SSM (`.github/actions/k3s-kubeconfig`) é copiada para
`oficina-app` — único repositório que precisa dela. Publicá-la como quinto repositório contrariaria a
exigência de exatamente quatro.

## 7. Ordem de bootstrap (por sessão)

Dependência real entre pipelines; o runbook completo está em [10](10-runbook-sessao-custos.md).

```
1. gh secret set ... (credenciais da sessão)
2. oficina-infra-k8s      → apply    (publica VPC, EIP, ECR em SSM)
3. oficina-infra-database → apply    (consome VPC, publica endpoint)
4. oficina-app            → deploy   (imagem + migrations + pods)
5. oficina-auth-lambda    → deploy   (função + gateway, consome EIP e endpoint)
6. validações e demo
7. destroy em 5 → 4 → 3 → 2 (ordem inversa)
```

## 8. README de cada repositório (exigência literal do PDF)

Seções obrigatórias, iguais nos quatro: propósito; tecnologias; pré-requisitos; passos de execução
local; passos de deploy; **diagrama da arquitetura específica daquele repositório** (Mermaid,
renderizado pelo GitHub, custo zero); link do Swagger/Postman; link do deploy ativo quando houver;
tabela de custo estimado; aviso de `destroy`. Template em [09](09-documentacao-arquitetural.md).

## 9. Critério de aceite

- Os 4 repositórios existem, **públicos, sob a organização**, com histórico preservado e sem material
  de credencial no histórico (comando de verificação de §3 retorna "histórico limpo") — nesta ordem:
  limpar, verificar, publicar.
- Um *organization secret* definido uma única vez é lido com sucesso por um workflow dos 4
  repositórios.
- Push direto em `main` é recusado pelo servidor; PR sem check verde não permite merge.
- Um merge em `homolog` dispara deploy em homologação sem intervenção; um merge em `main` dispara o de
  produção após a aprovação do ambiente.
- Rodar o script de segredos e, na sequência, as 4 pipelines na ordem, produz um ambiente funcional a
  partir do zero.
- `soat-architecture` com acesso aos 4 repositórios *(execução do usuário)*.

## 10. Fora de escopo

- Monorepo com pipelines por path — a fase exige explicitamente quatro repositórios.
- OIDC entre GitHub e AWS (bloqueado no Academy).
- Assinatura de imagem (cosign), SBOM, ambiente efêmero por PR: bons próximos passos, registrados como
  evolução futura no ADR de CI/CD.
