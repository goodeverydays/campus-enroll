# Phase 7 observability and load testing

## Scope

Phase 7 adds a reproducible local observability stack and a safe catalog-read
baseline. It does not change the Phase 6 delivery, retry, acknowledgement, or
idempotency rules. Write-contention tests must use disposable students and course
fixtures; they are intentionally kept separate from the default baseline so a
developer cannot mutate normal local enrollment data by running one command.

## Topology

Prometheus scrapes `/actuator/prometheus` from the six Java services every five
seconds. Grafana provisions the Prometheus datasource and the
`CampusEnroll Overview` dashboard from version-controlled files. Both host ports
bind to `127.0.0.1` by default.

| Component | Local address | Retention / persistence |
| --- | --- | --- |
| Prometheus | `http://localhost:19090` | Seven days in `prometheus-data` |
| Grafana | `http://localhost:13000` | Dashboard state in `grafana-data` |

Grafana uses an embedded SQLite database only for this local single-instance
environment. A production deployment should use a managed or highly available
Grafana database and should protect both observability endpoints with network and
identity controls.

## Business metrics

The custom counters deliberately use only bounded `outcome` values. Request IDs,
student IDs, and course IDs remain in logs or databases and never become metric
labels.

| Prometheus metric | Outcomes |
| --- | --- |
| `campus_enrollment_publish_total` | `confirmed`, `nack`, `returned`, `serialization_error`, `interrupted`, `confirm_failure`, `broker_error` |
| `campus_enrollment_worker_messages_total` | `processed`, `retry`, `dead_letter`, `invalid_json`, `requeue` |

The dashboard also shows per-service HTTP request rate, HTTP p95 latency, 5xx
ratio, and JVM heap utilization.

## Run the baseline

Start the normal stack, then run:

```powershell
.\scripts\verify-phase7.ps1
```

The script validates six healthy Prometheus targets, the provisioned Grafana
dashboard, HTTP and business metric queries, and a small k6 catalog-read test. It
writes the machine-readable result to
`load-tests/results/catalog-summary.json`; generated result files are ignored by
Git.

The load profile uses a constant arrival rate so the requested iteration rate is
independent of response latency. Defaults are intentionally modest for laptops:

```powershell
.\scripts\verify-phase7.ps1 -LoadRate 20 -LoadDuration 30s
```

You may also run k6 directly:

```powershell
$env:K6_RATE = '20'
$env:K6_DURATION = '30s'
docker compose --profile load-test run --rm k6
```

Default pass/fail thresholds are:

- more than 99% of checks pass;
- HTTP failure rate stays below 1%;
- course-catalog p95 stays below 1500 ms.

Treat these as a repeatable development baseline, not a production capacity
claim. Record CPU limits, memory limits, runner hardware, dataset size, rate,
duration, p50/p95/p99, error rate, and dropped iterations before comparing runs.
Increase one independent variable at a time and keep the same fixture snapshot.
