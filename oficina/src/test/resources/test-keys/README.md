# Chaves RSA de teste

Par de chaves **descartável**, versionado de propósito para que `mvn verify` rode
sem nenhum passo prévio de geração — o perfil `test` aponta para cá
(`%test.smallrye.jwt.sign.key.location`, `%test.mp.jwt.verify.publickey.location`
em `application.properties`).

Não é segredo: não assina nada fora do classpath de teste e não tem correspondente
em nenhum ambiente. Scanners de segredo vão sinalizar `privateKey.pem` — este
arquivo existe para responder a esse alerta.

As chaves reais nunca são versionadas:

- **local / docker-compose** — geradas por `generate-keys.sh` ou pelo
  `docker-entrypoint.sh` em `keys/` (ignorado pelo git)
- **Kubernetes** — Secret `oficina-jwt-keys`, montado como volume para que todas as
  réplicas assinem com o mesmo par (ver `k8s/README.md`)
