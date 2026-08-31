# Phase 1 verification

## Automated build checks

```powershell
.\mvnw.cmd clean verify
docker compose --env-file .env.example config --quiet
```

GitHub Actions runs the same checks with Java 21 for every pull request to
`main` and for pushes to the maintained branch prefixes.

## Runtime smoke check

After copying `.env.example` to `.env` and replacing all example secrets:

```powershell
docker compose up -d --build
.\scripts\verify-phase1.ps1
```

脚本默认使用 Gateway `18000` 与 Nacos HTTP `18848`。如在 `.env` 中覆盖了这两个
宿主机端口，请同时传入 `-GatewayHostPort` 与 `-NacosHttpHostPort`。

The script verifies:

- all ten Compose services are running;
- all six Java services report `UP`;
- Gateway resolves `course-service` through Nacos;
- all six Java services have healthy Nacos instances;
- all four logical databases contain Flyway history;
- Redis password authentication succeeds;
- RabbitMQ diagnostics succeed.

## Recovery check

```powershell
.\scripts\verify-phase1.ps1 -IncludeRecovery
```

This opt-in check restarts MySQL, Redis, RabbitMQ, and Nacos one at a time and
waits for service health to recover. It changes local container state and must
not be run against shared or production environments.
