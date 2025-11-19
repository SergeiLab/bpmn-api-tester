# Создайте debug.ps1
Write-Host "=== Debug Info ===" -ForegroundColor Yellow

Write-Host "`n1. Upload fresh BPMN:"
$up = curl -s -X POST http://localhost:8080/api/v1/processes/upload `
  -F "bpmn=@01_bonus_payment.bpmn" `
  -F "name=Debug Test" | ConvertFrom-Json
Write-Host "ID: $($up.id), Steps: $($up.steps)"

Write-Host "`n2. Process details:"
$det = curl -s http://localhost:8080/api/v1/processes/$($up.id) | ConvertFrom-Json
Write-Host "Steps array length: $($det.steps.Count)"
$det.steps | ForEach-Object {
    Write-Host "  - $($_.name) | $($_.method) $($_.endpoint)"
}

Write-Host "`n3. Last 20 log lines:"
Get-Content logs/bpmn-api-tester.log -Tail 20