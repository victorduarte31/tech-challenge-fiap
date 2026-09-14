# E9 — Runbook de sessão e controle de custo

> Não é requisito do PDF. É o que impede que o requisito seja atendido e o orçamento acabe no meio do
> curso. Com quatro repositórios e uma conta que expira a cada 4 horas, a sequência de subida e o
> encerramento precisam ser mecânicos.

## 1. Orçamento e ponto de partida

- Crédito: **~US$48**, único para o curso, sem recarga. Esgotou, a conta é desativada.
- Custo de uma sessão de 4h com a stack de produção inteira: **≈ US$0,31** ([00](00-visao-geral.md) §5).
- Homologação, **quando sobe**, acrescenta ≈ US$0,18. Ela não sobe em toda sessão: só quando há
  mudança de infraestrutura ou migration para ensaiar antes de produção
  ([06](06-infra-k8s-terraform.md) §4.1).
- Margem: ~90 sessões completas. **O risco não é a sessão; é o recurso esquecido.**

Ordem de perigo dos recursos, por custo mensal se abandonados:

| Recurso | US$/h | US$/mês se esquecido | Para com "End Lab"? |
|---|---|---|---|
| RDS `db.t4g.micro` + 20 GiB | 0,019 | **13,70** | **não** |
| EC2 `t3.medium` | 0,0416 | 30,00 | **sim** (para; volume continua) |
| EIP não associado | 0,005 | 3,60 | **não** |
| Volume EBS órfão (20 GiB gp3) | 0,0032 | 2,30 | **não** |
| API Gateway / Lambda / SSM | por uso | ~0 | n/a |

## 2. Início de sessão

```
[ ] Start Lab no AWS Academy; copiar as credenciais STS
[ ] Rodar o script de distribuição de segredos (spec 08 §5)
[ ] Confirmar região us-east-1 e conta correta:  aws sts get-caller-identity
[ ] Anotar hora de início — o timer de 4h é rígido
```

## 3. Subida (ordem obrigatória)

A tabela abaixo é a sessão comum: **só produção**. Quando a sessão for de ensaio de infraestrutura,
rode a mesma sequência antes no workspace `hml` (merge em `homolog`), valide, e só então promova para
`main` — o que soma ~13 min (RDS domina) e US$0,18 à sessão.

| Ordem | Repositório | Ação | Tempo típico | Como confirmar |
|---|---|---|---|---|
| 1 | `oficina-infra-k8s` | `apply` (workflow_dispatch ou merge) | ~5 min (k3s sobe no user_data) | `kubectl get nodes` = `Ready` |
| 2 | `oficina-infra-database` | `apply` | ~8 min (criação de RDS é lenta) | status `available` |
| 3 | `oficina-app` | `deploy` | ~6 min | `rollout status` + `/q/health/ready` |
| 4 | `oficina-auth-lambda` | `deploy` | ~3 min | `POST /auth/token` devolve 200 |

Total ~22 min. Passos 1 e 2 podem sobrepor parcialmente (o RDS só precisa da VPC, que existe logo no
começo do passo 1), mas a pipeline do passo 2 depende dos parâmetros SSM publicados ao final do 1 —
paralelizar exige quebrar o passo 1 em dois applies e não vale a complexidade.

## 4. Validação pós-subida

```
[ ] kubectl top nodes            → folga de memória para o HPA chegar ao teto
[ ] kubectl get pods,hpa -n oficina
[ ] curl $API/health             → 200
[ ] POST $API/auth/token com CPF semeado → 200 com token
[ ] GET  $API/me/work-orders com o token → 200
[ ] GET  $API/me/work-orders sem token   → 401 vindo do gateway
[ ] New Relic: entidade oficina-app reportando; log com correlationId encontrável
[ ] Dashboards com dados; alertas ativos
```

## 5. Demonstração de escala (para o vídeo)

```bash
# Carga suficiente para cruzar 70% de CPU com 2 réplicas
hey -z 3m -c 50 "$API/public/work-orders/status?orderNumber=..."
kubectl -n oficina get hpa -w
```

Observar scale-out em ~30 s (o `behavior.scaleUp` está sem janela de estabilização) e scale-in após 3
minutos de estabilização. **Aquecer o Lambda antes de gravar** — uma invocação prévia elimina o cold
start da demonstração.

## 6. Encerramento (não opcional)

```
[ ] oficina-auth-lambda    → workflow destroy
[ ] oficina-app            → (nada a destruir; morre com o cluster)
[ ] oficina-infra-database → workflow destroy      ← o mais caro se esquecido
[ ] oficina-infra-k8s      → workflow destroy
[ ] Varredura de órfãos (abaixo)
[ ] End Lab
```

```bash
# Nada deve retornar
aws resourcegroupstaggingapi get-resources \
  --tag-filters Key=Project,Values=oficina --query 'ResourceTagMappingList[].ResourceARN'

# Os que escapam de tag: EIPs soltos, volumes órfãos, snapshots
aws ec2 describe-addresses     --query 'Addresses[?AssociationId==`null`].PublicIp'
aws ec2 describe-volumes       --filters Name=status,Values=available --query 'Volumes[].VolumeId'
aws rds  describe-db-instances --query 'DBInstances[].DBInstanceIdentifier'
aws apigatewayv2 get-apis      --query 'Items[].Name'
aws lambda list-functions      --query 'Functions[].FunctionName'
```

Se o `destroy` falhar no meio (acontece: dependência de ENI do Lambda costuma segurar a subnet por
alguns minutos), **rodar de novo** antes de encerrar a sessão. Um `destroy` parcial é a forma mais
comum de deixar RDS e EIP para trás.

## 7. Monitoramento do orçamento

- Conferir o crédito restante no painel do AWS Academy **ao início e ao fim** de cada sessão, e anotar
  o consumo em `docs/orcamento.md` (data, duração, custo, saldo). Duas medições ruins revelam um
  vazamento antes que ele custe caro.
- Se o consumo de uma sessão passar de US$1,00, há recurso rodando fora do previsto — investigar antes
  da próxima sessão, não depois.
- Regra de corte: abaixo de **US$15** de saldo, homologação deixa de subir e só produção é usada, pelo
  tempo estritamente necessário à gravação.

## 8. Modo econômico (se o crédito apertar)

Em ordem de aplicação, do menos ao mais doloroso:

1. Suspender completamente as sessões de `hml` — validar mudança de infraestrutura direto em produção,
   assumindo o risco (e registrando que foi decisão de orçamento, não descuido).
2. `t3.small` em vez de `t3.medium` em produção (HPA máximo 2).
3. Reduzir a janela de sessão: subir, gravar o trecho do vídeo, destruir.
4. Rodar tudo localmente (`docker compose` + k3d) para desenvolvimento; a AWS só nas sessões de
   validação e gravação. `RUN-LOCAL.md` já cobre o ambiente local.

## 9. Critério de aceite

- O runbook foi executado ponta a ponta **ao menos uma vez** antes da gravação do vídeo, e os tempos
  da tabela §3 foram confirmados (ou corrigidos).
- A varredura de órfãos volta vazia ao final de toda sessão.
- `docs/orcamento.md` existe e tem ao menos duas medições.
