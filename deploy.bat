@echo off

if "%1"=="" (
    echo Usage: deploy.bat ^<tag^>
    exit /b 1
)

set TAG=%1
set IMAGE_NAME=emqarani3/auth-server

echo Building Docker image %IMAGE_NAME%:%TAG%...
docker build -t %IMAGE_NAME%:%TAG% .

if %ERRORLEVEL% NEQ 0 (
    echo Docker build failed!
    exit /b %ERRORLEVEL%
)

echo Pushing Docker image %IMAGE_NAME%:%TAG%...
docker push %IMAGE_NAME%:%TAG%

if %ERRORLEVEL% NEQ 0 (
    echo Docker push failed!
    exit /b %ERRORLEVEL%
)

echo Deployment to Docker Hub complete!
