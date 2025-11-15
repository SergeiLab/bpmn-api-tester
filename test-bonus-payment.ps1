# 

Write-Host "╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Testing Bonus Payment Files             ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Test BPMN
Write-Host "[1/4] Testing BPMN upload..." -ForegroundColor Yellow

$bpmnPath = ".\01_bonus_payment.bpmn"

if (-not (Test-Path $bpmnPath)) {
    Write-Host "  ❌ File not found: $bpmnPath" -ForegroundColor Red
    exit 1
}

try {
    $form = @{
        bpmn = Get-Item -Path $bpmnPath
        name = "Bonus Payment Process (BPMN)"
    }
    
    $uploadBpmn = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/processes/upload" `
        -Method Post -Form $form
    
    Write-Host "  ✅ BPMN uploaded: ID=$($uploadBpmn.id), Steps=$($uploadBpmn.steps)" -ForegroundColor Green
    $bpmnProcessId = $uploadBpmn.id
} catch {
    Write-Host "  ❌ BPMN upload failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Get BPMN details
Write-Host "`n[2/4] Checking BPMN steps..." -ForegroundColor Yellow

try {
    $process = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/processes/$bpmnProcessId"
    
    Write-Host "  Process: $($process.name)" -ForegroundColor Cyan
    Write-Host "  Steps:" -ForegroundColor Cyan
    $process.steps | ForEach-Object {
        $icon = if($_.method){"✅"}else{"⚠️"}
        Write-Host "    $icon [$($_.order)] $($_.name)" -ForegroundColor White
        if ($_.method -and $_.endpoint) {
            Write-Host "       $($_.method) $($_.endpoint)" -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "  ❌ Failed to get process details" -ForegroundColor Red
}

# Test PlantUML
Write-Host "`n[3/4] Testing PlantUML upload..." -ForegroundColor Yellow

$pumlPath = ".\01_bonus_payment.puml"

if (-not (Test-Path $pumlPath)) {
    Write-Host "  ❌ File not found: $pumlPath" -ForegroundColor Red
    exit 1
}

try {
    $form = @{
        sequence = Get-Item -Path $pumlPath
        name = "Bonus Payment Process (PlantUML)"
    }
    
    $uploadPuml = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/processes/upload-sequence" `
        -Method Post -Form $form
    
    Write-Host "  ✅ PlantUML uploaded: ID=$($uploadPuml.id), Steps=$($uploadPuml.steps)" -ForegroundColor Green
    $pumlProcessId = $uploadPuml.id
} catch {
    Write-Host "  ❌ PlantUML upload failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Get PlantUML details
Write-Host "`n[4/4] Checking PlantUML steps..." -ForegroundColor Yellow

try {
    $process = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/processes/$pumlProcessId"
    
    Write-Host "  Process: $($process.name)" -ForegroundColor Cyan
    Write-Host "  Steps:" -ForegroundColor Cyan
    $process.steps | ForEach-Object {
        $icon = if($_.method){"✅"}else{"⚠️"}
        Write-Host "    $icon [$($_.order)] $($_.name)" -ForegroundColor White
        if ($_.method -and $_.endpoint) {
            Write-Host "       $($_.method) $($_.endpoint)" -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "  ❌ Failed to get process details" -ForegroundColor Red
}

Write-Host "`n╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Test Complete                            ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan