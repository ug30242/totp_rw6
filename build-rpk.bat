@echo off
setlocal
cd /d "%~dp0"
call npm ci
if errorlevel 1 exit /b %errorlevel%
call npm test
if errorlevel 1 exit /b %errorlevel%
call npm run release
if errorlevel 1 exit /b %errorlevel%
for /f "delims=" %%v in ('node -p "require('./package.json').version"') do set "RPK_VERSION=%%v"
call npm run verify:rpk -- dist/com.vela.rw6totp.release.%RPK_VERSION%.rpk
if errorlevel 1 exit /b %errorlevel%
call npm run smoke:rpk -- dist/com.vela.rw6totp.release.%RPK_VERSION%.rpk
if errorlevel 1 exit /b %errorlevel%
