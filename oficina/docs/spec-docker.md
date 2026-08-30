## 4. Docker

### Dependências

Requer `spec-api.md` 2.5 para saber quais variáveis de mailer existem — esta spec **consome** os nomes
(`MAILER_HOST`, `MAILER_PORT`) definidos lá, não os redefine. Se houver divergência entre este arquivo e
`spec-api.md`, `spec-api.md` é a fonte de verdade.

### Contexto

`Dockerfile` já é multi-stage, non-root, com healthcheck — não há gap estrutural identificado, só a
necessidade de validar que continua funcionando após o refactor hexagonal (mudança de camadas não deveria
afetar o build, mas é a etapa em que isso se confirma na prática).

### Contrato

- `Dockerfile`: sem mudança estrutural. Reconstruir a imagem após o refactor de `spec-hexagonal.md` e validar
  que o container sobe e responde ao healthcheck normalmente.
- `docker-compose.yml`: adicionar serviço `mailpit` (imagem `axllent/mailpit`, portas `1025` e `8025`
  expostas) e injetar no serviço `app` as variáveis `MAILER_HOST=mailpit` e `MAILER_PORT=1025` definidas em
  `spec-api.md` 2.5.

### Critério de Aceite

- `docker build` da imagem da aplicação conclui sem erro após o refactor hexagonal completo.
- `docker compose up`: os serviços `app` e `mailpit` sobem e ficam saudáveis (`healthcheck` do `app` passa).
- Disparar uma transição de status via API local (com `app` rodando no compose) resulta em e-mail visível na
  UI do Mailpit em `localhost:8025` — esta é a validação funcional do contrato de `spec-api.md` 2.5 neste
  ambiente, feita manualmente e registrada no checklist de entrega.

### Fora de Escopo

- Qualquer mudança estrutural no `Dockerfile` (multi-stage, non-root, healthcheck) — já está correto, não é
  retrabalhado aqui.
- Configuração de mailer para produção — isso é `spec-kubernetes.md` (Secret com SMTP real), não este arquivo.