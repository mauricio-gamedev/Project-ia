@echo off
setlocal
set "GRADLE_VERSION=8.13"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_ROOT=%GRADLE_USER_HOME%\wrapper\dists\nucleo-gradle-%GRADLE_VERSION%"
set "GRADLE_HOME=%DIST_ROOT%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
  set "ZIP_FILE=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $expected='20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'; $zip=Join-Path $env:DIST_ROOT ('gradle-' + $env:GRADLE_VERSION + '-bin.zip'); if ((Test-Path $zip) -and ((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant() -ne $expected)) { Remove-Item -LiteralPath $zip -Force }; if (!(Test-Path $zip)) { Invoke-WebRequest -UseBasicParsing -Uri ('https://services.gradle.org/distributions/gradle-' + $env:GRADLE_VERSION + '-bin.zip') -OutFile $zip }; $actual=(Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant(); if ($actual -ne $expected) { Remove-Item -LiteralPath $zip -Force; throw 'Gradle SHA-256 verification failed' }; Expand-Archive -LiteralPath $zip -DestinationPath $env:DIST_ROOT -Force"
  if errorlevel 1 exit /b %errorlevel%
)
call "%GRADLE_BIN%" %*
exit /b %errorlevel%
