# Tech Challenge FIAP — Fase 3

Este monorepo é **arquivo histórico** (Fases 1 e 2) e guarda as **specs** da Fase 3 em `oficina/specs/`.
O código da Fase 3 vive em 4 repositórios da org GitHub `victor-duarte-mendonca`, clonados em
`C:\Users\victo\Desktop\Projetos\{oficina-app,oficina-auth-lambda,oficina-infra-k8s,oficina-infra-database}`.

## Início de sessão (obrigatório)

1. Ler `oficina/specs/PROGRESSO.md` — estado atual, pendências e próximo passo. **Não** varrer o
   repositório para redescobrir o que já foi feito.
2. Ler apenas a spec da etapa em curso (`oficina/specs/NN-*.md`), não todas.

## Fim de sessão (obrigatório)

Atualizar `oficina/specs/PROGRESSO.md`: mover itens concluídos, registrar decisões tomadas e
pendências novas, ajustar o "Próximo passo". Commitar na branch de trabalho do monorepo.

## Convenções

- Ordem das etapas: spec `00-visao-geral.md` §4 (E0 → E9). Não pular dependências.
- Cada repo novo: branches `main`/`homolog` protegidas (PR + squash + checks). Merges são feitos
  **pelo usuário** — Claude abre o PR e informa o link.
- Ferramentas no Windows (fora do PATH do shell da sessão — usar caminho completo):
  `gh` → `C:\Program Files\GitHub CLI\gh.exe`;
  `git filter-repo` → `%LOCALAPPDATA%\Programs\Python\Python312\Scripts\git-filter-repo.exe`.
- Git Bash daqui: `python` não resolve; process substitution `<(...)` falha — usar arquivos no scratchpad.
- Credenciais AWS Academy, `gh auth`, criação de contas e valores de secrets: sempre do usuário.
