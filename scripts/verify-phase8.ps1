[CmdletBinding()]
param(
    [int]$FrontendHostPort = 15173,
    [int]$AuthHostPort = 18081,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-JsonResponse {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [ValidateSet('Get', 'Post')]
        [string]$Method = 'Get',
        [hashtable]$Headers = @{},
        [AllowNull()]
        [object]$Body = $null
    )

    $parameters = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        SkipHttpErrorCheck = $true
        TimeoutSec = 10
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    $response = Invoke-WebRequest @parameters
    $body = if ([string]::IsNullOrWhiteSpace($response.Content)) {
        $null
    } else {
        $response.Content | ConvertFrom-Json
    }
    return [pscustomobject]@{
        StatusCode = [int]$response.StatusCode
        Headers = $response.Headers
        Body = $body
    }
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory)]
        [string]$Database,
        [Parameter(Mandatory)]
        [string]$Sql
    )

    $mysqlCommand = 'exec mysql -ucampus_app -p"$MYSQL_PASSWORD" ' + $Database
    $result = @($Sql | & docker compose exec -T mysql sh -c $mysqlCommand 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed for database $Database."
    }
    return $result
}

$frontendBaseUrl = "http://localhost:$FrontendHostPort"
$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    try {
        $health = Invoke-WebRequest -Uri "$frontendBaseUrl/healthz" -TimeoutSec 5
        $frontendReady = $health.StatusCode -eq 200 -and $health.Content.Trim() -eq 'ok'
    } catch {
        $frontendReady = $false
    }
    if (-not $frontendReady) {
        Start-Sleep -Seconds 2
    }
} while (-not $frontendReady -and [DateTimeOffset]::UtcNow -lt $deadline)
if (-not $frontendReady) {
    throw 'Frontend health endpoint did not become ready.'
}
Write-Host 'FRONTEND Nginx health endpoint succeeded'

$homePage = Invoke-WebRequest -Uri "$frontendBaseUrl/" -TimeoutSec 10
$deepLink = Invoke-WebRequest -Uri "$frontendBaseUrl/courses/987654" -TimeoutSec 10
if ($homePage.StatusCode -ne 200 `
        -or $homePage.Content -notmatch '<div id="app"></div>' `
        -or $homePage.Content -notmatch '/assets/index-' `
        -or $deepLink.Content -ne $homePage.Content) {
    throw 'Frontend production bundle or SPA deep-link fallback check failed.'
}
Write-Host 'FRONTEND production assets and SPA deep-link fallback succeeded'

$traceId = "phase8:$([Guid]::NewGuid().ToString('N'))"
$catalog = Invoke-JsonResponse `
    -Uri "$frontendBaseUrl/api/v1/courses?page=0&size=1" `
    -Headers @{ 'X-Request-Id' = $traceId }
if ($catalog.StatusCode -ne 200 `
        -or $catalog.Body.code -ne 0 `
        -or $catalog.Body.requestId -ne $traceId `
        -or [string]$catalog.Headers['X-Request-Id'] -ne $traceId) {
    throw 'Frontend API proxy or Gateway request-ID propagation check failed.'
}
Write-Host 'GATEWAY request ID propagated through the frontend proxy and response envelope'

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$legacySystem = 'phase8-verifier'
$legacyUserId = "phase8-user-$suffix"
try {
    $systemKey = (& docker compose exec -T auth-service printenv LEGACY_SYSTEM_API_KEY).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($systemKey)) {
        throw 'Could not read the local Auth Service verification key.'
    }
    $ticket = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$AuthHostPort/internal/v1/auth/sso-tickets" `
        -Headers @{ 'X-Legacy-System-Key' = $systemKey } `
        -Body @{ legacySystem = $legacySystem; legacyUserId = $legacyUserId; studentId = 980000001 }
    if ($ticket.StatusCode -ne 200 -or $ticket.Body.code -ne 0) {
        throw 'Phase 8 temporary SSO ticket issuance failed.'
    }
    $exchange = Invoke-JsonResponse `
        -Method Post `
        -Uri "$frontendBaseUrl/api/v1/auth/sso/exchange" `
        -Body @{ ticket = $ticket.Body.data.ticket }
    if ($exchange.StatusCode -ne 200 -or $exchange.Body.code -ne 0) {
        throw 'Phase 8 SSO exchange through the frontend proxy failed.'
    }
    $accessToken = [string]$exchange.Body.data.accessToken

    $client = [Net.Http.HttpClient]::new()
    $tasks = [Collections.Generic.List[Threading.Tasks.Task[Net.Http.HttpResponseMessage]]]::new()
    $requests = [Collections.Generic.List[Net.Http.HttpRequestMessage]]::new()
    try {
        for ($index = 0; $index -lt 30; $index++) {
            $request = [Net.Http.HttpRequestMessage]::new(
                [Net.Http.HttpMethod]::Post,
                "$frontendBaseUrl/api/v1/enrollments")
            $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new(
                'Bearer', $accessToken)
            $request.Headers.Add('Idempotency-Key', "phase8-$suffix-$index")
            $request.Content = [Net.Http.StringContent]::new(
                '{"courseId":980000002}',
                [Text.Encoding]::UTF8,
                'application/json')
            $requests.Add($request)
            $tasks.Add($client.SendAsync($request))
        }
        [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]$tasks.ToArray())
        $statusCodes = @($tasks | ForEach-Object { [int]$_.Result.StatusCode })
        $limitedCount = @($statusCodes | Where-Object { $_ -eq 429 }).Count
        $forwardedCount = @($statusCodes | Where-Object { $_ -ne 429 }).Count
        if ($limitedCount -lt 1 -or $forwardedCount -lt 1) {
            throw "Gateway rate-limit check failed: limited=$limitedCount forwarded=$forwardedCount"
        }
        Write-Host "GATEWAY Redis write limiter rejected $limitedCount of 30 concurrent student requests"
    } finally {
        foreach ($task in $tasks) {
            if ($task.IsCompletedSuccessfully) {
                $task.Result.Dispose()
            }
        }
        foreach ($request in $requests) {
            $request.Dispose()
        }
        $client.Dispose()
    }

    Write-Host 'Phase 8 frontend and Gateway verification passed.'
} finally {
    $cleanup = @"
DELETE t FROM sso_ticket t
JOIN legacy_identity i ON i.id = t.legacy_identity_id
WHERE i.legacy_system = '$legacySystem' AND i.legacy_user_id = '$legacyUserId';
DELETE FROM legacy_identity
WHERE legacy_system = '$legacySystem' AND legacy_user_id = '$legacyUserId';
"@
    try {
        Invoke-MySql -Database 'campus_auth' -Sql $cleanup | Out-Null
        Write-Host 'CLEANUP temporary Phase 8 authentication data removed'
    } catch {
        Write-Warning "Automatic Phase 8 cleanup failed: $($_.Exception.Message)"
    }
}
