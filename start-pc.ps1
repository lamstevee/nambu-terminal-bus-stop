$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js 20 이상을 설치하세요.' }
if (-not (Test-Path -LiteralPath 'node_modules')) { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'npm 설치 실패' } }
try {
    $taskStatus = Invoke-RestMethod -Uri 'http://127.0.0.1:4173/api/config' -TimeoutSec 2
    if ($taskStatus.platform -eq 'pc') { Start-Process 'http://127.0.0.1:4173'; Write-Output 'PC 테스트 서버가 이미 실행 중입니다.'; return }
} catch {}
Start-Process 'http://127.0.0.1:4173'
& node server.mjs
