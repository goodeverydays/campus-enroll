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
writes the raw k6 summary, a normalized run record, and Markdown/CSV/JSON reports
under `load-tests/results`; generated result files are ignored by Git. The
normalized record includes the Git commit, workload parameters, iteration and
request counts, check and failure rates, dropped iterations, and
p50/p90/p95/p99/max latency.

The load profile uses a constant arrival rate so the requested iteration rate is
independent of response latency. Defaults are intentionally modest for laptops:

```powershell
.\scripts\verify-phase7.ps1 -LoadRate 20 -LoadDuration 30s
```

You may also run k6 directly:

```powershell
$env:LOAD_RATE = '20'
$env:LOAD_DURATION = '30s'
docker compose --profile load-test run --rm --no-deps k6
```

## Run a repeatable experiment

Keep the normal application stack running, then execute a rate matrix with
multiple repetitions:

```powershell
.\scripts\run-phase7-experiment.ps1 `
  -Rates 5,10,20 `
  -Duration 30s `
  -Repetitions 3
```

Each invocation creates an immutable timestamped directory under
`load-tests/results/experiments`. It preserves both the raw k6 summaries and the
normalized records, then generates:

- `experiment-summary.md` for a quick review;
- `experiment-summary.csv` for spreadsheet analysis;
- `experiment-summary.json` for automated comparisons.

Rates run sequentially so one workload does not overlap another. A failed k6
threshold stops the matrix immediately and preserves results from completed runs.
The normal CI workflow intentionally runs only the lightweight default baseline;
its reports are retained as a GitHub Actions artifact for 14 days. Longer matrices
should be run deliberately on controlled hardware.

Default pass/fail thresholds are:

- more than 99% of checks pass;
- HTTP failure rate stays below 1%;
- course-catalog p95 stays below 1500 ms.

Treat these as a repeatable development baseline, not a production capacity
claim. Record CPU limits, memory limits, runner hardware, dataset size, rate,
duration, p50/p95/p99, error rate, and dropped iterations before comparing runs.
Increase one independent variable at a time and keep the same fixture snapshot.
Use at least three repetitions and compare the average and worst p95 rather than
selecting the fastest run.
