# syntax=docker/dockerfile:1.7
# ===================================================
# Multi-stage build: Build + Runtime
# ===================================================

# Stage 1: Build
# Tags de patch fixas em vez de "3.9"/"21": tag móvel torna o build não
# reproduzível — a mesma commit pode compilar contra JDKs diferentes com semanas de
# diferença. Fixar por digest (@sha256:...) seria o passo seguinte.
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# O cache mount do BuildKit persiste o ~/.m2 entre builds sem inchar a imagem, o
# que o antigo "mvn dependency:go-offline" não conseguia (ele nem baixa todos os
# plugins do Quarkus, então a camada era invalidada com frequência mesmo assim).
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B --no-transfer-progress dependency:go-offline

# Copia o código-fonte e compila. Os testes NÃO rodam aqui: são um gate do
# pipeline (job build-test), não do empacotamento — rodá-los de novo no build da
# imagem dobraria o tempo sem acrescentar informação.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B --no-transfer-progress package -DskipTests -Dquarkus.profile=docker

# ===================================================
# Stage 2: Runtime
# ===================================================
FROM eclipse-temurin:21.0.5_11-jre-alpine

# UID/GID numéricos e explícitos. Com "USER oficina" (nome), o kubelet não consegue
# validar `runAsNonRoot: true` e recusa o pod com "container has runAsNonRoot and
# image has non-numeric user" — o securityContext do deployment.yaml depende disto.
RUN addgroup -S -g 1001 oficina && adduser -S -u 1001 -G oficina -D oficina

WORKDIR /app

# Defaults não-sensíveis (credenciais devem vir de variáveis de ambiente externas).
# MaxRAMPercentage em vez de -Xmx fixo: a JVM passa a dimensionar o heap a partir do
# limite de memória do cgroup do container, então mudar `resources.limits` no
# Kubernetes não exige mais editar o Dockerfile — e um -Xmx maior que o limite
# deixaria de virar OOMKill.
ENV DB_HOST=postgres \
    DB_PORT=5432 \
    DB_NAME=oficina_db \
    JWT_ISSUER=oficina-api \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Copia o JAR gerado
COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar /app/
COPY --from=build /app/target/quarkus-app/app/ /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

# OpenSSL gera o par de chaves JWT no primeiro start em ambiente local; wget serve ao
# HEALTHCHECK. No Kubernetes as chaves vêm do Secret oficina-jwt-keys e o entrypoint
# apenas as encontra prontas.
# "apk upgrade" antes do add: a tag da imagem base é fixa (build reproduzível), mas
# isso congela junto os pacotes do Alpine daquela data — e o gate do Trivy no
# pipeline reprova CRITICAL com correção disponível (gnutls, openssl, sqlite-libs).
# O upgrade traz esses pacotes para a versão corrigida do mesmo branch do Alpine,
# sem trocar a versão do JRE.
RUN apk --no-cache upgrade && apk add --no-cache openssl wget

COPY docker-entrypoint.sh /app/docker-entrypoint.sh
# Normaliza fins de linha para LF: se o script for checkout/commitado com CRLF
# (comum em Windows com core.autocrlf=true), a shebang vira "#!/bin/sh\r" e o
# container falha com "no such file or directory". tr remove os CR de forma
# portável (busybox).
RUN tr -d '\r' < /app/docker-entrypoint.sh > /app/docker-entrypoint.sh.unix \
    && mv /app/docker-entrypoint.sh.unix /app/docker-entrypoint.sh \
    && chmod +x /app/docker-entrypoint.sh \
    && mkdir -p /app/keys \
    && chown -R oficina:oficina /app

USER 1001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/q/health/live || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
# Shell form: expande ${JAVA_OPTS} em tempo de execução antes de lançar a JVM
CMD ["/bin/sh", "-c", "exec java ${JAVA_OPTS} -jar /app/quarkus-run.jar"]
