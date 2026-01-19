@echo off

if "%~1"=="" (
    echo Usage: gpush.bat "commit message"
    exit /b 1
)

set MESSAGE=%~1

echo Adding files...
git add .

echo Committing...
git commit -m "%MESSAGE%"

echo Pushing to origin...
git push

if %ERRORLEVEL% NEQ 0 (
    echo Git push failed!
    exit /b %ERRORLEVEL%
)

echo Git push complete!
