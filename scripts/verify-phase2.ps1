[CmdletBinding()]
param(
    [int]$GatewayHostPort = 18000,
    [int]$StudentHostPort = 18082,
    [int]$CourseHostPort = 18083
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-JsonResponse {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [hashtable]$Headers = @{}
    )

    $response = Invoke-WebRequest -Uri $Uri -Headers $Headers -SkipHttpErrorCheck
    return [PSCustomObject]@{
        StatusCode = [int]$response.StatusCode
        Headers = $response.Headers
        Body = $response.Content | ConvertFrom-Json
    }
}

$requestId = 'phase2-contract-check'
$courseList = Invoke-JsonResponse `
    -Uri "http://localhost:$GatewayHostPort/api/v1/courses?page=0&size=20" `
    -Headers @{ 'X-Request-Id' = $requestId }
if ($courseList.StatusCode -ne 200 `
        -or $courseList.Body.code -ne 0 `
        -or $courseList.Body.requestId -ne $requestId `
        -or $null -eq $courseList.Body.data.items) {
    throw 'Course list contract check failed.'
}
Write-Host 'COURSES gateway query and standard envelope succeeded'

$invalidRequest = Invoke-JsonResponse `
    -Uri "http://localhost:$GatewayHostPort/api/v1/courses?size=101"
if ($invalidRequest.StatusCode -ne 400 -or $invalidRequest.Body.code -ne 40000) {
    throw 'Course validation error contract check failed.'
}
Write-Host 'COURSES validation error envelope succeeded'

$missingCourse = Invoke-JsonResponse `
    -Uri "http://localhost:$GatewayHostPort/api/v1/courses/9223372036854775807"
if ($missingCourse.StatusCode -ne 404 -or $missingCourse.Body.code -ne 40400) {
    throw 'Course not-found contract check failed.'
}
Write-Host 'COURSES not-found envelope succeeded'

$missingStudent = Invoke-JsonResponse `
    -Uri "http://localhost:$StudentHostPort/internal/v1/students/9223372036854775807"
if ($missingStudent.StatusCode -ne 404 -or $missingStudent.Body.code -ne 40400) {
    throw 'Student internal contract check failed.'
}
Write-Host 'STUDENTS internal not-found envelope succeeded'

$invalidSyncResponse = Invoke-WebRequest `
    -Method Put `
    -Uri "http://localhost:$StudentHostPort/internal/v1/students/legacy/phase2-invalid" `
    -ContentType 'application/json' `
    -Body '{"studentNo":"","name":"Invalid","departmentCode":"D","departmentName":"Department","majorCode":"M","majorName":"Major","gradeYear":2026,"status":"ACTIVE"}' `
    -SkipHttpErrorCheck
$invalidSyncBody = $invalidSyncResponse.Content | ConvertFrom-Json
if ([int]$invalidSyncResponse.StatusCode -ne 400 -or $invalidSyncBody.code -ne 40000) {
    throw 'Student sync validation contract check failed.'
}
Write-Host 'STUDENTS sync validation envelope succeeded'

$semesters = Invoke-JsonResponse -Uri "http://localhost:$GatewayHostPort/api/v1/semesters"
if ($semesters.StatusCode -ne 200 -or $semesters.Body.code -ne 0) {
    throw 'Semester query contract check failed.'
}
Write-Host 'CATALOG semester query through Gateway succeeded'

$missingOffering = Invoke-JsonResponse `
    -Uri "http://localhost:$GatewayHostPort/api/v1/course-offerings/9223372036854775807"
if ($missingOffering.StatusCode -ne 404 -or $missingOffering.Body.code -ne 40400) {
    throw 'Course offering not-found contract check failed.'
}
Write-Host 'CATALOG offering not-found envelope succeeded'

$openApi = Invoke-RestMethod -Uri "http://localhost:$CourseHostPort/v3/api-docs"
$documentedPaths = @($openApi.paths.PSObject.Properties.Name)
foreach ($path in @(
        '/api/v1/courses',
        '/api/v1/courses/{courseId}',
        '/api/v1/courses/{courseId}/capacity',
        '/api/v1/courses/{courseId}/offerings',
        '/api/v1/course-offerings/{offeringId}',
        '/api/v1/semesters',
        '/api/v1/teachers/{teacherId}')) {
    if ($path -notin $documentedPaths) {
        throw "OpenAPI path missing: $path"
    }
}
Write-Host 'OPENAPI Phase 2 course paths found'

$studentOpenApi = Invoke-RestMethod -Uri "http://localhost:$StudentHostPort/v3/api-docs"
$studentPaths = @($studentOpenApi.paths.PSObject.Properties.Name)
if ('/internal/v1/students/legacy/{legacyStudentId}' -notin $studentPaths) {
    throw 'Student sync OpenAPI path missing.'
}
Write-Host 'OPENAPI Phase 2 student sync path found'

Write-Host 'Phase 2 verification passed.'
