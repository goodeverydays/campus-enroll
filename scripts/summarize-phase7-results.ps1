[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string[]]$ResultFiles,
    [Parameter(Mandatory)]
    [string]$OutputDirectory,
    [string]$ReportName = 'experiment-summary'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($ResultFiles.Count -eq 0) {
    throw 'At least one normalized Phase 7 result file is required.'
}
if ($ReportName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') {
    throw 'ReportName may contain only letters, digits, dots, underscores, and hyphens.'
}

function Format-Invariant {
    param(
        [double]$Value,
        [string]$Pattern = '0.00'
    )

    return $Value.ToString($Pattern, [Globalization.CultureInfo]::InvariantCulture)
}

function Format-UtcTimestamp {
    param(
        [Parameter(Mandatory)]
        $Value
    )

    if ($Value -is [DateTimeOffset]) {
        return $Value.ToUniversalTime().ToString('o')
    }
    if ($Value -is [DateTime]) {
        return $Value.ToUniversalTime().ToString('o')
    }
    return [string]$Value
}

$records = @(
    foreach ($resultFile in $ResultFiles) {
        $resolvedPath = Resolve-Path -LiteralPath $resultFile -ErrorAction Stop
        $record = Get-Content -Raw -LiteralPath $resolvedPath | ConvertFrom-Json
        if ([int]$record.schemaVersion -ne 1) {
            throw "Unsupported Phase 7 result schema in $resolvedPath"
        }
        if ($record.profile -ne 'catalog-read') {
            throw "Unsupported Phase 7 workload profile in $resolvedPath"
        }
        $record
    }
)

$summaries = @(
    $records |
        Group-Object -Property ratePerSecond |
        Sort-Object { [double]$_.Name } |
        ForEach-Object {
            $groupRecords = @($_.Group)
            $p95Values = @($groupRecords | ForEach-Object { [double]$_.metrics.latencyMs.p95 })
            $p99Values = @($groupRecords | ForEach-Object { [double]$_.metrics.latencyMs.p99 })
            $failureRates = @($groupRecords | ForEach-Object { [double]$_.metrics.failureRate })
            [pscustomobject][ordered]@{
                ratePerSecond = [int]$_.Name
                runs = $groupRecords.Count
                totalIterations = [int](($groupRecords | Measure-Object -Property { $_.metrics.iterations } -Sum).Sum)
                averageP95Milliseconds = [double](($p95Values | Measure-Object -Average).Average)
                worstP95Milliseconds = [double](($p95Values | Measure-Object -Maximum).Maximum)
                averageP99Milliseconds = [double](($p99Values | Measure-Object -Average).Average)
                averageFailureRate = [double](($failureRates | Measure-Object -Average).Average)
                droppedIterations = [int](($groupRecords | Measure-Object -Property { $_.metrics.droppedIterations } -Sum).Sum)
                passed = @($groupRecords | Where-Object { -not $_.thresholds.passed }).Count -eq 0
            }
        }
)

$null = New-Item -ItemType Directory -Path $OutputDirectory -Force
$jsonPath = Join-Path $OutputDirectory "$ReportName.json"
$csvPath = Join-Path $OutputDirectory "$ReportName.csv"
$markdownPath = Join-Path $OutputDirectory "$ReportName.md"

$report = [pscustomobject][ordered]@{
    schemaVersion = 1
    generatedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    profile = 'catalog-read'
    runCount = $records.Count
    summaries = $summaries
    records = $records
}
$json = $report | ConvertTo-Json -Depth 12
[IO.File]::WriteAllText($jsonPath, $json, [Text.UTF8Encoding]::new($false))

$csvRows = $records |
    Sort-Object ratePerSecond, repetition |
    ForEach-Object {
        [pscustomobject][ordered]@{
            experimentId = $_.experimentId
            repetition = $_.repetition
            recordedAtUtc = Format-UtcTimestamp $_.recordedAtUtc
            gitCommit = $_.gitCommit
            ratePerSecond = $_.ratePerSecond
            duration = $_.duration
            iterations = $_.metrics.iterations
            requests = $_.metrics.requests
            checksRate = $_.metrics.checksRate
            failureRate = $_.metrics.failureRate
            droppedIterations = $_.metrics.droppedIterations
            averageMilliseconds = $_.metrics.latencyMs.average
            p50Milliseconds = $_.metrics.latencyMs.p50
            p90Milliseconds = $_.metrics.latencyMs.p90
            p95Milliseconds = $_.metrics.latencyMs.p95
            p99Milliseconds = $_.metrics.latencyMs.p99
            maxMilliseconds = $_.metrics.latencyMs.max
            passed = $_.thresholds.passed
        }
    }
$csv = ($csvRows | ConvertTo-Csv -NoTypeInformation) -join [Environment]::NewLine
[IO.File]::WriteAllText($csvPath, "$csv$([Environment]::NewLine)", [Text.UTF8Encoding]::new($false))

$markdown = [Collections.Generic.List[string]]::new()
$markdown.Add('# CampusEnroll Phase 7 load experiment')
$markdown.Add('')
$markdown.Add("- Generated at (UTC): $($report.generatedAtUtc)")
$markdown.Add("- Workload: read-only course catalog")
$markdown.Add("- Runs: $($records.Count)")
$markdown.Add('')
$markdown.Add('| Rate (iter/s) | Runs | Iterations | Avg p95 (ms) | Worst p95 (ms) | Avg p99 (ms) | Avg failure | Dropped | Result |')
$markdown.Add('| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | :--- |')
foreach ($summary in $summaries) {
    $result = if ($summary.passed) { 'PASS' } else { 'FAIL' }
    $failurePercent = Format-Invariant -Value ($summary.averageFailureRate * 100)
    $markdown.Add(('| {0} | {1} | {2} | {3} | {4} | {5} | {6}% | {7} | {8} |' -f `
            $summary.ratePerSecond,
            $summary.runs,
            $summary.totalIterations,
            (Format-Invariant $summary.averageP95Milliseconds),
            (Format-Invariant $summary.worstP95Milliseconds),
            (Format-Invariant $summary.averageP99Milliseconds),
            $failurePercent,
            $summary.droppedIterations,
            $result))
}
$markdown.Add('')
$markdown.Add('This development baseline is not a production capacity claim. Compare runs only when hardware, container limits, dataset, duration, and commit are controlled.')
[IO.File]::WriteAllText(
    $markdownPath,
    (($markdown -join [Environment]::NewLine) + [Environment]::NewLine),
    [Text.UTF8Encoding]::new($false)
)

Write-Host "PHASE7 REPORT JSON $jsonPath"
Write-Host "PHASE7 REPORT CSV $csvPath"
Write-Host "PHASE7 REPORT MARKDOWN $markdownPath"
