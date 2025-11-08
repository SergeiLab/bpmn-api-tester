# check-credentials.ps1

Write-Host "╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Checking Banking API Credentials        ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# 1. Check environment variables
Write-Host "[1/3] Checking Environment Variables..." -ForegroundColor Yellow

$clientId = $env:BANKING_API_CLIENT_ID
$clientSecret = $env:BANKING_API_CLIENT_SECRET

if (-not $clientId) {
    Write-Host "  ❌ BANKING_API_CLIENT_ID not set" -ForegroundColor Red
    Write-Host "  Set it with:" -ForegroundColor Yellow
    Write-Host '  $env:BANKING_API_CLIENT_ID = "team112"' -ForegroundColor Gray
} else {
    Write-Host "  ✅ BANKING_API_CLIENT_ID = $clientId" -ForegroundColor Green
}

if (-not $clientSecret) {
    Write-Host "  ❌ BANKING_API_CLIENT_SECRET not set" -ForegroundColor Red
    Write-Host "  Set it with:" -ForegroundColor Yellow
    Write-Host '  $env:BANKING_API_CLIENT_SECRET = "TzNr0aYAz5vWT5Dib9l7FNR59NgPrmyR"' -ForegroundColor Gray
} else {
    Write-Host "  ✅ BANKING_API_CLIENT_SECRET = $($clientSecret.Substring(0, 10))..." -ForegroundColor Green
}

# 2. Check .env file
Write-Host "`n[2/3] Checking .env file..." -ForegroundColor Yellow

if (Test-Path ".env") {
    $envContent = Get-Content ".env" -Raw
    if ($envContent -match "BANKING_API_CLIENT_ID=(.+)") {
        $envClientId = $matches[1].Trim()
        if ($envClientId -ne "your_client_id_here" -and $envClientId -ne "") {
            Write-Host "  ✅ .env contains CLIENT_ID: $envClientId" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  .env CLIENT_ID not configured" -ForegroundColor Yellow
        }
    }
    
    if ($envContent -match "BANKING_API_CLIENT_SECRET=(.+)") {
        $envClientSecret = $matches[1].Trim()
        if ($envClientSecret -ne "your_client_secret_here" -and $envClientSecret -ne "") {
            Write-Host "  ✅ .env contains CLIENT_SECRET: $($envClientSecret.Substring(0, 10))..." -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  .env CLIENT_SECRET not configured" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "  ⚠️  .env file not found" -ForegroundColor Yellow
}

# 3. Test OAuth2 token
Write-Host "`n[3/3] Testing OAuth2 Token..." -ForegroundColor Yellow

try {
    $tokenBody = "grant_type=client_credentials&client_id=team112&client_secret=TzNr0aYAz5vWT5Dib9l7FNR59NgPrmyR"
    
    $tokenResponse = Invoke-RestMethod `
        -Uri "https://auth.bankingapi.ru/auth/realms/kubernetes/protocol/openid-connect/token" `
        -Method Post `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $tokenBody `
        -ErrorAction Stop
    
    Write-Host "  ✅ Token obtained successfully!" -ForegroundColor Green
    Write-Host "  Token: $($tokenResponse.access_token.Substring(0, 50))..." -ForegroundColor Gray
    Write-Host "  Expires in: $($tokenResponse.expires_in) seconds" -ForegroundColor Gray
    
} catch {
    Write-Host "  ❌ Failed to obtain token!" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "  Status Code: $statusCode" -ForegroundColor Red
    }
}

Write-Host "`n╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Recommended Action                       ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

if (-not $clientId -or -not $clientSecret) {
    Write-Host "Set environment variables in current PowerShell session:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host '$env:BANKING_API_CLIENT_ID = "team112"' -ForegroundColor Cyan
    Write-Host '$env:BANKING_API_CLIENT_SECRET = "TzNr0aYAz5vWT5Dib9l7FNR59NgPrmyR"' -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Then restart the application:" -ForegroundColor Yellow
    Write-Host "mvn spring-boot:run" -ForegroundColor Cyan
}