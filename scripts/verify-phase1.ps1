[CmdletBinding()]
param(
    [switch]$IncludeRecovery,
    [int]$TimeoutSeconds = 180,
    [int]$GatewayHostPort = 18000,
    [int]$NacosHttpHostPort = 18848
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$expectedContainers = @(
    'mysql',
    'redis',
    'rabbitmq',
    'nacos',
    'gateway-service',
    'auth-service',
    'student-service',
    'course-service',
    'enrollment-service',
    'enrollment-worker'
)

$serviceHealthUris = @(
    "http://localhost:$GatewayHostPort/actuator/health",
    'http://localhost:18081/actuator/health',
    'http://localhost:18082/actuator/health',
    'http://localhost:18083/actuator/health',
    'http://localhost:18084/actuator/health',
    'http://localhost:18085/actuator/health'
)

$registeredServices = @(
    'gateway-service',
    'auth-service',
    'student-service',
    'course-service',
    'enrollment-service',
    'enrollment-worker'
)

function Wait-JsonEndpoint {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [Parameter(Mandatory)]
        [scriptblock]$Accept
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-RestMethod -Uri $Uri -TimeoutSec 10
            if (& $Accept $response) {
                return $response
            }
        }
        catch {
            # The dependency may still be starting; retry until the deadline.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    throw "Timed out waiting for $Uri"
}

function Assert-ServiceHealth {
    foreach ($uri in $serviceHealthUris) {
        $null = Wait-JsonEndpoint -Uri $uri -Accept { param($body) $body.status -eq 'UP' }
        Write-Host "UP $uri"
    }
}

function Assert-NacosRegistrations {
    foreach ($serviceName in $registeredServices) {
        $encodedName = [Uri]::EscapeDataString($serviceName)
        $uri = "http://localhost:$NacosHttpHostPort/nacos/v3/client/ns/instance/list?serviceName=$encodedName"
        $null = Wait-JsonEndpoint -Uri $uri -Accept {
            param($body)
            @($body.data | Where-Object { $_.healthy -and $_.enabled }).Count -gt 0
        }
        Write-Host "REGISTERED $serviceName"
    }
}

$running = @(docker compose ps --services --status running)
$missing = @($expectedContainers | Where-Object { $_ -notin $running })
if ($missing.Count -gt 0) {
    throw "Containers not running: $($missing -join ', ')"
}

Assert-ServiceHealth

$gatewaySmoke = Wait-JsonEndpoint -Uri "http://localhost:$GatewayHostPort/_internal/smoke/course" -Accept {
    param($body)
    $body.service -eq 'course-service' -and $body.phase -eq '1-skeleton'
}
Write-Host "ROUTED gateway -> $($gatewaySmoke.service)"

Assert-NacosRegistrations

$flywayCounts = docker compose exec -T mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -Nse "SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_name = '\''flyway_schema_history'\'' AND table_schema LIKE '\''campus_%'\'' GROUP BY table_schema ORDER BY table_schema;"'
if (@($flywayCounts).Count -ne 4) {
    throw "Expected Flyway history in four databases, received: $flywayCounts"
}
Write-Host 'FLYWAY four service-owned schemas found'

$redisPing = docker compose exec -T redis sh -lc 'redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null'
if (($redisPing | Select-Object -Last 1).Trim() -ne 'PONG') {
    throw 'Redis authentication check failed.'
}
Write-Host 'REDIS authenticated PONG'

docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping | Out-Null
Write-Host 'RABBITMQ ping succeeded'

if ($IncludeRecovery) {
    foreach ($dependency in @('mysql', 'redis', 'rabbitmq', 'nacos')) {
        Write-Host "RESTARTING $dependency"
        docker compose restart $dependency | Out-Null
        Assert-ServiceHealth
        if ($dependency -eq 'nacos') {
            Assert-NacosRegistrations
        }
        Write-Host "RECOVERED $dependency"
    }
}

Write-Host 'Phase 1 verification passed.'
