# VPS Runbook

## 1) Deployment shape

BenchLab follows the same runtime-only VPS pattern used by the other Spring APIs:

- GitHub Actions runs tests and SonarCloud.
- GitHub Actions publishes three GHCR images: API, `runner-worker`, and `benchlab-web`.
- The VPS receives only `/deploy` files and a generated `.env`.
- The VPS runs `docker compose pull` and `docker compose up -d`; it does not build Java or Go code.

## 2) GitHub environment

Create a GitHub Environment named `prod`.

Recommended variables:

```text
APP_HOST_PORT=127.0.0.1:8083
API_HEALTH_URL=http://127.0.0.1:8083/management/health
WEB_HOST_PORT=127.0.0.1:3003
COMPOSE_PROJECT_NAME=benchlab
GHCR_USERNAME=Gabrielvcg
JHIPSTER_MAIL_BASE_URL=https://benchlab.example.com
POSTGRES_DB=benchLab
POSTGRES_HOST_PORT=127.0.0.1:5435
POSTGRES_USER=benchLab
RABBITMQ_MANAGEMENT_HOST_PORT=127.0.0.1:15673
RABBITMQ_USER=benchlab
SONAR_ORGANIZATION=gabrielvcg
SONAR_PROJECT_KEY=Gabrielvcg_BenchLab
SPRING_DATASOURCE_URL=jdbc:postgresql://postgresql:5432/benchLab
SPRING_DATASOURCE_USERNAME=benchLab
SPRING_LIQUIBASE_ENABLED=true
SPRING_LIQUIBASE_CONTEXTS=prod
SPRING_PROFILES_ACTIVE=prod
VPS_HOST=62.171.150.163
VPS_PATH=/opt/benchlab/app
VPS_PORT=22
VPS_USER=vacaro
WORKER_TEMP_PATH=/opt/benchlab/worker-tmp
BENCHLAB_LIMITS_MAX_SOURCE_BYTES=65536
BENCHLAB_LIMITS_MAX_TIMEOUT_MS=30000
BENCHLAB_LIMITS_MAX_MEMORY_MB=512
BENCHLAB_LIMITS_MAX_CPU_LIMIT=2.0
BENCHLAB_LIMITS_MAX_ITERATIONS=10
BENCHLAB_LIMITS_MAX_WARMUP_ITERATIONS=3
BENCHLAB_LIMITS_MAX_DATASET_SIZE=25000000
BENCHLAB_LIMITS_MAX_OUTSTANDING_RUNS_PER_USER=32
```

Required secrets:

```text
BENCHLAB_ADMIN_PASSWORD
BENCHLAB_WORKER_TOKEN
GHCR_READ_TOKEN
JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET
POSTGRES_PASSWORD
RABBITMQ_PASSWORD
SONAR_TOKEN
SPRING_DATASOURCE_PASSWORD
VPS_SSH_KEY
```

`SPRING_DATASOURCE_PASSWORD` can match `POSTGRES_PASSWORD`.

`BENCHLAB_ADMIN_PASSWORD` is required in production. On startup the API rotates the seeded JHipster `admin` user's password to this value and refuses to start if the value is missing or still set to `admin`.

## 3) Runtime environment

The generated VPS `.env` has the same shape as:

```env
BENCHLAB_API_IMAGE=ghcr.io/example/benchlab-api:latest
BENCHLAB_WORKER_IMAGE=ghcr.io/example/benchlab-runner-worker:latest
BENCHLAB_WEB_IMAGE=ghcr.io/example/benchlab-web:latest
POSTGRES_PASSWORD=change-me
RABBITMQ_USER=benchlab
RABBITMQ_PASSWORD=change-me
BENCHLAB_WORKER_TOKEN=change-me
BENCHLAB_SECURITY_ADMIN_PASSWORD=change-me
```

Use [`/deploy/.env.example`](../../deploy/.env.example) as the complete reference. Do not commit a real `.env`.

## 4) Deploy

Push to `main` after the `prod` environment variables and secrets exist. The workflow at [`.github/workflows/ci-cd.yml`](../../.github/workflows/ci-cd.yml) will build, publish, sync, and restart the stack.

For manual VPS recovery:

```bash
cd /opt/benchlab/app
docker compose --env-file .env -f deploy/docker-compose.yml pull
docker compose --env-file .env -f deploy/docker-compose.yml up -d
```

## 5) Basic checks

```powershell
./scripts/smoke-health.ps1
./scripts/smoke-benchmark.ps1 -Iterations 5
```

## 6) Daily backup

Use host cron/task scheduler to run:

```powershell
./scripts/backup-postgres.ps1
```

Keep backups in a folder mounted to persistent VPS storage.

## 7) Notes for the worker

The worker container mounts `/var/run/docker.sock` and uses `WORKER_TEMP_PATH` for source files passed into short-lived runner containers. Keep `WORKER_TEMP_PATH` as an absolute path that exists both on the host and inside the worker container.

Compose limits the worker service itself to 256 MB and 128 PIDs. Its readiness check calls `/health/ready` on port 8081 and becomes healthy only after RabbitMQ consumption is active. These worker-container limits are separate from the CPU/memory limits applied to each sibling runner container through the mounted Docker socket.

The Docker socket gives the worker host-level Docker control. Restrict host access, image provenance, and deployment credentials accordingly; the service-container resource limit is not a security boundary for the Docker daemon.

Queue publication/callback atomicity and bounded retry/DLQ behavior remain the next milestone. See [`/docs/architecture/queue-reliability-next.md`](../architecture/queue-reliability-next.md).

## 8) Web dashboard

The web dashboard is exposed internally on `WEB_HOST_PORT` and should be published through host-level Nginx. The reference config is [`/deploy/nginx/benchlab.conf`](../../deploy/nginx/benchlab.conf), with `/` going to the web container and `/api/**` going to the API.
