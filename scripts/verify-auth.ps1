[CmdletBinding()]
param(
    [int]$GatewayHostPort = 18000,
    [int]$AuthHostPort = 18081,
    [int]$StudentHostPort = 18082
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-JsonResponse {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [ValidateSet('Get', 'Post', 'Put')]
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
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    $response = Invoke-WebRequest @parameters
    return [PSCustomObject]@{
        StatusCode = [int]$response.StatusCode
        Headers = $response.Headers
        Body = $response.Content | ConvertFrom-Json
    }
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory)]
        [string]$Database,
        [Parameter(Mandatory)]
        [string]$Sql,
        [switch]$Tabular
    )

    $mysqlArguments = if ($Tabular) {
        'exec mysql -ucampus_app -p"$MYSQL_PASSWORD" -N ' + $Database
    } else {
        'exec mysql -ucampus_app -p"$MYSQL_PASSWORD" ' + $Database
    }
    $result = @($Sql | & docker compose exec -T mysql sh -c $mysqlArguments 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed for database $Database."
    }
    return $result
}

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$legacyStudentId = "auth-check-$suffix"
$studentNo = "T$suffix"
$departmentCode = "D$suffix"
$majorCode = "M$suffix"
$legacySystem = 'phase25-verifier'
$legacyUserId = "user-$suffix"
$studentId = $null

try {
    $systemKey = (& docker compose exec -T auth-service printenv LEGACY_SYSTEM_API_KEY).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($systemKey)) {
        throw 'Could not read the local Auth Service verification key.'
    }

    $sync = Invoke-JsonResponse `
        -Method Put `
        -Uri "http://localhost:$StudentHostPort/internal/v1/students/legacy/$legacyStudentId" `
        -Body @{
            studentNo = $studentNo
            name = 'Phase 2.5 Verification Student'
            departmentCode = $departmentCode
            departmentName = 'Phase 2.5 Verification Department'
            majorCode = $majorCode
            majorName = 'Phase 2.5 Verification Major'
            gradeYear = 2026
            status = 'ACTIVE'
        }
    if ($sync.StatusCode -ne 200 -or $sync.Body.code -ne 0) {
        throw 'Temporary student synchronization failed.'
    }
    $studentId = [long]$sync.Body.data.student.id
    Write-Host 'STUDENT temporary verification profile created'

    $badKey = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$AuthHostPort/internal/v1/auth/sso-tickets" `
        -Headers @{ 'X-Legacy-System-Key' = 'invalid-verification-key' } `
        -Body @{ legacySystem = $legacySystem; legacyUserId = $legacyUserId; studentId = $studentId }
    if ($badKey.StatusCode -ne 401 -or $badKey.Body.code -ne 40102) {
        throw 'Internal system-key rejection check failed.'
    }
    Write-Host 'AUTH invalid system key rejected'

    $issued = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$AuthHostPort/internal/v1/auth/sso-tickets" `
        -Headers @{ 'X-Legacy-System-Key' = $systemKey } `
        -Body @{ legacySystem = $legacySystem; legacyUserId = $legacyUserId; studentId = $studentId }
    if ($issued.StatusCode -ne 200 -or $issued.Body.code -ne 0) {
        throw 'One-time SSO ticket issuance failed.'
    }
    $ticket = [string]$issued.Body.data.ticket
    if ($ticket.Length -lt 40) {
        throw 'One-time SSO ticket does not have the expected entropy.'
    }
    Write-Host 'AUTH one-time SSO ticket issued'

    $anonymous = Invoke-JsonResponse `
        -Uri "http://localhost:$GatewayHostPort/api/v1/students/me" `
        -Headers @{ 'X-Student-Id' = "$studentId" }
    if ($anonymous.StatusCode -ne 401 -or $anonymous.Body.code -ne 40100) {
        throw 'Gateway anonymous-access rejection check failed.'
    }
    Write-Host 'GATEWAY forged identity header without JWT rejected'

    $invalidJwt = Invoke-JsonResponse `
        -Uri "http://localhost:$GatewayHostPort/api/v1/students/me" `
        -Headers @{ Authorization = 'Bearer invalid.jwt.token' }
    if ($invalidJwt.StatusCode -ne 401 -or $invalidJwt.Body.code -ne 40100) {
        throw 'Gateway invalid-JWT response contract check failed.'
    }
    Write-Host 'GATEWAY invalid JWT returned the standard unauthorized envelope'

    $exchange = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/auth/sso/exchange" `
        -Body @{ ticket = $ticket }
    if ($exchange.StatusCode -ne 200 -or $exchange.Body.code -ne 0) {
        throw 'SSO ticket exchange through Gateway failed.'
    }
    $accessToken = [string]$exchange.Body.data.accessToken
    Write-Host 'AUTH ticket exchanged for JWT through Gateway'

    $profile = Invoke-JsonResponse `
        -Uri "http://localhost:$GatewayHostPort/api/v1/students/me" `
        -Headers @{
            Authorization = "Bearer $accessToken"
            'X-Student-Id' = '999999999'
        }
    if ($profile.StatusCode -ne 200 `
            -or $profile.Body.code -ne 0 `
            -or [long]$profile.Body.data.id -ne $studentId) {
        throw 'Gateway trusted-student identity injection check failed.'
    }
    Write-Host 'GATEWAY JWT identity replaced forged student header'

    $reused = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/auth/sso/exchange" `
        -Body @{ ticket = $ticket }
    if ($reused.StatusCode -ne 401 -or $reused.Body.code -ne 40101) {
        throw 'Single-use ticket replay rejection check failed.'
    }
    Write-Host 'AUTH consumed ticket replay rejected'

    $ticketState = @(Invoke-MySql `
        -Database 'campus_auth' `
        -Tabular `
        -Sql "SELECT CHAR_LENGTH(t.ticket_hash), t.consumed_at IS NOT NULL FROM sso_ticket t JOIN legacy_identity i ON i.id=t.legacy_identity_id WHERE i.legacy_system='$legacySystem' AND i.legacy_user_id='$legacyUserId';")
    if ($ticketState.Count -ne 1 -or ($ticketState[0] -replace '\s+', ',') -ne '64,1') {
        throw 'Hashed and consumed ticket persistence check failed.'
    }
    Write-Host 'DATABASE only the 64-character ticket hash is persisted and marked consumed'

    $authOpenApi = Invoke-RestMethod -Uri "http://localhost:$AuthHostPort/v3/api-docs"
    $authPaths = @($authOpenApi.paths.PSObject.Properties.Name)
    foreach ($path in @('/internal/v1/auth/sso-tickets', '/api/v1/auth/sso/exchange')) {
        if ($path -notin $authPaths) {
            throw "Auth OpenAPI path missing: $path"
        }
    }
    $studentOpenApi = Invoke-RestMethod -Uri "http://localhost:$StudentHostPort/v3/api-docs"
    if ('/api/v1/students/me' -notin @($studentOpenApi.paths.PSObject.Properties.Name)) {
        throw 'Student profile OpenAPI path missing.'
    }
    Write-Host 'OPENAPI Phase 2.5 authentication and student profile paths found'

    Write-Host 'Phase 2.5 authentication verification passed.'
} finally {
    $authCleanup = @"
DELETE t FROM sso_ticket t
JOIN legacy_identity i ON i.id = t.legacy_identity_id
WHERE i.legacy_system = '$legacySystem' AND i.legacy_user_id = '$legacyUserId';
DELETE FROM legacy_identity
WHERE legacy_system = '$legacySystem' AND legacy_user_id = '$legacyUserId';
"@
    $studentCleanup = @"
DELETE FROM student WHERE legacy_student_id = '$legacyStudentId';
DELETE FROM major WHERE code = '$majorCode';
DELETE FROM department WHERE code = '$departmentCode';
"@
    try {
        Invoke-MySql -Database 'campus_auth' -Sql $authCleanup | Out-Null
        Invoke-MySql -Database 'campus_student' -Sql $studentCleanup | Out-Null
        Write-Host 'CLEANUP temporary Phase 2.5 verification data removed'
    } catch {
        Write-Warning "Automatic verification cleanup failed: $($_.Exception.Message)"
    }
}
