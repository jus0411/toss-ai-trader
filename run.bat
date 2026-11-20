@echo off
echo =================================================
echo  TOSS AUTO TRADER Backend Bootstrapper
echo =================================================

:: Fix window environment PATH error (.C: drive typo bypass)
set PATH=C:\Windows\system32;C:\Windows;C:\Windows\System32\Wbem;C:\Windows\System32\WindowsPowerShell\v1.0\;C:\Program Files\Git\cmd;D:\nvm4w\nodejs;D:\nvm4w\nvm;C:\Program Files\Java\jdk-21\bin;D:\apache-maven-3.9.15-bin\apache-maven-3.9.15\bin;%USERPROFILE%\AppData\Local\Programs\Python\Python313\Scripts\;%USERPROFILE%\AppData\Local\Programs\Python\Python313\

cd backend

echo [1/2] Compiling and Packaging Backend... (skip tests)
call mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build Failed! Check your code.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/2] Launching Spring Boot server...
java -jar target/backend-0.0.1-SNAPSHOT.jar
