# test-ollama.ps1

Write-Host "╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Testing Ollama AI Integration            ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# 1. Check if Ollama is running
Write-Host "[1/4] Checking Ollama service..." -ForegroundColor Yellow

try {
    $ollamaHealth = Invoke-RestMethod -Uri "http://localhost:11434/api/tags"
    Write-Host "  ✅ Ollama is running" -ForegroundColor Green
    Write-Host "  Models available:" -ForegroundColor Gray
    $ollamaHealth.models | ForEach-Object {
        Write-Host "    - $($_.name)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "  ❌ Ollama is not running!" -ForegroundColor Red
    Write-Host "  Start Ollama first, then run:" -ForegroundColor Yellow
    Write-Host "  ollama pull llama3.2:3b" -ForegroundColor Gray
    exit 1
}

# 2. Test Ollama generation
Write-Host "`n[2/4] Testing Ollama generation..." -ForegroundColor Yellow

$testPrompt = @{
    model = "llama3.2:3b"
    prompt = "Generate JSON with fields: accountId (20 digits), amount (number), currency (string RUB). Return only JSON:"
    stream = $false
    format = "json"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:11434/api/generate" `
        -Method Post `
        -ContentType "application/json" `
        -Body $testPrompt
    
    Write-Host "  ✅ Ollama generated response" -ForegroundColor Green
    Write-Host "  Response:" -ForegroundColor Gray
    Write-Host $response.response -ForegroundColor Cyan
} catch {
    Write-Host "  ❌ Generation failed: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. Upload test process
Write-Host "`n[3/4] Uploading test process..." -ForegroundColor Yellow

$form = @{
    bpmn = Get-Item -Path ".\example-process.bpmn"
    name = "Ollama AI Test $(Get-Date -Format 'HH:mm:ss')"
}

try {
    $upload = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/processes/upload" `
        -Method Post -Form $form
    
    Write-Host "  ✅ Process uploaded: ID=$($upload.id)" -ForegroundColor Green
    $processId = $upload.id
} catch {
    Write-Host "  ❌ Upload failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 4. Execute with AI generation
Write-Host "`n[4/4] Executing test with AI generation..." -ForegroundColor Yellow

$testRequest = @{
    mode = "STANDARD"
    generateTestData = $true
    initialContext = @{
        externalAccountID = "40817810000000000001"
    }
} | ConvertTo-Json

try {
    $execution = Invoke-RestMethod `
        -Uri "http://localhost:8080/api/v1/processes/$processId/execute" `
        -Method Post `
        -ContentType "application/json" `
        -Body $testRequest
    
    Write-Host "  ✅ Execution completed" -ForegroundColor Green
    Write-Host "  Status: $($execution.status)" -ForegroundColor Cyan
    
    $execution.stepResults | ForEach-Object {
        $icon = if($_.status -eq "SUCCESS"){"✅"}else{"⚠️"}
        Write-Host "  $icon $($_.stepName) - $($_.status)" -ForegroundColor White
        
        if ($_.requestPayload) {
            Write-Host "    Generated data:" -ForegroundColor Gray
            try {
                $data = $_.requestPayload | ConvertFrom-Json
                $data.PSObject.Properties | ForEach-Object {
                    Write-Host "      $($_.Name): $($_.Value)" -ForegroundColor DarkGray
                }
            } catch {}
        }
    }
} catch {
    Write-Host "  ❌ Execution failed: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n╔═══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Ollama AI Test Complete                  ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════╝" -ForegroundColor Cyan