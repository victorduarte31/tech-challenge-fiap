# E6 — API Gateway: roteamento e proteção das rotas sensíveis

> Atende R1, o item "API Gateway" de R6 e fecha R2 no perímetro. Vive no repositório
> **`oficina-auth-lambda`**, junto da função — rota e função são um artefato só de deploy.

## 1. Dependências

- [02](02-auth-cpf-lambda.md): a função de autenticação precisa existir para ser integrada.
- [06](06-infra-k8s-terraform.md): EIP e security group da EC2 do k3s (destino da integração HTTP).
- [04](04-app-rbac-cliente.md): filtro de segredo do gateway na aplicação.

## 2. Escolha do gateway

| Opção | Custo | Integra Lambda | Integra o cluster | Veredito |
|---|---|---|---|---|
| **AWS API Gateway HTTP API (v2)** | US$1,00/milhão de req, **sem custo por hora** | nativo | `HTTP_PROXY` para a URL pública | **Escolhido** |
| AWS API Gateway REST API (v1) | US$3,50/milhão + cache opcional pago | nativo | sim | 3,5× mais caro por recursos que não usamos (modelos, validação de request, WAF integrado) |
| Kong | licença OSS gratuita, mas exige EC2 dedicada (+US$0,0208/h ≈ US$15/mês) e Postgres próprio | via plugin | sim | Custo de infraestrutura e operação sem contrapartida no laboratório |
| Traefik (já embarcado no k3s) | US$0,00 | não nativamente — precisaria de Function URL + `ExternalName` | sim | Só faria sentido se o Lambda não existisse; e o gateway morreria junto com o node |
| ALB + Lambda target | US$0,0225/h ≈ US$16/mês fixo | sim | sim | Custo por hora inaceitável para o orçamento |

**Escolhido: HTTP API.** O argumento decisivo é o modelo de cobrança: US$0 quando ninguém chama. Numa
conta com US$48 para o curso inteiro, qualquer recurso com custo por hora compete diretamente com as
horas de laboratório disponíveis.

## 3. Rotas

Uma API por ambiente (`oficina-hml`, `oficina-prod`), stage `$default` com *auto-deploy*.

| Método | Rota | Integração | Autorização |
|---|---|---|---|
| `POST` | `/auth/token` | `AWS_PROXY` → alias do Lambda de autenticação | **nenhuma** (é o endpoint que emite o token) |
| `POST` | `/auth/login` | `HTTP_PROXY` → `http://<eip>/auth/login` | nenhuma (login de funcionário, já protegido por senha) |
| `ANY` | `/admin/{proxy+}` | `HTTP_PROXY` → `http://<eip>/admin/{proxy}` | **`JwtAuthorizer` (Lambda authorizer)** |
| `ANY` | `/me/{proxy+}` | `HTTP_PROXY` → `http://<eip>/me/{proxy}` | **`JwtAuthorizer`** — rotas do cliente autenticado por CPF (spec 04) |
| `GET` | `/health` | `HTTP_PROXY` → `http://<eip>/q/health/ready` | nenhuma — usado pelo monitor de uptime |
| `GET` | `/public/{proxy+}` | `HTTP_PROXY` → `http://<eip>/public/{proxy}` | nenhuma — acompanhamento por token de uso único da OS |

Os prefixos espelham os `@Path` que a aplicação já expõe (`/admin/*`, `/public/*`, `/auth/login`);
`/me/*` é o único conjunto novo. Nenhuma rota da aplicação precisa mudar de endereço por causa do
gateway.

Rotas **não** expostas pelo gateway, deliberadamente: `/q/metrics`, `/q/health/live`, `/swagger-ui`,
`/openapi`. São superfície interna; quem precisa delas alcança pelo cluster.

## 4. Proteção das rotas sensíveis

**Decidido: duas camadas.**

O PDF, na letra, não exige que o gateway valide o token — ele diz que a Function devolve "um token
(JWT) válido para consumo das APIs protegidas". Validar só na aplicação atenderia o texto. Mas então o
gateway faria apenas roteamento, e a exigência anterior — *"implementar um API Gateway para controle e
roteamento"* somada a *"proteger rotas sensíveis"* — ficaria atendida pela metade. O authorizer é o que
torna a palavra "proteger" verdadeira no perímetro.

**Camada 1 — Lambda authorizer no gateway.** Autorizador do tipo `REQUEST`, *simple response*,
`identitySource = $request.header.Authorization`, `resultTtlInSeconds = 300`. Valida assinatura RS256
com a chave **pública** (variável de ambiente, sem segredo), `iss`, `exp` e a presença de um `groups`
conhecido. Retorna `{ "isAuthorized": true, "context": { "clientId": "42", "role": "CLIENT" } }`.

- **Fora da VPC, deliberadamente.** O authorizer não consulta o banco — só verifica assinatura e
  validade. Mantê-lo fora da VPC evita alocação de ENI, elimina o cold start de VPC no caminho de cada
  requisição não cacheada e reduz a superfície da função que *de fato* alcança o RDS (a de
  autenticação, [02](02-auth-cpf-lambda.md) §4). Dois perfis de rede diferentes para dois níveis de
  privilégio diferentes.
- **Custo:** com cache de 300 s por token, uma demonstração inteira gera dezenas de invocações.
  Irrelevante.
- **Ganho concreto:** requisição sem token, com token expirado ou com assinatura forjada **nunca chega
  ao cluster** — a EC2 não gasta CPU com tráfego ilegítimo, e isso é visível no vídeo e no access log
  (`authorizer.error` preenchido, `integration.latency` vazio).
- **Novo modo de falha, e como é contido:** se o authorizer falhar, todas as rotas protegidas param.
  Por isso ele é minúsculo, sem dependência de rede, sem banco e sem VPC — o conjunto de coisas que
  podem quebrá-lo é praticamente só a própria chave pública estar errada, o que o *smoke test* pega.

**Camada 2 — a aplicação continua validando o JWT** (SmallRye, `@RolesAllowed`), como já faz. O
authorizer decide *se passa*; a aplicação decide *o que pode fazer* (papel, posse do recurso). Nenhuma
das duas confia na outra. É o que impede que alguém que alcance a EC2 por outro caminho contorne a
autorização.

> **Alternativa considerada e rejeitada:** JWT authorizer nativo do HTTP API. Ele é mais simples e
> gratuito, mas exige um emissor OIDC acessível por **HTTPS** publicando
> `/.well-known/openid-configuration` e JWKS. O laboratório não tem domínio nem certificado, e montar
> ACM + domínio custaria tempo e (com Route 53) dinheiro. Registrado no ADR como o caminho natural
> quando houver domínio real.

## 5. Caminho gateway → cluster

O `HTTP_PROXY` do API Gateway chama a EC2 pela internet, em HTTP simples. Dois controles impedem que
isso vire uma porta aberta:

1. **Security group restrito às faixas do próprio API Gateway.** Data source
   `aws_ip_ranges { services = ["api_gateway"], regions = ["us-east-1"] }` alimenta as regras de
   ingresso na porta 80, somadas ao `/32` do aluno (acesso direto para depuração). Isso substitui o
   `0.0.0.0/0` que o `variables.tf` atual proíbe por validação — e a proibição continua valendo.
   *Verificar na implementação:* o número de prefixos retornados contra a cota de 60 regras por
   security group; se estourar, dividir em dois SGs anexados à mesma instância.
2. **Segredo compartilhado no header.** O gateway injeta `X-Gateway-Key: <valor>` via
   `request_parameters` na integração; a aplicação rejeita com 403 qualquer requisição a `/admin/*` ou `/me/*` sem
   o valor correto (filtro em [04](04-app-rbac-cliente.md)). Protege contra alguém que descubra o EIP e
   esteja numa faixa da AWS. O valor é um GitHub Secret, entregue ao gateway pelo Terraform e à
   aplicação pelo Secret do Kubernetes.

**Ausência de TLS entre gateway e cluster é uma limitação assumida**, não um esquecimento: sem domínio
não há certificado válido, e certificado autoassinado exigiria `tlsConfig.insecureSkipVerify`, que é
pior do que não ter TLS porque *aparenta* proteção. O ADR registra que o caminho de produção é
domínio + ACM no gateway e cert-manager/Let's Encrypt no Traefik.

## 6. Controles operacionais

- **Throttling padrão da stage:** 50 rps / burst 100. **Por rota** em `POST /auth/token`: 10 rps /
  burst 20 — esta é a defesa primária contra enumeração de CPF.
- **CORS** no gateway (`allow_origins` = origem do front, `allow_headers` = `authorization,content-type`,
  `allow_methods` = `GET,POST,PUT,PATCH,DELETE,OPTIONS`). Com o gateway na frente, a configuração CORS
  da aplicação passa a ser irrelevante para o tráfego externo — mantida só para acesso direto em
  desenvolvimento.
- **Access logs** em JSON para CloudWatch, com `$context.requestId`, `$context.integration.latency`,
  `$context.responseLatency`, `$context.status`, `$context.authorizer.error`. É a fonte da métrica de
  **latência de API** exigida por R7 medida no perímetro (a latência interna vem do APM).
  Retenção de 7 dias.
- **Sem *usage plans* / API keys**: são de REST API (v1) e resolveriam um problema — quota por
  consumidor — que não existe aqui.

## 7. Terraform

```
main.tf              aws_apigatewayv2_api (protocol_type = "HTTP")
                     aws_apigatewayv2_stage ($default, auto_deploy, access_log_settings)
integrations.tf      aws_apigatewayv2_integration  (AWS_PROXY do lambda de auth)
                     aws_apigatewayv2_integration  (HTTP_PROXY para http://<eip>, com X-Gateway-Key)
routes.tf            aws_apigatewayv2_route × 6
authorizer.tf        aws_apigatewayv2_authorizer + aws_lambda_function (JwtAuthorizer)
                     — SEM bloco vpc_config: o authorizer roda fora da VPC (§4)
                     aws_lambda_permission para o gateway invocar ambas as funções
throttling.tf        aws_apigatewayv2_route_settings (por rota)
outputs.tf           api_endpoint → publicado em SSM /oficina/<env>/api_gateway_url
```

Entradas lidas de SSM (contrato publicado pelos repositórios de infraestrutura):
`/oficina/<env>/k3s_eip`, `/oficina/<env>/vpc_id`, `/oficina/<env>/private_subnet_ids`,
`/oficina/<env>/db_endpoint`. As duas últimas alimentam **apenas** a função de autenticação — o
authorizer não recebe nenhuma delas, e essa ausência é o controle que garante que ele continue sem
acesso ao banco.

## 8. Risco: permissões da conta Academy

`apigateway:*` costuma estar liberado no Learner Lab, mas **não é garantido** e a validação é barata:
na primeira sessão, antes de qualquer outra coisa, rodar

```bash
aws apigatewayv2 create-api --name probe-delete-me --protocol-type HTTP
aws apigatewayv2 delete-api --api-id <id>
```

Se falhar por permissão, o **plano B** é: Traefik como gateway (`IngressRoute` com middleware de
autenticação por `forwardAuth` apontando para a própria aplicação) e o Lambda exposto por **Function
URL** com `AWS_IAM` desabilitado e throttling por `reservedConcurrentExecutions`. O PDF aceita Traefik
explicitamente, então o requisito continua atendido — muda a justificativa do ADR, não o entregável.

## 9. Critério de aceite

- `curl -X POST $API/auth/token -d '{"cpf":"..."}'` devolve token; `curl $API/me/work-orders` com
  esse token devolve 200; **sem** o header `Authorization` devolve 401 **emitido pelo gateway**
  (confirmado no access log: `authorizer.error` preenchido, `integration.latency` vazio — a requisição
  não chegou ao cluster).
- Token com assinatura adulterada → 401 no gateway.
- `curl http://<eip>/me/work-orders` direto, com token válido mas **sem** `X-Gateway-Key` → 403.
- 30 requisições em 1 s em `/auth/token` → ao menos uma 429.
- Access log no CloudWatch contém `requestId` correlacionável com o log do Lambda e com o
  `X-Correlation-Id` que a aplicação registra.
- URL do gateway publicada em SSM e presente no README do repositório.

## 10. Fora de escopo

- WAF (US$5/mês por web ACL + US$1 por regra), domínio customizado, certificado ACM, Route 53.
- Cache de resposta do gateway (só REST API, cobrado por hora).
- Versionamento de API por stage (`/v1`, `/v2`): a API tem um consumidor conhecido; o roteiro de
  versionamento entra no ADR de contratos como evolução futura.
