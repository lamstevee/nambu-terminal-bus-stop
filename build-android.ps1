param([switch]$Install)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (Test-Path -LiteralPath 'tools\jdk') { $env:JAVA_HOME = (Get-ChildItem -LiteralPath 'tools\jdk' -Directory | Select-Object -First 1).FullName }
if (Test-Path -LiteralPath 'tools\android-sdk') { $env:ANDROID_HOME = (Resolve-Path 'tools\android-sdk').Path }
$gradleExe = Join-Path $PSScriptRoot 'tools\gradle\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradleExe)) { throw 'tools에 Gradle이 없습니다. Android Studio에서 android 폴더를 열어 빌드하세요.' }
& $gradleExe -p android --console=plain assembleDebug
if ($LASTEXITCODE -ne 0) { throw '안드로이드 빌드 실패' }
New-Item -ItemType Directory -Path 'dist' -Force | Out-Null
Copy-Item -LiteralPath 'android\app\build\outputs\apk\debug\app-debug.apk' -Destination 'dist\homebound.apk' -Force
if ($Install) { & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r 'dist\homebound.apk' }
Write-Output 'APK: dist\homebound.apk'
