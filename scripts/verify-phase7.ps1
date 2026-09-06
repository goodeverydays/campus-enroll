[CmdletBinding()]
param(
    [int]$GatewayHostPort = 18000,
    [int]$PrometheusHostPort = 19090,
    [int]$GrafanaHostPort = 13000,
    [int]$TimeoutSeconds = 120,
    [int]$LoadRate = 5,
    [string]$LoadDuration = '10s'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Wait-JsonEndpoint {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [Parameter(Mandatory)]
        [scriptblock]$Accept,
        [hashtable]$Headers = @{}
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-RestMethod -Uri $Uri -Headers $Headers -TimeoutSec 10
            if (& $Accept $response) {
                return $response
            }
        } catch {
            # The observability service or its first scrape may still be starting.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    throw "Timed out waiting for $Uri"
}

function Invoke-PrometheusQuery {
    param(
        [Parameter(Mandatory)]
        [string]$Query
    )

    $encoded = [Uri]::EscapeDataString($Query)
    return Invoke-RestMethod `
        -Uri "http://localhost:$PrometheusHostPort/api/v1/query?query=$encoded" `
        -TimeoutSec 10
}

$null = Wait-JsonEndpoint `
    -Uri "http://localhost:$PrometheusHostPort/-/ready" `
    -Accept { param($body) $true }
Write-Host 'PROMETHEUS readiness endpoint succeeded'

$expectedJobs = @(
    'gateway-service',
    'auth-service',
    'student-service',
    'course-service',
    'enrollment-service',
    'enrollment-worker'
)
$targets = Wait-JsonEndpoint `
    -Uri "http://localhost:$PrometheusHostPort/api/v1/targets?state=active" `
    -Accept {
        param($body)
        $healthyJobs = @($body.data.activeTargets `
                | Where-Object { $_.health -eq 'up' } `
                | ForEach-Object { $_.labels.job })
        @($expectedJobs | Where-Object { $_ -notin $healthyJobs }).Count -eq 0
    }
$healthyTargetJobs = @($targets.data.activeTargets `
        | Where-Object { $_.health -eq 'up' } `
        | ForEach-Object { $_.labels.job })
Write-Host "PROMETHEUS six application targets are UP: $($healthyTargetJobs -join ', ')"

$grafanaHealth = Wait-JsonEndpoint `
    -Uri "http://localhost:$GrafanaHostPort/api/health" `
    -Accept { param($body) $body.database -eq 'ok' }
Write-Host "GRAFANA health succeeded: $($grafanaHealth.version)"

$grafanaPassword = (& docker compose exec -T grafana printenv GF_SECURITY_ADMIN_PASSWORD).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($grafanaPassword)) {
    throw 'Could not read the local Grafana verification password.'
}
$grafanaToken = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes("campus_admin:$grafanaPassword"))
$dashboard = Wait-JsonEndpoint `
    -Uri "http://localhost:$GrafanaHostPort/api/dashboards/uid/campus-enroll-overview" `
    -Headers @{ Authorization = "Basic $grafanaToken" } `
    -Accept { param($body) $body.dashboard.title -eq 'CampusEnroll Overview' }
if ($dashboard.meta.folderTitle -ne 'CampusEnroll') {
    throw 'Grafana dashboard was not provisioned in the CampusEnroll folder.'
}
Write-Host 'GRAFANA Prometheus datasource and CampusEnroll dashboard are provisioned'

$catalog = Invoke-RestMethod `
    -Uri "http://localhost:$GatewayHostPort/api/v1/courses?page=0&size=20" `
    -TimeoutSec 10
if ($catalog.code -ne 0) {
    throw 'Gateway catalog probe did not return the standard success envelope.'
}

$metricDeadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 2
    $httpMetric = Invoke-PrometheusQuery `
        -Query 'sum(http_server_requests_seconds_count{job="gateway-service"})'
    $publisherMetric = Invoke-PrometheusQuery `
        -Query 'count(campus_enrollment_publish_total)'
    $workerMetric = Invoke-PrometheusQuery `
        -Query 'count(campus_enrollment_worker_messages_total)'
    $metricsReady = @($httpMetric.data.result).Count -eq 1 `
        -and [double]$httpMetric.data.result[0].value[1] -gt 0 `
        -and @($publisherMetric.data.result).Count -eq 1 `
        -and [double]$publisherMetric.data.result[0].value[1] -ge 7 `
        -and @($workerMetric.data.result).Count -eq 1 `
        -and [double]$workerMetric.data.result[0].value[1] -ge 5
} while (-not $metricsReady -and [DateTimeOffset]::UtcNow -lt $metricDeadline)
if (-not $metricsReady) {
    throw 'Prometheus did not expose the HTTP and enrollment business metrics before the deadline.'
}
Write-Host 'METRICS HTTP traffic and low-cardinality enrollment outcomes are queryable'

$resultsDirectory = Join-Path $PSScriptRoot '..\load-tests\results'
$null = New-Item -ItemType Directory -Path $resultsDirectory -Force
& docker compose --profile load-test run --rm `
    -e "K6_RATE=$LoadRate" `
    -e "K6_DURATION=$LoadDuration" `
    k6
if ($LASTEXITCODE -ne 0) {
    throw 'k6 catalog baseline failed its thresholds.'
}

$summaryPath = Join-Path $resultsDirectory 'catalog-summary.json'
if (-not (Test-Path -LiteralPath $summaryPath -PathType Leaf)) {
    throw 'k6 did not write the catalog summary artifact.'
}
$summary = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
$iterations = [int]$summary.metrics.iterations.values.count
$p95Milliseconds = [double]$summary.metrics.http_req_duration.values.'p(95)'
$failureRate = [double]$summary.metrics.http_req_failed.values.rate
if ($iterations -lt 1) {
    throw 'k6 completed without executing any iterations.'
}
Write-Host ("K6 catalog baseline: iterations={0}, p95={1:N2}ms, failureRate={2:P2}" -f `
        $iterations, $p95Milliseconds, $failureRate)
Write-Host "REPORT $summaryPath"
Write-Host 'Phase 7 observability and load baseline verification passed.'
