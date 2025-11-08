# quick-test.ps1

Write-Host "╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║         Quick Final Test                  ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

try {
    Write-Host "[1/5] Checking backend health..." -ForegroundColor Yellow
    $health = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/health"
    Write-Host "  ✅ Backend: $($health.service) v$($health.version)" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Backend not running!" -ForegroundColor Red
    Write-Host "  Start it with: mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

try {
    Write-Host "`n[2/5] Uploading BPMN process..." -ForegroundColor Yellow
    $form = @{
        bpmn = Get-Item -Path ".\example-process.bpmn"
        name = "Final Test $(Get-Date -Format 'HH:mm:ss')"
    }

    $upload = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/processes/upload" `
        -Method Post -Form $form

    Write-Host "  ✅ Process uploaded: ID=$($upload.id), Steps=$($upload.steps)" -ForegroundColor Green
    $processId = $upload.id
} catch {
    Write-Host "  ❌ Upload failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

try {
    Write-Host "`n[3/5] Executing test..." -ForegroundColor Yellow
    $testRequest = @{
        mode = "STANDARD"
        generateTestData = $true
        initialContext = @{
            externalAccountID = "40817810000000000001"
        }
    } | ConvertTo-Json -Depth 10

    $execution = Invoke-RestMethod `
        -Uri "http://localhost:8080/api/v1/processes/$processId/execute" `
        -Method Post `
        -ContentType "application/json" `
        -Body $testRequest

    Write-Host "  ✅ Execution completed: ID=$($execution.executionId)" -ForegroundColor Green
    $executionId = $execution.executionId
} catch {
    Write-Host "  ❌ Execution failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "`n[4/5] Test Results:" -ForegroundColor Yellow
Write-Host "  Status: $($execution.status)" -ForegroundColor $(if($execution.status -eq "COMPLETED"){"Green"}else{"Yellow"})
Write-Host ""

$execution.stepResults | ForEach-Object {
    $icon = switch ($_.status) {
        "SUCCESS" { "✅" }
        "FAILED" { if($_.httpStatus -eq 401){"🔐"}elseif($_.httpStatus -eq 404){"📍"}else{"❌"} }
        default { "⚠️" }
    }
    
    Write-Host "  $icon $($_.stepName)"
    Write-Host "     Status: $($_.status)" -ForegroundColor $(if($_.status -eq "SUCCESS"){"Green"}else{"Yellow"})
    
    if ($_.httpStatus) {
        $statusColor = if($_.httpStatus -ge 200 -and $_.httpStatus -lt 300){"Green"}elseif($_.httpStatus -eq 404){"Yellow"}else{"Red"}
        Write-Host "     HTTP: $($_.httpStatus)" -ForegroundColor $statusColor
    }
    
    if ($_.executionTimeMs) {
        Write-Host "     Time: $($_.executionTimeMs)ms" -ForegroundColor Gray
    }
    
    if ($_.errorMessage) {
        Write-Host "     Error: $($_.errorMessage)" -ForegroundColor Yellow
    }
}

try {
    Write-Host "`n[5/5] Exporting reports..." -ForegroundColor Yellow
    
    Invoke-WebRequest -Uri "http://localhost:8080/api/v1/executions/$executionId/export/html" `
        -OutFile "quick-test-report.html"
    Write-Host "  ✅ HTML: quick-test-report.html" -ForegroundColor Green
    
    Invoke-WebRequest -Uri "http://localhost:8080/api/v1/executions/$executionId/export/json" `
        -OutFile "quick-test-report.json"
    Write-Host "  ✅ JSON: quick-test-report.json" -ForegroundColor Green
    
} catch {
    Write-Host "  ⚠️  Export failed: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`n╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║              Summary                      ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan

$successCount = ($execution.stepResults | Where-Object { $_.status -eq "SUCCESS" }).Count
$totalCount = $execution.stepResults.Count

Write-Host "`nSteps: $successCount/$totalCount successful" -ForegroundColor White

if ($execution.status -eq "COMPLETED") {
    Write-Host "`n🎉 ALL TESTS PASSED!" -ForegroundColor Green
} elseif ($successCount -gt 0) {
    Write-Host "`n⚠️  PARTIAL SUCCESS" -ForegroundColor Yellow
    Write-Host "Some steps failed - this is expected if Banking API endpoints are not accessible" -ForegroundColor Gray
} else {
    Write-Host "`n❌ ALL TESTS FAILED" -ForegroundColor Red
    Write-Host "Check logs for details" -ForegroundColor Yellow
}

Write-Host "`nView report: start quick-test-report.html" -ForegroundColor Cyan
Write-Host ""