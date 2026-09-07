[CmdletBinding()]
param(
    [int]$GatewayHostPort = 18000,
    [int]$AuthHostPort = 18081,
    [int]$StudentHostPort = 18082,
    [int]$EnrollmentHostPort = 18084,
    [int]$RabbitManagementHostPort = 25673,
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

& "$PSScriptRoot\verify-phase5.ps1" `
    -GatewayHostPort $GatewayHostPort `
    -AuthHostPort $AuthHostPort `
    -StudentHostPort $StudentHostPort `
    -EnrollmentHostPort $EnrollmentHostPort `
    -VerifyReliableCapacity

if ($LASTEXITCODE -ne 0) {
    throw 'Phase 6 baseline enrollment flow verification failed.'
}

$workerEnvironment = @(& docker compose exec -T enrollment-worker sh -c 'printenv | cut -d= -f1 | sort')
foreach ($required in @(
        'ENROLLMENT_RETRY_EXCHANGE',
        'ENROLLMENT_RETRY_QUEUE',
        'ENROLLMENT_RETRY_ROUTING_KEY',
        'ENROLLMENT_DLX',
        'ENROLLMENT_DLQ',
        'ENROLLMENT_DLQ_ROUTING_KEY',
        'ENROLLMENT_RETRY_DELAY',
        'ENROLLMENT_MAX_ATTEMPTS',
        'ENROLLMENT_CONFIRM_TIMEOUT')) {
    if ($required -notin $workerEnvironment) {
        throw "Enrollment Worker is missing reliability configuration: $required"
    }
}
Write-Host 'BOUNDARY Worker receives retry, DLQ, and confirm configuration'

$queueRows = @(& docker compose exec -T rabbitmq `
    rabbitmqctl -q list_queues -p /campus-enroll name messages consumers)
if ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect RabbitMQ reliability queues.'
}
$queueState = @{}
foreach ($row in $queueRows) {
    $columns = @($row -split '\s+')
    if ($columns.Count -eq 3 -and $columns[0] -like 'campus.enrollment.*') {
        $queueState[$columns[0]] = @([int]$columns[1], [int]$columns[2])
    }
}
foreach ($queueName in @(
        'campus.enrollment.queue',
        'campus.enrollment.retry.queue',
        'campus.enrollment.dlq')) {
    if (-not $queueState.ContainsKey($queueName)) {
        throw "RabbitMQ reliability queue is missing: $queueName"
    }
}
if ($queueState['campus.enrollment.queue'][0] -ne 0 `
        -or $queueState['campus.enrollment.retry.queue'][0] -ne 0 `
        -or $queueState['campus.enrollment.dlq'][0] -ne 0) {
    throw 'Reliability verification requires initially empty enrollment queues.'
}

$rabbitUser = (& docker compose exec -T rabbitmq printenv RABBITMQ_DEFAULT_USER).Trim()
$rabbitPassword = (& docker compose exec -T rabbitmq printenv RABBITMQ_DEFAULT_PASS).Trim()
$basicToken = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes("${rabbitUser}:${rabbitPassword}"))
$headers = @{ Authorization = "Basic $basicToken" }
$poisonRequestId = [Guid]::NewGuid().ToString()
$poisonTask = @{
    requestId = $poisonRequestId
    studentId = 999999991
    courseId = 999999992
    offeringId = 999999993
    semesterId = 999999994
    schedules = @()
    requestedAt = [DateTimeOffset]::UtcNow.ToString('o')
} | ConvertTo-Json -Compress
$publishBody = @{
    properties = @{
        delivery_mode = 2
        message_id = $poisonRequestId
        headers = @{ 'x-enrollment-attempt' = 1 }
    }
    routing_key = 'campus.enrollment.requested'
    payload = $poisonTask
    payload_encoding = 'string'
} | ConvertTo-Json -Depth 6 -Compress
$publishResult = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:$RabbitManagementHostPort/api/exchanges/%2Fcampus-enroll/campus.enrollment.exchange/publish" `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body $publishBody `
    -TimeoutSec 10
if (-not $publishResult.routed) {
    throw 'RabbitMQ management API did not route the retry probe.'
}

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Milliseconds 500
    $rows = @(& docker compose exec -T rabbitmq `
        rabbitmqctl -q list_queues -p /campus-enroll name messages consumers)
    $deadLetterRow = $rows | Where-Object { $_ -match '^campus\.enrollment\.dlq\s+' } | Select-Object -First 1
    if ($deadLetterRow) {
        $columns = @($deadLetterRow -split '\s+')
        if ($columns.Count -eq 3 -and [int]$columns[1] -eq 1) {
            break
        }
    }
} while ([DateTimeOffset]::UtcNow -lt $deadline)
$deadLetterColumns = @($deadLetterRow -split '\s+')
if (-not $deadLetterRow -or $deadLetterColumns.Count -ne 3 -or [int]$deadLetterColumns[1] -ne 1) {
    throw 'Retry probe did not reach the dead-letter queue before the deadline.'
}

$getBody = @{
    count = 1
    ackmode = 'ack_requeue_false'
    encoding = 'auto'
    truncate = 50000
} | ConvertTo-Json -Compress
$deadLetters = @(Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:$RabbitManagementHostPort/api/queues/%2Fcampus-enroll/campus.enrollment.dlq/get" `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body $getBody `
    -TimeoutSec 10)
if ($deadLetters.Count -ne 1) {
    throw 'Could not consume the retry probe from the dead-letter queue.'
}
$deadLetterPayload = $deadLetters[0].payload | ConvertFrom-Json
$attempt = [int]$deadLetters[0].properties.headers.'x-enrollment-attempt'
if ($deadLetterPayload.requestId -ne $poisonRequestId -or $attempt -ne 3) {
    throw 'Dead-letter probe did not preserve request identity or bounded retry count.'
}
Write-Host 'RABBITMQ poison task retried twice, reached DLQ on attempt three, and was cleaned up'

$finalRows = @(& docker compose exec -T rabbitmq `
    rabbitmqctl -q list_queues -p /campus-enroll name messages consumers)
foreach ($queueName in @(
        'campus.enrollment.queue',
        'campus.enrollment.retry.queue',
        'campus.enrollment.dlq')) {
    $row = $finalRows | Where-Object { $_ -match "^$([Regex]::Escape($queueName))\s+" } | Select-Object -First 1
    $columns = @($row -split '\s+')
    if ($columns.Count -ne 3 -or [int]$columns[1] -ne 0) {
        throw "RabbitMQ queue was not clean after verification: $queueName"
    }
}

Write-Host 'Phase 6 reliable RabbitMQ verification passed.'
