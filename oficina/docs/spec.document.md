## 8. Documentação (README + entregáveis)

### Dependências

Conceitualmente depende de todas as demais — documenta o estado final do sistema. Na prática, pode ser escrita
em paralelo às últimas etapas, mas só fechada (revisada como "pronta") depois que `spec-kubernetes.md` e
`spec-terraform.md` estiverem validadas, já que o diagrama de arquitetura depende dos componentes reais
provisionados.

### Contrato

- `README.md` ganha seção "Fase 2": objetivos da fase, diagrama de arquitetura (componentes app + EC2/k3s +
  ECR + RDS PostgreSQL + fluxo de deploy), instruções de execução local (`docker compose up`), instruções
  de deploy K8s e Terraform reproduzíveis por terceiros (alguém que não seja o autor consegue seguir o passo a
  passo sem precisar perguntar nada).
- `docs/DDD.md` atualizado para refletir os ports `in`/`out` formalizados em `spec-hexagonal.md` — não é
  reescrita do zero, é atualização do que mudou de fato.
- Diagrama de arquitetura em Mermaid no README, no mesmo padrão já usado em `docs/DDD.md` (consistência visual
  entre os dois documentos).
- Coleção Postman/Swagger: já existe OpenAPI automático (SmallRye) — exportar e linkar no README, não recriar
  manualmente.
- Vídeo demonstrativo e compartilhamento do repositório com `soat-architecture`: ações manuais do usuário,
  fora do escopo de código — listadas como checklist no README, não como item de código a implementar.

### Critério de Aceite

- README contém instruções que, seguidas do zero por alguém sem contexto prévio do projeto, resultam em
  ambiente local funcional (`docker compose up`) sem etapas implícitas não documentadas.
- Diagrama Mermaid renderiza sem erro de sintaxe no preview do GitHub.
- `docs/DDD.md` não contradiz a estrutura de pacotes real do código após `spec-hexagonal.md` — validável por
  inspeção cruzada simples (os ports citados no documento existem no código com esse nome).
- Link para o OpenAPI/Swagger exportado está presente e funcional.
- Checklist de ações manuais (vídeo, compartilhamento de repo) está explícito e marcável, não embutido em
  prosa corrida.

### Fora de Escopo

- Geração do vídeo em si e o compartilhamento do repositório — são ações do usuário, não produto de código;
  esta spec só garante que o checklist existe e está claro.