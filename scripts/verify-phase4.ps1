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
    -VerifyRedisReservation

if ($LASTEXITCODE -ne 0) {
    throw 'Phase 4 verification failed.'
}

$enrollmentEnvironment = @(& docker compose exec -T enrollment-service sh -c 'printenv | cut -d= -f1 | sort')
if ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect Enrollment Service environment.'
}
foreach ($required in @('REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD')) {
    if ($required -notin $enrollmentEnvironment) {
        throw "Enrollment Service is missing required Redis environment variable: $required"
    }
}
Write-Host 'BOUNDARY Enrollment Service receives the required Redis configuration'
Write-Host 'Phase 4 Redis Lua reservation verification passed.'
