[CmdletBinding()]
param(
    [int]$GatewayHostPort = 18000,
    [int]$AuthHostPort = 18081,
    [int]$StudentHostPort = 18082,
    [int]$EnrollmentHostPort = 18084
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

& "$PSScriptRoot\verify-phase3.ps1" `
    -GatewayHostPort $GatewayHostPort `
    -AuthHostPort $AuthHostPort `
    -StudentHostPort $StudentHostPort `
    -EnrollmentHostPort $EnrollmentHostPort `
    -VerifyRedisReservation `
    -VerifyAsyncAcceptance

if ($LASTEXITCODE -ne 0) {
    throw 'Phase 5 enrollment flow verification failed.'
}

foreach ($service in @('enrollment-service', 'enrollment-worker')) {
    $environment = @(& docker compose exec -T $service sh -c 'printenv | cut -d= -f1 | sort')
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect $service environment."
    }
    foreach ($required in @(
            'RABBITMQ_HOST',
            'RABBITMQ_PORT',
            'RABBITMQ_USER',
            'RABBITMQ_PASSWORD',
            'RABBITMQ_VHOST')) {
        if ($required -notin $environment) {
            throw "$service is missing required RabbitMQ environment variable: $required"
        }
    }
}
Write-Host 'BOUNDARY Enrollment Service and Worker both receive RabbitMQ configuration'

$queueRows = @(& docker compose exec -T rabbitmq `
    rabbitmqctl -q list_queues -p /campus-enroll name messages consumers)
if ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect RabbitMQ queues.'
}
$queue = $queueRows | Where-Object { $_ -match '^campus\.enrollment\.queue\s+' } | Select-Object -First 1
if ($null -eq $queue) {
    throw 'RabbitMQ enrollment queue was not declared.'
}
$queueColumns = @($queue -split '\s+')
if ($queueColumns.Count -ne 3 -or [int]$queueColumns[1] -ne 0 -or [int]$queueColumns[2] -lt 1) {
    throw "RabbitMQ enrollment queue is not drained or has no consumer: $queue"
}

Write-Host 'RABBITMQ durable enrollment queue is drained and has an active consumer'
Write-Host 'Phase 5 RabbitMQ asynchronous enrollment verification passed.'
