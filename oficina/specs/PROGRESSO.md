# Progresso da Fase 3

> Fonte única do estado do projeto. Ler no início da sessão; atualizar ao final.
> Última atualização: **2026-09-14** (sessão 1 — E0).

## Etapas (spec 00 §4)

| Etapa | Spec | Status | Sessão |
|---|---|---|---|
| E0 Split dos repositórios + proteção + pipelines | 08 | ✅ concluída (pendências abaixo) | 1 |
| E1 Modelagem do banco (V5, `clients.status`) | 01 | ⬜ próxima | — |
| E2 Infra K8s (Terraform, `oficina-infra-k8s`) | 06 | ⬜ | — |
| E3 Infra DB (Terraform, `oficina-infra-database`) | 07 | ⬜ | — |
| E4 App: role CLIENT, ownership, correlação, métricas | 04 | ⬜ | — |
| E5 Lambda de autenticação por CPF | 02 | ⬜ | — |
| E6 API Gateway + authorizer | 03 | ⬜ | — |
| E7 Observabilidade fim-a-fim (New Relic) | 05 | ⬜ | — |
| E8 Documentação arquitetural (ADRs, diagramas) | 09 | ⬜ | — |
| E9 Runbook de sessão e custos | 10 | ⬜ | — |

## Próximo passo

**E1 — spec 01**, em `Projetos/oficina-app`, branch `feature/e1-modelagem-banco` a partir de `main`.
Não depende das pendências abaixo (não toca a AWS).

## Pendências (do usuário)

- [ ] Mergear [oficina-infra-k8s#1](https://github.com/victor-duarte-mendonca/oficina-infra-k8s/pull/1) — script `scripts/set-aws-session-secrets.sh` + README
- [ ] Mergear [tech-challenge-fiap#8](https://github.com/victorduarte31/tech-challenge-fiap/pull/8) — aviso de arquivo no monorepo + este arquivo + `CLAUDE.md`
- [ ] Rodar `set-aws-session-secrets.sh` com as credenciais da sessão Academy — **só necessário antes de E2** (primeiro `terraform apply`)
- [ ] Adicionar `soat-architecture` na org `victor-duarte-mendonca` (pode ficar para o fim da fase)
- [ ] Validações de primeira sessão AWS (spec 03 §8 e spec 00 §6): `apigateway:*` liberado? `LabRole` com trust para `lambda.amazonaws.com`? — fazer junto com E2

## O que existe hoje (E0)

**Repositórios** — org `victor-duarte-mendonca`, públicos, histórico das Fases 1-2 preservado, sem material de credencial (`kubeconfig-raw` e `patch.json` removidos do histórico):

| Repo | Conteúdo atual | Pipeline (`main`/`homolog`, PR) | Checks obrigatórios |
|---|---|---|---|
| `oficina-app` | app Quarkus completa (raiz = antigo `oficina/`), `.github/actions/k3s-kubeconfig`, `specs/` | `ci.yml` | `build-test`, `docker-build` (Trivy CRITICAL/fixed) |
| `oficina-infra-k8s` | `oficina/infra/` sem `rds.tf` e sem output `rds_endpoint`; `.gitignore`; PR#1 adiciona script + README | `terraform.yml` | `validate`, `plan` (plan só roda com secrets AWS) |
| `oficina-infra-database` | só `rds.tf` + README + `.gitignore` — módulo incompleto até E3 | `terraform.yml` (validate = só `fmt`) | `validate`, `plan` |
| `oficina-auth-lambda` | README + `.gitignore` | `ci.yml` placeholder | `build-test`, `terraform-check` |

**Proteção (nos 4)**: ruleset `protecao-main-homolog` em `main` e `homolog` — sem push direto, sem
force-push, sem delete, PR obrigatório, histórico linear (squash), checks obrigatórios com branch
atualizada, **sem bypass**. Repo: só squash merge, apaga branch após merge.

**Environments (nos 4)**: `homologacao` (só branch `homolog`) e `producao` (só `main`, *required
reviewer* = Victor, self-review permitido). Sem secrets definidos ainda.

**Monorepo**: `master` em `1930b30` (PR#7 mergeado: specs + RUN-LOCAL + ignore de `oficina-front/`).
`oficina-front/` é projeto de teste visual, fora do escopo, ignorado.

## Decisões tomadas na execução (complementam as specs)

| Decisão | Motivo |
|---|---|
| Ruleset com **0 aprovações** (spec pedia 1) | GitHub não deixa o autor aprovar o próprio PR; equipe de um travaria. Subir para 1 quando houver segundo revisor (`gh api -X PUT repos/…/rulesets/<id>`) |
| Pipelines commitadas direto em `main` **antes** do ruleset | Checks precisam existir para virarem obrigatórios; depois disso tudo via PR |
| `plan` roda só se `AWS_ACCESS_KEY_ID` existir; senão passa com `::notice` | Sem credenciais o job falharia em todo PR; skip contaria como sucesso de qualquer forma |
| `validate` do `infra-database` é só `fmt` até E3 | `rds.tf` sozinho referencia SG/subnets/vars inexistentes |
| netty `4.1.135` → `4.1.137.Final` em `oficina-app` | CVE-2026-75595 CRITICAL pego pelo gate Trivy na primeira run |
| Script de segredos vive em `oficina-infra-k8s/scripts/` | É o passo 1 do bootstrap; infra-k8s é o passo 2 |
| Composite action `k3s-kubeconfig` ficou em `oficina-app` | Único repo que precisa dela; quinto repo violaria "exatamente quatro" |

## Histórico de sessões

- **2026-09-14 (sessão 1)** — E0 completa: instalação de `gh`/Python/`git-filter-repo`, org criada,
  split de 4 repos com `git filter-repo`, pipelines, rulesets, environments, PRs #1 (infra-k8s) e #8 (monorepo).
