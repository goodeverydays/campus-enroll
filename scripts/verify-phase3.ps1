[CmdletBinding()]
param(
    [int]$GatewayHostPort = 18000,
    [int]$AuthHostPort = 18081,
    [int]$StudentHostPort = 18082,
    [int]$EnrollmentHostPort = 18084,
    [switch]$VerifyRedisReservation,
    [switch]$VerifyAsyncAcceptance,
    [switch]$VerifyReliableCapacity
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-JsonResponse {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [ValidateSet('Get', 'Post', 'Put', 'Delete')]
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
        TimeoutSec = 5
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

function Invoke-Redis {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $result = @(& docker compose exec -T redis sh -c `
        'exec redis-cli -a "$REDIS_PASSWORD" --raw "$@"' sh @Arguments 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'Redis verification command failed.'
    }
    return $result
}

function Wait-EnrollmentRequest {
    param(
        [Parameter(Mandatory)]
        [string]$RequestId,
        [Parameter(Mandatory)]
        [hashtable]$Headers
    )

    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $state = Invoke-JsonResponse `
                -Uri "http://localhost:$GatewayHostPort/api/v1/enrollment-requests/$RequestId" `
                -Headers $Headers
            if ($state.StatusCode -eq 200 -and $state.Body.data.status -in @('SUCCESS', 'FAILED')) {
                return $state
            }
        } catch {
            if ($attempt -eq 59) {
                throw
            }
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Enrollment request $RequestId did not reach a final state."
}

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$baseId = 700000000 + (Get-Random -Minimum 1000000 -Maximum 90000000)
$semesterId = $baseId
$teacherId = $baseId + 1
$courseId = $baseId + 2
$conflictCourseId = $baseId + 3
$offeringId = $baseId + 4
$conflictOfferingId = $baseId + 5
$legacyStudentId = "phase3-student-$suffix"
$studentNo = "P3$suffix"
$departmentCode = "P3D$suffix"
$majorCode = "P3M$suffix"
$legacySystem = 'phase3-verifier'
$legacyUserId = "phase3-user-$suffix"
$studentId = $null
$accessToken = $null
$redisKey = "campus:enrollment:reservation:{$courseId}:offering:$offeringId"
$conflictRedisKey = "campus:enrollment:reservation:{$conflictCourseId}:offering:$conflictOfferingId"

try {
    $courseFixture = @"
INSERT INTO semester (
  id, code, name, starts_on, ends_on,
  enrollment_starts_at, enrollment_ends_at, status)
VALUES (
  $semesterId, 'P3S$suffix', 'Phase 3 Verification Semester',
  DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 120 DAY),
  DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY),
  DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), 'ENROLLMENT_OPEN');
INSERT INTO teacher (id, teacher_no, name)
VALUES ($teacherId, 'P3T$suffix', 'Phase 3 Verification Teacher');
INSERT INTO course (id, code, name, credits, total_hours, status)
VALUES
  ($courseId, 'P3C$suffix', 'Phase 3 Transaction Course', 2.0, 32, 'ACTIVE'),
  ($conflictCourseId, 'P3X$suffix', 'Phase 3 Conflict Course', 2.0, 32, 'ACTIVE');
INSERT INTO course_offering (
  id, course_id, semester_id, teacher_id, section_no, capacity, selected_count, status)
VALUES
  ($offeringId, $courseId, $semesterId, $teacherId, '01', 1, 0, 'OPEN'),
  ($conflictOfferingId, $conflictCourseId, $semesterId, $teacherId, '01', 2, 0, 'OPEN');
INSERT INTO course_schedule (
  offering_id, day_of_week, start_section, end_section, location, start_week, end_week)
VALUES
  ($offeringId, 2, 1, 2, 'P3-A101', 1, 16),
  ($conflictOfferingId, 2, 2, 3, 'P3-A102', 1, 16);
"@
    Invoke-MySql -Database 'campus_course' -Sql $courseFixture | Out-Null
    Write-Host 'FIXTURE temporary semester, courses, offerings, and schedules created'

    $sync = Invoke-JsonResponse `
        -Method Put `
        -Uri "http://localhost:$StudentHostPort/internal/v1/students/legacy/$legacyStudentId" `
        -Body @{
            studentNo = $studentNo
            name = 'Phase 3 Verification Student'
            departmentCode = $departmentCode
            departmentName = 'Phase 3 Verification Department'
            majorCode = $majorCode
            majorName = 'Phase 3 Verification Major'
            gradeYear = 2026
            status = 'ACTIVE'
        }
    if ($sync.StatusCode -ne 200 -or $sync.Body.code -ne 0) {
        throw 'Temporary student synchronization failed.'
    }
    $studentId = [long]$sync.Body.data.student.id
    Write-Host 'STUDENT temporary eligible profile created'

    $systemKey = (& docker compose exec -T auth-service printenv LEGACY_SYSTEM_API_KEY).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($systemKey)) {
        throw 'Could not read the local Auth Service verification key.'
    }
    $ticketResponse = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$AuthHostPort/internal/v1/auth/sso-tickets" `
        -Headers @{ 'X-Legacy-System-Key' = $systemKey } `
        -Body @{ legacySystem = $legacySystem; legacyUserId = $legacyUserId; studentId = $studentId }
    $exchange = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/auth/sso/exchange" `
        -Body @{ ticket = $ticketResponse.Body.data.ticket }
    if ($exchange.StatusCode -ne 200 -or $exchange.Body.code -ne 0) {
        throw 'Phase 3 JWT setup failed.'
    }
    $accessToken = [string]$exchange.Body.data.accessToken
    $authHeaders = @{ Authorization = "Bearer $accessToken" }
    Write-Host 'AUTH student JWT created for enrollment verification'

    $anonymous = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments" `
        -Headers @{ 'Idempotency-Key' = "anon-$suffix" } `
        -Body @{ courseId = $courseId }
    if ($anonymous.StatusCode -ne 401 -or $anonymous.Body.code -ne 40100) {
        throw 'Anonymous enrollment rejection failed.'
    }
    Write-Host 'GATEWAY anonymous enrollment rejected'

    $enrollHeaders = @{
        Authorization = "Bearer $accessToken"
        'Idempotency-Key' = "enroll-$suffix"
        'X-Student-Id' = '999999999'
    }
    $enrolled = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments" `
        -Headers $enrollHeaders `
        -Body @{ courseId = $courseId }
    if ($enrolled.StatusCode -notin @(200, 202) `
            -or $enrolled.Body.code -ne 0 `
            -or $enrolled.Body.data.status -notin @('PENDING', 'SUCCESS') `
            -or [long]$enrolled.Body.data.courseId -ne $courseId) {
        throw 'Enrollment acceptance failed.'
    }
    if ($VerifyAsyncAcceptance `
            -and ($enrolled.StatusCode -ne 202 -or $enrolled.Body.data.status -ne 'PENDING')) {
        throw 'Enrollment did not expose the Phase 5 HTTP 202/PENDING contract.'
    }
    $enrollmentRequestId = [string]$enrolled.Body.data.requestId
    Write-Host 'ENROLL request accepted with a stable request ID'

    if ($VerifyRedisReservation) {
        $redisRemaining = @(Invoke-Redis -Arguments @('HGET', $redisKey, 'remaining'))
        $redisMarker = @(Invoke-Redis -Arguments @('HGET', $redisKey, "student:$studentId"))
        if ($redisRemaining.Count -ne 1 `
                -or [int]$redisRemaining[0] -ne 0 `
                -or $redisMarker.Count -ne 1 `
                -or $redisMarker[0] -ne $enrollmentRequestId) {
            throw 'Redis reservation was not atomically recorded.'
        }
        Write-Host 'REDIS Lua reservation decremented remaining capacity and stored the student marker'
    }

    $enrollmentFinal = Wait-EnrollmentRequest -RequestId $enrollmentRequestId -Headers $authHeaders
    if ($enrollmentFinal.Body.data.status -ne 'SUCCESS') {
        throw "Enrollment worker failed the request: $($enrollmentFinal.Body.data.failureMessage)"
    }
    Write-Host 'WORKER enrollment request reached SUCCESS'

    $replayed = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments" `
        -Headers $enrollHeaders `
        -Body @{ courseId = $courseId }
    if ($replayed.StatusCode -ne 200 `
            -or $replayed.Body.data.requestId -ne $enrollmentRequestId) {
        throw 'Enrollment idempotency replay failed.'
    }
    Write-Host 'IDEMPOTENCY enrollment replay returned the original result'

    if ($VerifyRedisReservation) {
        $replayRemaining = @(Invoke-Redis -Arguments @('HGET', $redisKey, 'remaining'))
        if ($replayRemaining.Count -ne 1 -or [int]$replayRemaining[0] -ne 0) {
            throw 'Idempotency replay changed Redis capacity.'
        }
        Write-Host 'REDIS idempotency replay did not decrement capacity twice'
    }

    $requestState = $enrollmentFinal
    if ($requestState.StatusCode -ne 200 -or $requestState.Body.data.status -ne 'SUCCESS') {
        throw 'Enrollment request status query failed.'
    }
    $enrollmentList = Invoke-JsonResponse `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments" `
        -Headers $authHeaders
    if ($enrollmentList.StatusCode -ne 200 `
            -or @($enrollmentList.Body.data).Count -ne 1 `
            -or [long]$enrollmentList.Body.data[0].courseId -ne $courseId) {
        throw 'Student enrollment list query failed.'
    }
    Write-Host 'QUERY enrollment request and active course list succeeded'

    $duplicate = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments" `
        -Headers @{ Authorization = "Bearer $accessToken"; 'Idempotency-Key' = "duplicate-$suffix" } `
        -Body @{ courseId = $courseId }
    if ($duplicate.StatusCode -ne 409 -or $duplicate.Body.code -ne 40910) {
        throw "Duplicate enrollment detection failed: status=$($duplicate.StatusCode), code=$($duplicate.Body.code), message=$($duplicate.Body.message)"
    }
    Write-Host 'ENROLL duplicate course rejected before capacity mutation'

    $conflict = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments" `
        -Headers @{ Authorization = "Bearer $accessToken"; 'Idempotency-Key' = "conflict-$suffix" } `
        -Body @{ courseId = $conflictCourseId }
    if ($conflict.StatusCode -ne 409 -or $conflict.Body.code -ne 40913) {
        throw 'Schedule conflict detection failed.'
    }
    Write-Host 'ENROLL overlapping schedule rejected before capacity mutation'

    $differentOperation = Invoke-JsonResponse `
        -Method Delete `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments/$courseId" `
        -Headers $enrollHeaders
    if ($differentOperation.StatusCode -ne 409 -or $differentOperation.Body.code -ne 40915) {
        throw 'Idempotency payload conflict check failed.'
    }
    Write-Host 'IDEMPOTENCY key reuse for a different action rejected'

    $dropHeaders = @{
        Authorization = "Bearer $accessToken"
        'Idempotency-Key' = "drop-$suffix"
    }
    $dropped = Invoke-JsonResponse `
        -Method Delete `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments/$courseId" `
        -Headers $dropHeaders
    if ($dropped.StatusCode -ne 200 -or $dropped.Body.data.status -ne 'SUCCESS') {
        throw 'Course drop failed.'
    }
    $dropRequestId = [string]$dropped.Body.data.requestId
    $dropReplay = Invoke-JsonResponse `
        -Method Delete `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments/$courseId" `
        -Headers $dropHeaders
    if ($dropReplay.StatusCode -ne 200 -or $dropReplay.Body.data.requestId -ne $dropRequestId) {
        throw 'Drop idempotency replay failed.'
    }
    Write-Host 'DROP capacity released and idempotent replay succeeded'

    if ($VerifyRedisReservation) {
        $dropRemaining = @(Invoke-Redis -Arguments @('HGET', $redisKey, 'remaining'))
        $dropMarkerExists = @(Invoke-Redis -Arguments @('HEXISTS', $redisKey, "student:$studentId"))
        if ($dropRemaining.Count -ne 1 `
                -or [int]$dropRemaining[0] -ne 1 `
                -or $dropMarkerExists.Count -ne 1 `
                -or [int]$dropMarkerExists[0] -ne 0) {
            throw 'Redis reservation was not released by the drop operation.'
        }
        Write-Host 'REDIS Lua release restored capacity and removed the student marker'
    }

    $reenrolled = Invoke-JsonResponse `
        -Method Post `
        -Uri "http://localhost:$GatewayHostPort/api/v1/enrollments" `
        -Headers @{ Authorization = "Bearer $accessToken"; 'Idempotency-Key' = "reenroll-$suffix" } `
        -Body @{ courseId = $courseId }
    if ($reenrolled.StatusCode -notin @(200, 202) `
            -or $reenrolled.Body.data.status -notin @('PENDING', 'SUCCESS')) {
        throw 'Re-enrollment acceptance after drop failed.'
    }
    if ($VerifyAsyncAcceptance `
            -and ($reenrolled.StatusCode -ne 202 -or $reenrolled.Body.data.status -ne 'PENDING')) {
        throw 'Re-enrollment did not expose the Phase 5 HTTP 202/PENDING contract.'
    }
    $reenrollRequestId = [string]$reenrolled.Body.data.requestId
    $reenrollFinal = Wait-EnrollmentRequest -RequestId $reenrollRequestId -Headers $authHeaders
    if ($reenrollFinal.Body.data.status -ne 'SUCCESS') {
        throw "Re-enrollment worker failed the request: $($reenrollFinal.Body.data.failureMessage)"
    }
    Write-Host 'ENROLL dropped row reactivated without violating uniqueness'

    if ($VerifyRedisReservation) {
        $reenrollRemaining = @(Invoke-Redis -Arguments @('HGET', $redisKey, 'remaining'))
        $reenrollMarker = @(Invoke-Redis -Arguments @('HGET', $redisKey, "student:$studentId"))
        $conflictKeyExists = @(Invoke-Redis -Arguments @('EXISTS', $conflictRedisKey))
        if ($reenrollRemaining.Count -ne 1 `
                -or [int]$reenrollRemaining[0] -ne 0 `
                -or $reenrollMarker.Count -ne 1 `
                -or $reenrollMarker[0] -ne $reenrollRequestId `
                -or $conflictKeyExists.Count -ne 1 `
                -or [int]$conflictKeyExists[0] -ne 0) {
            throw 'Redis re-enrollment or conflict precheck invariant failed.'
        }
        Write-Host 'REDIS re-enrollment reserved once and schedule conflict created no Redis state'
    }

    $databaseState = @(Invoke-MySql `
        -Database 'campus_enrollment' `
        -Tabular `
        -Sql "SELECT (SELECT COUNT(*) FROM enrollment WHERE student_id=$studentId AND course_id=$courseId), (SELECT COUNT(*) FROM enrollment WHERE student_id=$studentId AND course_id=$courseId AND status='ENROLLED'), (SELECT COUNT(*) FROM enrollment_schedule es JOIN enrollment e ON e.id=es.enrollment_id WHERE e.student_id=$studentId AND e.course_id=$courseId), (SELECT COUNT(*) FROM enrollment_request WHERE student_id=$studentId AND status='FAILED');")
    $capacityState = @(Invoke-MySql `
        -Database 'campus_course' `
        -Tabular `
        -Sql "SELECT selected_count FROM course_offering WHERE id=$offeringId; SELECT selected_count FROM course_offering WHERE id=$conflictOfferingId;")
    if ($databaseState.Count -ne 1 `
            -or ($databaseState[0] -replace '\s+', ',') -ne '1,1,1,2' `
            -or $capacityState.Count -ne 2 `
            -or [int]$capacityState[0] -ne 1 `
            -or [int]$capacityState[1] -ne 0) {
        throw 'Phase 3 database invariants failed.'
    }
    Write-Host 'DATABASE uniqueness, schedule snapshot, failure history, and capacity counts are consistent'

    if ($VerifyReliableCapacity) {
        $sourceRequestCount = @(Invoke-MySql `
            -Database 'campus_enrollment' `
            -Tabular `
            -Sql "SELECT COUNT(*) FROM enrollment WHERE student_id=$studentId AND course_id=$courseId AND source_request_id='$reenrollRequestId';")
        $capacityReservations = @(Invoke-MySql `
            -Database 'campus_course' `
            -Tabular `
            -Sql "SELECT request_id, status FROM course_capacity_reservation WHERE request_id IN ('$enrollmentRequestId', '$reenrollRequestId') ORDER BY request_id;")
        $reservationStates = @{}
        foreach ($row in $capacityReservations) {
            $columns = @($row -split '\s+')
            if ($columns.Count -eq 2) {
                $reservationStates[$columns[0]] = $columns[1]
            }
        }
        if ($sourceRequestCount.Count -ne 1 `
                -or [int]$sourceRequestCount[0] -ne 1 `
                -or $reservationStates[$enrollmentRequestId] -ne 'RELEASED' `
                -or $reservationStates[$reenrollRequestId] -ne 'RESERVED') {
            throw 'Request-idempotent capacity reservation state is inconsistent.'
        }
        Write-Host 'DATABASE capacity reserve, drop release, and re-enrollment are request-idempotent'
    }

    $openApi = Invoke-RestMethod -Uri "http://localhost:$EnrollmentHostPort/v3/api-docs"
    $paths = @($openApi.paths.PSObject.Properties.Name)
    foreach ($path in @(
            '/api/v1/enrollments',
            '/api/v1/enrollments/{courseId}',
            '/api/v1/enrollment-requests/{requestId}')) {
        if ($path -notin $paths) {
            throw "Enrollment OpenAPI path missing: $path"
        }
    }
    Write-Host 'OPENAPI Phase 3 enrollment paths found'

    Write-Host 'Phase 3 MySQL enrollment verification passed.'
} finally {
    try {
        Invoke-Redis -Arguments @('DEL', $redisKey, $conflictRedisKey) | Out-Null
        if ($null -ne $studentId) {
            $enrollmentCleanup = @"
DELETE es FROM enrollment_schedule es
JOIN enrollment e ON e.id = es.enrollment_id
WHERE e.student_id = $studentId;
DELETE dl FROM enrollment_dead_letter dl
JOIN enrollment_request er ON er.request_id = dl.request_id
WHERE er.student_id = $studentId;
DELETE FROM enrollment_request WHERE student_id = $studentId;
DELETE FROM enrollment WHERE student_id = $studentId;
DELETE FROM student_enrollment_lock WHERE student_id = $studentId;
"@
            Invoke-MySql -Database 'campus_enrollment' -Sql $enrollmentCleanup | Out-Null
        }
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
        $courseCleanup = @"
DELETE FROM course_schedule WHERE offering_id IN ($offeringId, $conflictOfferingId);
DELETE FROM course_capacity_reservation WHERE offering_id IN ($offeringId, $conflictOfferingId);
DELETE FROM course_offering WHERE id IN ($offeringId, $conflictOfferingId);
DELETE FROM course WHERE id IN ($courseId, $conflictCourseId);
DELETE FROM teacher WHERE id = $teacherId;
DELETE FROM semester WHERE id = $semesterId;
"@
        Invoke-MySql -Database 'campus_auth' -Sql $authCleanup | Out-Null
        Invoke-MySql -Database 'campus_student' -Sql $studentCleanup | Out-Null
        Invoke-MySql -Database 'campus_course' -Sql $courseCleanup | Out-Null
        Write-Host 'CLEANUP temporary Phase 3 verification data removed'
    } catch {
        Write-Warning "Automatic verification cleanup failed: $($_.Exception.Message)"
    }
}
