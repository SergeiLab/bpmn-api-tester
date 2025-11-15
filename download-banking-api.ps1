# download-banking-api.ps1

$apiUrl = "https://vbank.open.bankingapi.ru/openapi.json"
$outputPath = ".\banking-api-spec.json"

Invoke-WebRequest -Uri $apiUrl -OutFile $outputPath

Write-Host "✅ Downloaded: $outputPath" -ForegroundColor Green

$spec = Get-Content $outputPath | ConvertFrom-Json
Write-Host "API: $($spec.info.title)" -ForegroundColor Cyan
Write-Host "Endpoints:" -ForegroundColor Yellow

$spec.paths.PSObject.Properties | ForEach-Object {
    Write-Host "  $($_.Name)" -ForegroundColor Gray
}