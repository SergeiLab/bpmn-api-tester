# download-openapi.ps1

Write-Host "Downloading OpenAPI specification..." -ForegroundColor Yellow

try {
    $openApiUrl = "https://vbank.open.bankingapi.ru/openapi.json"
    $outputPath = ".\banking-openapi.json"
    
    Invoke-WebRequest -Uri $openApiUrl -OutFile $outputPath
    
    Write-Host "✅ OpenAPI downloaded: $outputPath" -ForegroundColor Green
    
    $content = Get-Content $outputPath | ConvertFrom-Json
    Write-Host "API Title: $($content.info.title)" -ForegroundColor Cyan
    Write-Host "Version: $($content.info.version)" -ForegroundColor Cyan
    Write-Host "Endpoints: $($content.paths.PSObject.Properties.Count)" -ForegroundColor Cyan
    
} catch {
    Write-Host "❌ Failed to download: $($_.Exception.Message)" -ForegroundColor Red
}