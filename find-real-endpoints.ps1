# find-real-endpoints.ps1

Write-Host "Поиск реальных Banking API endpoints..." -ForegroundColor Cyan
Write-Host ""

$token = ""

try {
    $tokenBody = "grant_type=client_credentials&client_id=team112&client_secret=TzNr0aYAz5vWT5Dib9l7FNR59NgPrmyR"
    
    $tokenResponse = Invoke-RestMethod `
        -Uri "https://auth.bankingapi.ru/auth/realms/kubernetes/protocol/openid-connect/token" `
        -Method Post `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $tokenBody
    
    $token = $tokenResponse.access_token
    Write-Host "✅ Token получен" -ForegroundColor Green
} catch {
    Write-Host "❌ Не удалось получить токен" -ForegroundColor Red
    exit 1
}

$baseUrl = "https://api.bankingapi.ru"
$headers = @{
    Authorization = "Bearer $token"
    "Content-Type" = "application/json"
}

# Пробуем разные варианты endpoints
$endpoints = @(
    "/accounts",
    "/api/accounts",
    "/api/v1/accounts",
    "/api/v2/accounts",
    "/banking/accounts",
    "/rb/accounts"
)

Write-Host "`nПроверка endpoints:" -ForegroundColor Yellow

foreach ($endpoint in $endpoints) {
    try {
        $response = Invoke-WebRequest `
            -Uri "$baseUrl$endpoint" `
            -Method Get `
            -Headers $headers `
            -ErrorAction Stop
        
        Write-Host "✅ $endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 403) {
            Write-Host "⚠️  $endpoint - 403 Forbidden (endpoint exists)" -ForegroundColor Yellow
        } elseif ($statusCode -eq 401) {
            Write-Host "⚠️  $endpoint - 401 Unauthorized (endpoint exists)" -ForegroundColor Yellow
        } else {
            Write-Host "❌ $endpoint - $statusCode" -ForegroundColor Red
        }
    }
}

Write-Host "`nПроверка OpenAPI спецификации:" -ForegroundColor Yellow

try {
    $spec = Invoke-RestMethod -Uri "https://vbank.open.bankingapi.ru/openapi.json"
    
    Write-Host "Доступные endpoints из спецификации:" -ForegroundColor Cyan
    $spec.paths.PSObject.Properties | ForEach-Object {
        Write-Host "  $($_.Name)" -ForegroundColor Gray
    }
} catch {
    Write-Host "❌ Не удалось загрузить OpenAPI спецификацию" -ForegroundColor Red
}