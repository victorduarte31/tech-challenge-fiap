# ===================================================
# Multi-stage build: Build + Runtime
# ===================================================

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copia o pom.xml e baixa dependências primeiro (aproveitamento de cache)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia o código-fonte e compila (sem testes)
COPY src ./src
RUN mvn package -DskipTests -q

# ===================================================
# Stage 2: Runtime
# ===================================================
FROM eclipse-temurin:21-jre-alpine

# Cria usuário não-root para reduzir superfície de ataque
RUN addgroup -S oficina && adduser -S -G oficina -D oficina

WORKDIR /app

# Defaults não-sensíveis (credenciais devem vir de variáveis de ambiente externas)
ENV DB_HOST=postgres \
    DB_PORT=5432 \
    DB_NAME=oficina_db \
    JWT_ISSUER=oficina-api \
    JAVA_OPTS="-Xms256m -Xmx512m"

# Copia o JAR gerado
COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar /app/
COPY --from=build /app/target/quarkus-app/app/ /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

# OpenSSL é necessário para gerar chaves JWT no entrypoint; wget para healthcheck
RUN apk add --no-cache openssl wget

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

USER oficina

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/q/health/live || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
# Shell form: expande ${JAVA_OPTS} em tempo de execução antes de lançar a JVM
CMD ["/bin/sh", "-c", "exec java ${JAVA_OPTS} -jar /app/quarkus-run.jar"]
