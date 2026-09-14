# E5 — Function Serverless de autenticação por CPF

> Atende R3 integralmente e metade de R2. Repositório: **`oficina-auth-lambda`** (repo 1 de 4).

## 1. Dependências

- [01](01-modelagem-banco.md): coluna `clients.status` e papel `oficina_auth_ro` precisam existir.
- [06](06-infra-k8s-terraform.md): VPC e subnets privadas (o Lambda roda dentro delas).
- [07](07-infra-db-terraform.md): endpoint do RDS e security group.
- [03](03-api-gateway.md) consome esta função — mas é escrita depois, não antes.

## 2. Contrato da função

### Requisição

```
POST /auth/token
Content-Type: application/json

{ "cpf": "529.982.247-25" }
```

Aceita CPF com ou sem máscara; normaliza para 11 dígitos antes de qualquer coisa.

### Respostas

| Status | Corpo | Quando |
|---|---|---|
| 200 | `{ "access_token": "...", "token_type": "Bearer", "expires_in": 3600, "client": { "id": 42, "name": "Maria S." } }` | CPF válido, cliente existe e está `ACTIVE` |
| 400 | `{ "error": "invalid_request", "message": "CPF inválido" }` | CPF ausente, malformado ou com dígito verificador errado |
| 401 | `{ "error": "unauthorized", "message": "Não foi possível autenticar com os dados informados" }` | Cliente inexistente **ou** `INACTIVE` **ou** `BLOCKED` |
| 429 | `{ "error": "too_many_requests" }` | Throttling do API Gateway (não é a função que emite) |
| 500 | `{ "error": "internal_error", "correlationId": "..." }` | Falha de banco ou de assinatura |

**Por que 401 é indistinguível entre "não existe" e "bloqueado":** o CPF é o único fator de
autenticação deste fluxo. Responder 404 para inexistente transformaria o endpoint num validador de
"este CPF é cliente da oficina?" — vazamento de base de clientes por enumeração. A distinção existe
no log estruturado (`reason: "not_found" | "blocked" | "inactive"`), que só o operador vê.

### Claims do JWT emitido

```json
{
  "iss": "oficina-api",
  "sub": "42",
  "upn": "42",
  "groups": ["CLIENT"],
  "clientId": 42,
  "cpf": "529******25",
  "jti": "3f9a...",
  "iat": 1757000000,
  "exp": 1757003600
}
```

- `iss` **idêntico** ao da aplicação (`oficina-api`) e assinatura **RS256 com a mesma chave privada**
  que a aplicação já usa. Consequência: a aplicação valida o token do Lambda sem nenhuma configuração
  nova — `mp.jwt.verify.publickey.location` já aponta para a chave pública correspondente.
  A alternativa (segundo emissor, segunda chave) exigiria multi-issuer no SmallRye e não traz ganho:
  os dois emissores pertencem ao mesmo domínio de confiança.
- `groups: ["CLIENT"]` — papel novo, sem interseção com `ADMIN`/`MECHANIC`. As regras de acesso do
  papel estão em [04](04-app-rbac-cliente.md).
- `cpf` mascarado: o token trafega em logs de gateway e em ferramentas de observabilidade; CPF
  completo em claim seria dado pessoal exposto por padrão.
- `exp` = 1 hora. Curto porque o fator é fraco; longo o bastante para não atrapalhar a demonstração.
- `jti` presente para permitir revogação futura (denylist) sem quebrar o contrato — hoje não há
  revogação, e isso é explícito no ADR.

## 3. Runtime e empacotamento

**Decidido: Java 21 + SnapStart, empacotado como ZIP** (Maven Shade). A comparação abaixo fica
registrada porque é a justificativa que vai para o ADR 0016.

| Critério | Java 21 + SnapStart | Node 22 / Python 3.13 |
|---|---|---|
| Cold start em VPC | ~400 ms (SnapStart restaura snapshot pré-inicializado) | ~300 ms |
| Cold start sem SnapStart | 2–4 s | ~300 ms |
| Custo do SnapStart | **US$0,00** para Java desde 2023 | n/a |
| Toolchain | Maven, já dominado pelo time | segundo toolchain para manter, testar e escanear |
| Linhas de código | ~200 | ~90 |
| Reuso conceitual | mesmo algoritmo de CPF e mesma biblioteca JOSE do domínio | reimplementação |

SnapStart exige **versão publicada + alias** (não funciona no `$LATEST`) e proíbe imagem de container.
A conexão JDBC **não pode** ser aberta na inicialização e reaproveitada no snapshot: o socket morre na
restauração. Padrão correto: `Core.getGlobalContext().register(new Resource(){ afterRestore(...) })`
ou, mais simples, abrir a conexão sob demanda no handler com um pool minúsculo (`HikariCP`, `max=2`).

Dependências: `aws-lambda-java-core`, `aws-lambda-java-events`, driver `postgresql`,
`com.nimbusds:nimbus-jose-jwt` (assinatura RS256 a partir do PEM PKCS#8), `HikariCP`. Sem framework —
Quarkus/Spring dentro de um Lambda de 200 linhas é peso morto de cold start e de superfície de CVE.

**Custo desta escolha, assumido conscientemente:** SnapStart acrescenta dois passos à pipeline
(`publish-version` e movimentação do alias) e proíbe imagem de container — o artefato é ZIP e o
`Dockerfile` deste repositório existe só para o build reprodutível, não para o runtime da função. Em
troca, o toolchain continua sendo um só (Maven) em todo o projeto.

> **Este repositório tem duas funções.** A de autenticação, descrita aqui, roda **dentro** da VPC. O
> *authorizer* do gateway ([03](03-api-gateway.md) §4) roda **fora** da VPC: ele só valida assinatura
> com a chave pública, não toca no banco, e manter os dois perfis de rede separados evita que o
> authorizer consuma ENI e pague o cold start de VPC em cada requisição não cacheada.

## 4. Acesso ao banco

**Dentro da VPC, subnets privadas, security group próprio.** O RDS é `publicly_accessible = false`;
não há outro caminho.

Implicações aceitas e o porquê:

- **Sem NAT Gateway.** O Lambda não precisa de internet: só fala com o RDS, que está na mesma VPC. NAT
  custaria US$0,045/h ≈ US$32/mês — dois terços do orçamento inteiro da fase.
- **Sem Secrets Manager / SSM em runtime.** São APIs públicas; sem NAT, exigiriam VPC endpoints de
  interface a US$0,01/h cada (≈ US$7,20/mês por endpoint). Segredos entram como **variáveis de ambiente
  do Lambda**, criptografadas em repouso pela chave gerenciada `aws/lambda` (custo zero).
  Trade-off explícito: quem tiver `lambda:GetFunctionConfiguration` lê os segredos. Mitigação: papel do
  banco é somente-leitura em 5 colunas de uma tabela, e a chave privada JWT é rotacionável por sessão.
- **Sem internet ⇒ telemetria não sai direto.** A extensão do New Relic para Lambda precisaria de saída
  HTTPS. Solução em [05](05-observabilidade.md): logs JSON para CloudWatch + uma função *forwarder*
  fora da VPC assinada no log group.

Variáveis de ambiente:

| Variável | Origem |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | SSM Parameter Store, lido pelo **Terraform** em tempo de `apply` (não em runtime) |
| `DB_USER` = `oficina_auth_ro`, `DB_PASSWORD` | GitHub Secret do ambiente |
| `JWT_PRIVATE_KEY_PEM` | GitHub Secret (o mesmo par usado pela aplicação) |
| `JWT_ISSUER` = `oficina-api`, `JWT_TTL_SECONDS` = `3600` | Terraform, literal |

## 5. Validação de CPF

Regra completa, no domínio da função (classe `Cpf` — Value Object, sem setters, validação no
construtor):

1. Remove tudo que não for dígito.
2. Exige exatamente 11 dígitos.
3. Rejeita as 10 sequências repetidas (`00000000000`…`99999999999`) — passam no cálculo dos dígitos
   verificadores e são o caso de teste que mais escapa.
4. Calcula os dois dígitos verificadores (módulo 11) e compara.

O algoritmo é uma reimplementação do que já existe em `CpfCnpjValidator` na aplicação. **Não** será
extraída uma biblioteca compartilhada: publicar e versionar um artefato Maven para 40 linhas estáveis
há décadas criaria acoplamento entre dois repositórios que a fase pede justamente para separar. O ADR
registra a duplicação como decisão consciente, com o teste de equivalência como rede de segurança.

## 6. Observabilidade da função

- Log estruturado JSON em uma linha por evento, sem framework:
  `{"level":"INFO","event":"auth.granted","clientId":42,"cpfMasked":"529******25","correlationId":"<requestId do API Gateway>","durationMs":38}`
- **Nunca** logar CPF completo, token emitido ou stack trace com dados de conexão.
- `correlationId` = `context.getAwsRequestId()` **e** o `x-amzn-requestid` do gateway, ambos no log —
  é o que amarra o rastro gateway → lambda → aplicação em [05](05-observabilidade.md).
- Log group com `retention_in_days = 7` (custo: CloudWatch cobra US$0,50/GB ingeridos e US$0,03/GB-mês
  retidos; sem retenção definida o padrão é "para sempre").
- Métricas derivadas de log (New Relic): taxa de 401, p95 de duração, contagem de cold starts.

## 7. Segurança

- Throttling por rota no API Gateway (spec 03): 10 rps / burst 20. Sem isso, o endpoint é um oráculo de
  enumeração de CPF a milhares de tentativas por segundo.
- Resposta com tempo aproximadamente constante entre "não existe" e "existe mas bloqueado" — o caminho
  de erro não deve retornar antes da consulta.
- `reservedConcurrentExecutions = 10`: teto de concorrência. Protege o `db.t4g.micro` de esgotar
  conexões sob um pico (ou um ataque) e limita o custo de uma invocação em loop.
- Timeout de 10 s, memória 512 MB (mais memória = mais CPU proporcional; abaixo de 512 MB a assinatura
  RSA e o handshake TLS do JDBC ficam lentos o suficiente para dominar a latência).
- Chave privada em memória apenas; nada em `/tmp`.

## 8. Terraform que mora neste repositório

O repositório do Lambda é dono da **stack serverless completa** (função + gateway), porque função e
rota são um artefato só do ponto de vista de deploy:

- `aws_lambda_function` + `aws_lambda_function_event_invoke_config` + `aws_lambda_alias` (SnapStart)
- `aws_security_group` do Lambda + regra de entrada em 5432 no SG do RDS (referência cruzada por SSM)
- `aws_cloudwatch_log_group` com retenção
- Toda a parte de API Gateway — detalhada em [03](03-api-gateway.md)
- `data "aws_iam_role" "lab_role"` — **a conta Academy bloqueia `iam:CreateRole`**. A função usa a
  `LabRole` existente. *Validar na primeira sessão* que a trust policy da `LabRole` aceita
  `lambda.amazonaws.com` e que ela tem as permissões de ENI (`ec2:CreateNetworkInterface`,
  `DescribeNetworkInterfaces`, `DeleteNetworkInterface`) exigidas por Lambda em VPC.

## 9. Pipeline (`oficina-auth-lambda`)

| Job | Gatilho | O que faz |
|---|---|---|
| `build-test` | todo push e PR | `mvn verify`: testes de unidade do `Cpf`, do construtor de claims e do mapeamento de erros; teste de integração com **Testcontainers PostgreSQL** exercitando a consulta real contra o schema |
| `terraform-check` | todo push e PR | `fmt -check`, `validate`, `tflint` |
| `package` | `homolog`/`main` | ZIP com shade, artefato assinado por checksum |
| `deploy` | push em `homolog` → env `homolog`; push em `main` → env `producao` | `terraform apply` no workspace correspondente, `lambda publish-version`, move o alias, *smoke test* invocando com um CPF semeado e um CPF inválido |

Gate de custo: `deploy` roda em GitHub Environment com *required reviewer* em `producao` — ver
[08](08-repositorios-cicd.md).

## 10. Critério de aceite

- `POST /auth/token` com CPF de cliente `ACTIVE` semeado devolve 200 e um JWT que a **aplicação em
  execução aceita** em uma rota `@RolesAllowed("CLIENT")` — validado fim a fim, não só na função.
- CPF com dígito verificador errado → 400; CPF de sequência repetida → 400.
- Cliente `BLOCKED` e CPF inexistente → **respostas byte a byte idênticas** (401 com a mesma mensagem).
- Cold start medido após `terraform apply` < 1 s com SnapStart ativo (`REPORT` do CloudWatch com
  `Restore Duration`).
- Conexão do Lambda ao banco falha se tentar `INSERT` — prova do papel somente-leitura.
- Nenhum segredo em log: `aws logs filter-log-events` com o CPF de teste retorna vazio.

## 11. Fora de escopo

- *Refresh token*, revogação e denylist de `jti`: o token dura 1 hora e o fluxo é de baixo risco;
  registrado como evolução futura.
- Segundo fator (OTP por SMS/e-mail): seria o caminho correto para elevar a segurança do CPF como
  credencial, mas custa (SNS/SES) e não é pedido.
- Cadastro de cliente pelo Lambda — a função **só autentica**; criação de cliente continua sendo da
  aplicação, com papel `ADMIN`.
