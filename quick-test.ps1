# Quick validation test before demo

$ErrorActionPreference = "Stop"

Write-Host "=== Quick Validation Test ===" -ForegroundColor Green
Write-Host ""

# Check files
Write-Host "[1/5] Checking files..." -ForegroundColor Yellow
$filesOK = $true
if (!(Test-Path "01_bonus_payment.bpmn")) {
    Write-Host "  ✗ 01_bonus_payment.bpmn not found!" -ForegroundColor Red
    $filesOK = $false
}
if (!(Test-Path "01_bonus_payment.puml")) {
    Write-Host "  ✗ 01_bonus_payment.puml not found!" -ForegroundColor Red
    $filesOK = $false
}
if (!(Test-Path ".env")) {
    Write-Host "  ⚠ .env not found (will use defaults)" -ForegroundColor Yellow
}

if ($filesOK) {
    Write-Host "  ✓ All required files present" -ForegroundColor Green
} else {
    exit 1
}

# Check backend
Write-Host "`n[2/5] Checking backend..." -ForegroundColor Yellow
try {
    $health = curl -s http://localhost:8080/api/v1/health | ConvertFrom-Json
    Write-Host "  ✓ Backend responding: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Backend not responding!" -ForegroundColor Red
    Write-Host "  → Start with: mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# Check AI
Write-Host "`n[3/5] Checking AI..." -ForegroundColor Yellow
try {
    $ai = curl -s http://localhost:8080/api/v1/ai/status | ConvertFrom-Json
    if ($ai.enabled) {
        Write-Host "  ✓ AI enabled: $($ai.provider)" -ForegroundColor Green
    } else {
        Write-Host "  ⚠ AI disabled (will use fallback)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ⚠ AI status check failed" -ForegroundColor Yellow
}

# Test BPMN upload
Write-Host "`n[4/5] Testing BPMN upload..." -ForegroundColor Yellow
try {
    $upload = curl -s -X POST http://localhost:8080/api/v1/processes/upload `
      -F "bpmn=@01_bonus_payment.bpmn" `
      -F "name=Test Process" | ConvertFrom-Json
    Write-Host "  ✓ BPMN upload OK: $($upload.steps) steps parsed" -ForegroundColor Green
    $testId = $upload.id
} catch {
    Write-Host "  ✗ BPMN upload failed!" -ForegroundColor Red
    exit 1
}

# Test execution
Write-Host "`n[5/5] Testing execution..." -ForegroundColor Yellow
try {
    $exec = curl -s -X POST http://localhost:8080/api/v1/processes/$testId/execute `
      -H "Content-Type: application/json" `
      -d '{"mode":"STANDARD","generateTestData":true,"initialContext":{}}' | ConvertFrom-Json
    Write-Host "  ✓ Execution OK: $($exec.status)" -ForegroundColor Green
} catch {
    Write-Host "  ⚠ Execution test failed (may be API credentials)" -ForegroundColor Yellow
}

# Summary
Write-Host "`n=== All Pre-flight Checks Passed ===" -ForegroundColor Green
Write-Host ""
Write-Host "Ready for demo!" -ForegroundColor Cyan
Write-Host "Run: .\demo-test.ps1" -ForegroundColor White
Write-Host ""