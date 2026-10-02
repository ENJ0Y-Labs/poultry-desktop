@echo off
if "%JAVA_HOME%" == "" (
  echo Error: JAVA_HOME is not set. >&2
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Error: JAVA_HOME does not point to a valid JDK. >&2
  exit /b 1
)
set "MAVEN_PROJECTBASEDIR=%~dp0"
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"
set "WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
if exist "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties" (
  for /F "usebackq tokens=1,2 delims==" %%A in ("%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties") do (
    if "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
  )
)
if not exist "%WRAPPER_JAR%" (
  echo Downloading Maven Wrapper...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; New-Item -ItemType Directory -Force -Path '%MAVEN_PROJECTBASEDIR%.mvn\wrapper' | Out-Null; Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%' }"
  if errorlevel 1 (
    echo Error: Could not download Maven Wrapper. >&2
    exit /b 1
  )
)
"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
exit /b %ERRORLEVEL%
