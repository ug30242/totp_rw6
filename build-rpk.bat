@echo off
setlocal
cd /d "%~dp0"
call npm ci
if errorlevel 1 exit /b %errorlevel%
call npm test
if errorlevel 1 exit /b %errorlevel%
call npm run web:build
if errorlevel 1 exit /b %errorlevel%
call npm run release
if errorlevel 1 exit /b %errorlevel%
call npm run verify:rpk -- dist/com.vela.rw6totp.release.1.0.1.rpk
if errorlevel 1 exit /b %errorlevel%
