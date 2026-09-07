[CmdletBinding()]
param(
    [int[]]$Rates = @(5, 10, 20),
    [string]$Duration = '30s',
    [ValidateRange(1, 20)]
    [int]$Repetitions = 3,
    [string]$OutputRoot = '',
    [int]$GatewayHostPort = 18000,
    [int]$PrometheusHostPort = 19090,
    [int]$GrafanaHostPort = 13000,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($Rates.Count -eq 0 -or @($Rates | Where-Object { $_ -le 0 }).Count -gt 0) {
    throw 'Rates must contain one or more positive integers.'
}
if ($Duration -notmatch '^\d+(ms|s|m|h)$') {
    throw 'Duration must use a k6 duration such as 30s, 2m, or 1h.'
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $PSScriptRoot '..\load-tests\results\experiments'
}

$experimentId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$experimentDirectory = Join-Path $OutputRoot $experimentId
$null = New-Item -ItemType Directory -Path $experimentDirectory -Force
$normalizedResults = [Collections.Generic.List[string]]::new()
$verifyScript = Join-Path $PSScriptRoot 'verify-phase7.ps1'
$baselineDirectory = Join-Path $PSScriptRoot '..\load-tests\results'

foreach ($rate in ($Rates | Sort-Object -Unique)) {
    for ($repetition = 1; $repetition -le $Repetitions; $repetition++) {
        Write-Host "EXPERIMENT rate=$rate iteration/s repetition=$repetition/$Repetitions duration=$Duration"
        & $verifyScript `
            -GatewayHostPort $GatewayHostPort `
            -PrometheusHostPort $PrometheusHostPort `
            -GrafanaHostPort $GrafanaHostPort `
            -TimeoutSeconds $TimeoutSeconds `
            -LoadRate $rate `
            -LoadDuration $Duration

        $rawSource = Join-Path $baselineDirectory 'catalog-summary.json'
        $recordSource = Join-Path $baselineDirectory 'catalog-run.json'
        $fileStem = 'rate-{0:D5}-run-{1:D2}' -f $rate, $repetition
        $rawDestination = Join-Path $experimentDirectory "$fileStem.raw.json"
        $recordDestination = Join-Path $experimentDirectory "$fileStem.json"
        Copy-Item -LiteralPath $rawSource -Destination $rawDestination -Force

        $record = Get-Content -Raw -LiteralPath $recordSource | ConvertFrom-Json
        $record.experimentId = $experimentId
        $record.repetition = $repetition
        $recordJson = $record | ConvertTo-Json -Depth 10
        [IO.File]::WriteAllText(
            $recordDestination,
            $recordJson,
            [Text.UTF8Encoding]::new($false)
        )
        $normalizedResults.Add($recordDestination)
    }
}

& (Join-Path $PSScriptRoot 'summarize-phase7-results.ps1') `
    -ResultFiles $normalizedResults.ToArray() `
    -OutputDirectory $experimentDirectory

Write-Host "PHASE7 EXPERIMENT COMPLETE $experimentDirectory"
