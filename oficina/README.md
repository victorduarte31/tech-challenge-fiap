# Oficina Mecânica - Sistema Integrado MVP

> Tech Challenge Fase 1 — FIAP Pós-Graduação SOAT  
> Back-end em Java 21 + Quarkus 3.15 + PostgreSQL

## Sobre o Sistema

Sistema integrado de atendimento e execução de serviços para oficina mecânica, com foco em:
- Gestão de Ordens de Serviço (OS) com máquina de estados
- CRUD de clientes, veículos, peças e serviços
- Acompanhamento público de OS pelo cliente
- Controle de estoque de peças e insumos
- Autenticação JWT para APIs administrativas

## Requisitos

| Ferramenta | Versão mínima |
|-----------|--------------|
| Java      | 21+          |
| Maven     | 3.9+         |
| Docker    | 24+          |
| Docker Compose | 2.0+   |

## Como Executar

### Opção 1 — Docker Compose (Recomendado)

```bash
# Clone o repositório
git clone <url-repositorio>
cd oficina

# Sobe toda a stack (PostgreSQL + App) em background
docker-compose up --build -d

# Acompanha os logs
docker-compose logs -f app

# Para a stack
docker-compose down
```

A aplicação estará disponível em `http://localhost:8080`

> **Nota:** Na primeira execução, o container gera automaticamente o par de chaves RSA para JWT. O volume `oficina_jwt_keys` persiste as chaves entre reinicializações.

### Opção 2 — Desenvolvimento Local

```bash
# 1. Gere as chaves JWT (necessário apenas na primeira vez)
chmod +x generate-keys.sh
./generate-keys.sh

# 2. Suba o PostgreSQL via Docker
docker run -d \
  --name oficina-postgres \
  -e POSTGRES_DB=oficina_db \
  -e POSTGRES_USER=oficina \
  -e POSTGRES_PASSWORD=oficina123 \
  -p 5432:5432 \
  postgres:16-alpine

# 3. Execute em modo dev (hot-reload)
mvn quarkus:dev
```

### Opção 3 — Build e execução manual

```bash
# Build sem testes
mvn package -DskipTests

# Executa o JAR
java -jar target/quarkus-app/quarkus-run.jar
```

## Documentação da API (Swagger)

Após subir a aplicação, acesse:

- **Swagger UI:** http://localhost:8080/swagger-ui
- **OpenAPI JSON:** http://localhost:8080/openapi

## Usuários Padrão (apenas dev/MVP)

Em **dev** (via `docker-compose.yml`) os seguintes usuários são criados na primeira execução:

| Usuário   | Senha (dev) | Papel    | Permissões                         |
|-----------|-------------|----------|------------------------------------|
| admin     | admin123    | ADMIN    | Acesso total                       |
| mecanico  | mecanico123 | MECHANIC | Consulta + atualização de OS        |

> ⚠️ **Em produção, defina obrigatoriamente** as variáveis `APP_SEED_ADMIN_PASSWORD` e
> `APP_SEED_MECHANIC_PASSWORD`. Se não forem definidas, o sistema **gera senhas aleatórias**
> e as registra no log apenas uma vez. Após criar os usuários, defina `APP_SEED_ENABLED=false`.

## Autenticação

```bash
# 1. Faça login para obter o token JWT
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}'

# Resposta:
# {
#   "token": "eyJhbGc...",
#   "username": "admin",
#   "role": "ADMIN",
#   "expiresIn": 28800
# }

# 2. Use o token nas chamadas administrativas
curl http://localhost:8080/admin/clients \
  -H "Authorization: Bearer eyJhbGc..."
```

## Fluxo da Ordem de Serviço

```
RECEIVED → IN_DIAGNOSIS → AWAITING_APPROVAL → IN_EXECUTION → FINISHED → DELIVERED
                                     ↓
                                 CANCELLED
```

| Ação                          | Endpoint                                                | Papel          |
|-------------------------------|---------------------------------------------------------|----------------|
| Criar OS                      | `POST /admin/work-orders`                               | ADMIN/MECHANIC |
| Iniciar diagnóstico           | `PATCH /admin/work-orders/{id}/start-diagnosis`         | ADMIN/MECHANIC |
| Enviar orçamento              | `PATCH /admin/work-orders/{id}/send-for-approval`       | ADMIN/MECHANIC |
| Cliente aprova (remoto)       | `POST /public/work-orders/{orderNumber}/approve`        | Público (¹)    |
| Cliente rejeita (remoto)      | `POST /public/work-orders/{orderNumber}/reject`         | Público (¹)    |
| Aprovar (registro presencial) | `PATCH /admin/work-orders/{id}/approve`                 | ADMIN/MECHANIC |
| Rejeitar (registro presencial)| `PATCH /admin/work-orders/{id}/reject`                  | ADMIN/MECHANIC |
| Concluir execução             | `PATCH /admin/work-orders/{id}/complete`                | ADMIN/MECHANIC |
| Registrar entrega             | `PATCH /admin/work-orders/{id}/deliver`                 | ADMIN/MECHANIC |
| Cancelar                      | `PATCH /admin/work-orders/{id}/cancel`                  | ADMIN          |

> (¹) **Approve/Reject — dois canais distintos**
> - **Canal público** (`/public/work-orders/{orderNumber}/approve|reject`): destinado ao
>   próprio cliente aprovar/rejeitar remotamente seu orçamento. Exige o número da OS e
>   prova de identidade (CPF/CNPJ) no corpo da requisição.
> - **Canal administrativo** (`/admin/work-orders/{id}/approve|reject`): destinado ao
>   atendente registrar uma aprovação/rejeição feita **presencialmente ou por telefone**
>   pelo cliente. Mantém auditoria via JWT do operador (ADMIN/MECHANIC).

## Endpoints Públicos (sem autenticação)

| Método | Endpoint                                          | Descrição                          |
|--------|---------------------------------------------------|------------------------------------|
| GET    | `/public/work-orders/{orderNumber}/status`        | Consultar status da OS             |
| POST   | `/public/work-orders/{orderNumber}/approve`       | Aprovar orçamento (exige CPF/CNPJ) |
| POST   | `/public/work-orders/{orderNumber}/reject`        | Rejeitar orçamento (exige CPF/CNPJ)|

> **Aprovação/rejeição pública — exigência de identidade**
> Os endpoints `approve`/`reject` exigem o CPF/CNPJ do cliente no corpo da requisição.
> O sistema valida que esse documento bate com o cliente associado à OS — caso contrário
> retorna `404` (mesma resposta de OS inexistente, para não distinguir cenários).
>
> Exemplo:
> ```bash
> curl -X POST http://localhost:8080/public/work-orders/OS-000001/approve \
>   -H "Content-Type: application/json" \
>   -d '{"clientCpfCnpj": "111.444.777-35"}'
> ```

## Executando os Testes

```bash
# Roda todos os testes com relatório de cobertura JaCoCo
mvn verify

# Apenas testes unitários
mvn test

# Relatório de cobertura em: target/site/jacoco/index.html
```

## Estrutura do Projeto

```
src/main/java/br/com/oficina/
├── domain/
│   ├── model/          # Entidades JPA (Cliente, Veículo, OS, etc.)
│   └── exception/      # Exceções de domínio
├── application/
│   ├── dto/            # Data Transfer Objects (request/response)
│   └── service/        # Serviços de aplicação (regras de negócio)
├── infrastructure/
│   ├── repository/     # Repositórios Panache
│   ├── security/       # Autenticação JWT, AppUser
│   └── validation/     # Validadores de CPF/CNPJ e placa
└── interfaces/
    ├── rest/           # Recursos JAX-RS (endpoints REST)
    └── exception/      # Mapeamento de exceções para HTTP
```

## Banco de Dados

**PostgreSQL** foi escolhido pelos seguintes motivos:
- Suporte nativo a tipos avançados (JSONB, arrays)
- ACID compliance robusto para operações transacionais
- Excelente performance em queries complexas com JOIN
- Suporte a índices parciais e expressões
- Comunidade ativa e ampla adoção enterprise

As migrations são gerenciadas pelo **Flyway** (`src/main/resources/db/migration/`).

## Variáveis de Ambiente

| Variável                     | Padrão              | Descrição                                   |
|------------------------------|---------------------|---------------------------------------------|
| DB_HOST                      | localhost           | Host do PostgreSQL                          |
| DB_PORT                      | 5432                | Porta do PostgreSQL                         |
| DB_NAME                      | oficina_db          | Nome do banco                               |
| DB_USERNAME                  | postgres            | Usuário do banco                            |
| DB_PASSWORD                  | postgres            | Senha do banco                              |
| JWT_ISSUER                   | oficina-api         | Issuer do JWT                               |
| JWT_EXPIRATION_HOURS         | 8                   | Validade do token JWT (horas)               |
| JWT_PRIVATE_KEY_LOCATION     | keys/privateKey.pem | Caminho da chave privada RSA                |
| JWT_PUBLIC_KEY_LOCATION      | keys/publicKey.pem  | Caminho da chave pública RSA                |
| CORS_ALLOWED_ORIGINS         | localhost:3000,8080 | Lista de origens permitidas (CSV)           |
| APP_SEED_ENABLED             | true                | Se cria usuários iniciais (desabilite após) |
| APP_SEED_ADMIN_PASSWORD      | _gerada_            | Senha do admin; vazia → senha aleatória     |
| APP_SEED_MECHANIC_PASSWORD   | _gerada_            | Senha do mecanico; vazia → senha aleatória  |

## Health Check

```bash
# Liveness
curl http://localhost:8080/q/health/live

# Readiness
curl http://localhost:8080/q/health/ready

# Status completo
curl http://localhost:8080/q/health
```

## Segurança

- Senhas armazenadas com **BCrypt** (custo 12, sem reversibilidade)
- Tokens JWT assinados com **RSA-256** (chave 2048-bit)
- Tokens expiram em **8 horas** (configurável via `JWT_EXPIRATION_HOURS`)
- Endpoints administrativos requerem role `ADMIN` ou `MECHANIC`
- Comparação de senha em **tempo constante** evita _timing attacks_ que vazariam usuários válidos
- Validação de CPF/CNPJ (com dígitos verificadores) no cadastro de clientes
- Validação de placa de veículo (formato antigo e Mercosul)
- CORS configurável por origem (sem `*` em produção)
- Container Docker executa como **usuário não-root**
- Em ambiente `prod`, Swagger UI e OpenAPI são **desabilitados automaticamente**
- Senhas iniciais não-hardcoded: se não configuradas, são geradas aleatoriamente e logadas uma única vez
- `correlationId` em respostas 500 facilita troubleshooting sem expor stack trace ao cliente

## Tecnologias

| Tecnologia           | Versão    | Finalidade                    |
|---------------------|-----------|-------------------------------|
| Java                | 21 LTS    | Linguagem                     |
| Quarkus             | 3.15.1    | Framework (fast startup, GraalVM-ready) |
| Maven               | 3.9+      | Gerenciador de dependências    |
| PostgreSQL          | 16        | Banco de dados principal       |
| Hibernate ORM Panache | 3.15.1  | ORM com padrão Repository      |
| Flyway              | —         | Migrations de banco de dados   |
| SmallRye JWT        | —         | Autenticação JWT (RSA-256)     |
| SmallRye OpenAPI    | —         | Documentação Swagger           |
| Hibernate Validator | —         | Validação de beans             |
| H2                  | —         | Banco em memória para testes   |
| JUnit 5             | —         | Testes unitários               |
| Mockito             | —         | Mocking para testes unitários  |
| REST-Assured        | —         | Testes de integração REST      |
| JaCoCo              | 0.8.12    | Cobertura de código            |
