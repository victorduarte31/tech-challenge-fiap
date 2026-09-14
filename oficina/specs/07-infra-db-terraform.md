# E3 — Infraestrutura do banco de dados gerenciado (Terraform)

> Repositório **`oficina-infra-database`** (repo 3 de 4). Atende o item "Banco de Dados Gerenciado" de
> R6 e sustenta a justificativa formal de R8/R9 documentada em [01](01-modelagem-banco.md).

## 1. Dependências

- [06](06-infra-k8s-terraform.md) aplicado: consome `vpc_id`, `private_subnet_ids` e
  `k3s_security_group_id` via SSM.

## 2. Escopo

```
rds.tf              aws_db_instance (PostgreSQL 16, db.t4g.micro, criptografado, privado)
subnet_group.tf     aws_db_subnet_group nas 2 subnets privadas
parameter_group.tf  aws_db_parameter_group                                   (novo)
security_group.tf   SG do RDS: 5432 apenas do SG do k3s e do SG do Lambda
data.tf             leitura dos parâmetros SSM publicados pelo repo de infra-k8s
ssm_outputs.tf      publica /oficina/<env>/db_endpoint, db_name, db_port     (novo)
backend.tf          state S3, key = oficina/infra-database/<workspace>.tfstate
```

## 3. Parameter group (novo nesta fase)

O RDS hoje roda com o parameter group padrão. Três ajustes que sustentam os requisitos de
observabilidade e performance:

| Parâmetro | Valor | Motivo |
|---|---|---|
| `log_min_duration_statement` | `500` (ms) | Registra consulta lenta no log do RDS. É a evidência objetiva quando o alerta de latência p95 disparar — sem isso, "o banco está lento" é palpite |
| `log_connections` / `log_disconnections` | `1` | Rastreia esgotamento de pool e conexões do Lambda; é o que diagnostica o teto de ~112 conexões do `db.t4g.micro` |
| `rds.force_ssl` | `1` | Obriga TLS em toda conexão. O tráfego app↔banco atravessa a VPC, mas "rede interna" não é controle de segurança. Exige `sslmode=require` na URL JDBC da aplicação e no Lambda — **mudança coordenada**, senão a aplicação para de conectar |

`shared_preload_libraries = pg_stat_statements` fica **fora**: exige reboot da instância e o ganho
(estatística agregada de consulta) não se sustenta num banco recriado a cada sessão.

Não habilitar **Performance Insights**: gratuito só nos 7 dias de retenção em algumas classes, e a
classe `t4g.micro` tem suporte limitado; risco de custo silencioso sem ganho no laboratório.

## 4. Ambientes

Mesmos workspaces de [06](06-infra-k8s-terraform.md): `hml` e `prod`, com a mesma regra — **`hml` sobe
sob demanda**, quando há mudança de infraestrutura ou migration nova para ensaiar. O RDS é o recurso
mais lento a criar (~8 min) e o mais caro se esquecido: subir homologação sem necessidade custa tempo
de sessão e risco, não só centavos.

| | `hml` | `prod` |
|---|---|---|
| Classe | `db.t4g.micro` | `db.t4g.micro` |
| Storage | 20 GiB gp3 (mínimo do RDS) | 20 GiB gp3 |
| `backup_retention_period` | 0 | **1 dia** |
| `skip_final_snapshot` | true | true |
| `multi_az` | false | false |
| `deletion_protection` | false | false |

`prod` com 1 dia de retenção (custo: backup até o tamanho do storage é gratuito; 20 GiB de backup para
20 GiB de banco não gera cobrança) para que exista **alguma** história a contar sobre recuperação — e
para que a decisão de laboratório fique explícita em vez de implícita. `deletion_protection = false` é
obrigatório aqui: o `destroy` ao final da sessão é o principal controle de custo do projeto, e
proteção contra exclusão o transformaria em intervenção manual.

## 5. Senha do banco

Permanece como hoje: `TF_VAR_db_password` vindo de GitHub Secret, com validação de comprimento mínimo
de 16 caracteres, e o state em bucket criptografado. **Não** vai para Secrets Manager: US$0,40/mês por
segredo é irrisório, mas o Lambda em VPC sem NAT não conseguiria lê-lo em runtime
([02](02-auth-cpf-lambda.md) §4), então haveria dois mecanismos para o mesmo segredo — pior que um só.

O parâmetro `/oficina/<env>/db_endpoint` publicado em SSM **não** inclui credencial: só host, porta e
nome do banco. Usuário e senha continuam sendo GitHub Secrets, injetados como Secret do Kubernetes e
variável de ambiente do Lambda.

## 6. Alerta de custo específico deste repositório

O RDS é o recurso mais perigoso do projeto: **o "End Lab" do AWS Academy não o para**. Uma instância
esquecida custa US$0,019/h ≈ US$13,70/mês e come o orçamento inteiro em ~3,5 meses de inatividade.

Controles:

1. Job `destroy` com `workflow_dispatch` neste repositório, igual ao de infra-k8s.
2. O README abre com o aviso, não o esconde no fim.
3. O resumo do job `apply` (`$GITHUB_STEP_SUMMARY`) imprime o custo/hora e a data-hora do apply.
4. Checklist de encerramento de sessão em [10](10-runbook-sessao-custos.md).

## 7. Pipeline

Idêntica em forma à de [06](06-infra-k8s-terraform.md) (`validate` → `plan` no PR → `apply` por branch
→ `destroy` manual), com uma etapa a mais no `apply`: verificação de conectividade
(`aws rds describe-db-instances` até `available`) antes de publicar o parâmetro SSM — publicar um
endpoint de instância ainda em `creating` quebra o deploy da aplicação logo em seguida.

## 8. Critério de aceite

- `terraform apply` cria a instância consumindo a VPC do outro repositório **sem** nenhuma variável de
  rede copiada à mão.
- Conexão a partir de um pod do cluster funciona; a partir da internet, não (`publicly_accessible = false`).
- Com `rds.force_ssl = 1`, conexão sem TLS é recusada e a aplicação conecta com `sslmode=require`.
- `terraform destroy` remove instância, subnet group, parameter group e SG, sem recurso órfão.
- README com diagrama, justificativa da escolha do banco (resumo apontando para o RFC) e a tabela de
  custo.

## 9. Fora de escopo

- Aurora, réplica de leitura, RDS Proxy, Multi-AZ, Performance Insights.
- Migrations de schema: são da aplicação (Flyway), decisão registrada em [01](01-modelagem-banco.md) §3.4.
- Rotação automática de senha.
