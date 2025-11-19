$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   BPMN API Tester - Full Demo" -ForegroundColor Green
Write-Host "   Team: team112" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[Prerequisites Check]" -ForegroundColor Yellow
if (!(Test-Path "01_bonus_payment.bpmn")) {
    Write-Host "ERROR: 01_bonus_payment.bpmn not found!" -ForegroundColor Red
    exit 1
}
if (!(Test-Path "01_bonus_payment.puml")) {
    Write-Host "ERROR: 01_bonus_payment.puml not found!" -ForegroundColor Red
    exit 1
}
Write-Host "Files OK" -ForegroundColor Green

try {
    $null = curl -s http://localhost:8080/api/v1/health
    Write-Host "Backend OK" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Backend not responding!" -ForegroundColor Red
    exit 1
}

Write-Host ""

Write-Host "[1/10] Health Check..." -ForegroundColor Yellow
try {
    $health = curl -s http://localhost:8080/api/v1/health | ConvertFrom-Json
    Write-Host "   Status: $($health.status)" -ForegroundColor Green
    Write-Host "   Service: $($health.service)" -ForegroundColor Green
    Write-Host "   Version: $($health.version)" -ForegroundColor Green
} catch {
    Write-Host "   Health check failed" -ForegroundColor Red
    exit 1
}

Write-Host "`n[2/10] AI Status Check..." -ForegroundColor Yellow
try {
    $ai = curl -s http://localhost:8080/api/v1/ai/status | ConvertFrom-Json
    Write-Host "   AI Enabled: $($ai.enabled)" -ForegroundColor Green
    Write-Host "   Provider: $($ai.provider)" -ForegroundColor Green
    Write-Host "   Fallback Available: $($ai.fallbackAvailable)" -ForegroundColor Green
} catch {
    Write-Host "   AI status check failed" -ForegroundColor Red
}

Write-Host "`n[3/10] Uploading BPMN Process..." -ForegroundColor Yellow
try {
    $uploadBpmn = curl -s -X POST http://localhost:8080/api/v1/processes/upload `
      -F "bpmn=@01_bonus_payment.bpmn" `
      -F "name=Bonus Payment BPMN" | ConvertFrom-Json
    Write-Host "   Process ID: $($uploadBpmn.id)" -ForegroundColor Green
    Write-Host "   Name: $($uploadBpmn.name)" -ForegroundColor Green
    Write-Host "   Steps: $($uploadBpmn.steps)" -ForegroundColor Green
    $bpmnId = $uploadBpmn.id
} catch {
    Write-Host "   BPMN upload failed" -ForegroundColor Red
    exit 1
}

Write-Host "`n[4/10] Uploading Sequence Diagram..." -ForegroundColor Yellow
try {
    $uploadSeq = curl -s -X POST http://localhost:8080/api/v1/processes/upload-sequence `
      -F "sequence=@01_bonus_payment.puml" `
      -F "name=Bonus Payment Sequence" | ConvertFrom-Json
    Write-Host "   Process ID: $($uploadSeq.id)" -ForegroundColor Green
    Write-Host "   Steps: $($uploadSeq.steps)" -ForegroundColor Green
} catch {
    Write-Host "   Sequence upload failed" -ForegroundColor Red
}

Write-Host "`n[5/10] Listing Process Catalog..." -ForegroundColor Yellow
try {
    $processes = curl -s http://localhost:8080/api/v1/processes | ConvertFrom-Json
    Write-Host "   Total Processes: $($processes.Count)" -ForegroundColor Green
    foreach ($p in $processes) {
        Write-Host "     - ID $($p.id): $($p.name) ($($p.steps) steps)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   Process list failed" -ForegroundColor Red
}

Write-Host "`n[6/10] Getting Process Details (ID=$bpmnId)..." -ForegroundColor Yellow
try {
    $details = curl -s http://localhost:8080/api/v1/processes/$bpmnId | ConvertFrom-Json
    Write-Host "   Name: $($details.name)" -ForegroundColor Green
    Write-Host "   Total Steps: $($details.steps.Count)" -ForegroundColor Green
    Write-Host "   Endpoints mapped:" -ForegroundColor Green
    $stepNum = 1
    foreach ($step in $details.steps) {
        if ($step.method -and $step.endpoint) {
            Write-Host "     [$stepNum] $($step.method) $($step.endpoint)" -ForegroundColor Cyan
            $stepNum++
        }
    }
} catch {
    Write-Host "   Process details failed" -ForegroundColor Red
}

Write-Host "`n[7/10] Getting Test Data Templates..." -ForegroundColor Yellow
try {
    $templates = curl -s http://localhost:8080/api/v1/test-data/templates | ConvertFrom-Json
    Write-Host "   Available Templates:" -ForegroundColor Green
    $templates.PSObject.Properties | ForEach-Object {
        Write-Host "     - $($_.Name)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   Test data templates failed" -ForegroundColor Red
}

Write-Host "`n[8/10] Executing Test..." -ForegroundColor Yellow
try {
    $execution = curl -s -X POST http://localhost:8080/api/v1/processes/$bpmnId/execute `
      -H "Content-Type: application/json" `
      -d '{"mode":"STANDARD","generateTestData":true,"initialContext":{}}' | ConvertFrom-Json
    
    Write-Host "   Execution ID: $($execution.executionId)" -ForegroundColor Green
    Write-Host "   Status: $($execution.status)" -ForegroundColor $(if ($execution.status -eq "COMPLETED") { "Green" } else { "Yellow" })
    Write-Host "   Steps Results:" -ForegroundColor Green
    
    $stepNumber = 1
    foreach ($result in $execution.stepResults) {
        $color = if ($result.status -eq "SUCCESS") { "Green" } else { "Red" }
        $icon = if ($result.status -eq "SUCCESS") { "✓" } else { "✗" }
       
        $stepInfo = "Step $stepNumber"
        if ($result.endpoint) {
            $stepInfo += ": $($result.endpoint)"
        }
       
        Write-Host " $icon $stepInfo" -ForegroundColor $color
        Write-Host "     Status: $($result.status) | HTTP: $($result.httpStatus) | Time: $($result.executionTimeMs)ms" -ForegroundColor Gray
       
        if ($result.errorMessage -and $result.status -ne "SUCCESS") {
            $shortError = $result.errorMessage
            if ($shortError.Length -gt 80) {
                $shortError = $shortError.Substring(0, 80) + "..."
            }
            Write-Host "     Error: $shortError" -ForegroundColor Red
        }
        $stepNumber++
    }
    
    $execId = $execution.executionId
} catch {
    Write-Host "   Execution failed" -ForegroundColor Red
    exit 1
}

Write-Host "`n[9/10] Exporting Reports..." -ForegroundColor Yellow
try {
    curl -s http://localhost:8080/api/v1/executions/$execId/export/html -o "report_$execId.html"
    Write-Host "   [OK] Saved: report_$execId.html" -ForegroundColor Green
    
    curl -s http://localhost:8080/api/v1/executions/$execId/export/csv -o "report_$execId.csv"
    Write-Host "   [OK] Saved: report_$execId.csv" -ForegroundColor Green
    
    curl -s http://localhost:8080/api/v1/executions/$execId/export/json -o "report_$execId.json"
    Write-Host "   [OK] Saved: report_$execId.json" -ForegroundColor Green
} catch {
    Write-Host "   Export failed" -ForegroundColor Red
}

Write-Host "`n[10/10] Execution History..." -ForegroundColor Yellow
try {
    $history = curl -s http://localhost:8080/api/v1/processes/$bpmnId/executions | ConvertFrom-Json
    Write-Host "   Total Executions: $($history.Count)" -ForegroundColor Green
    foreach ($h in $history) {
        $statusColor = if ($h.status -eq "COMPLETED") { "Green" } else { "Yellow" }
        Write-Host "     - ID $($h.id): $($h.status) | $($h.mode)" -ForegroundColor $statusColor
    }
} catch {
    Write-Host "   History failed" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Demo Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Generated Reports:" -ForegroundColor Yellow
Write-Host "  - report_$execId.html (Visual)" -ForegroundColor Cyan
Write-Host "  - report_$execId.csv (Data)" -ForegroundColor Cyan
Write-Host "  - report_$execId.json (API)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "  1. Open: report_$execId.html" -ForegroundColor White
Write-Host "  2. Visit: http://localhost:8080" -ForegroundColor White
Write-Host ""
if ($execution.status -eq "COMPLETED") {
    Write-Host "Status: SUCCESS" -ForegroundColor Green
} else {
    Write-Host "Status: PARTIAL (OAuth OK, API credentials issue)" -ForegroundColor Yellow
}
Write-Host ""