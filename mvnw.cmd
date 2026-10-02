@echo off
setlocal

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

if not exist "%WRAPPER_JAR%" (
  echo Error: Maven Wrapper JAR not found: %WRAPPER_JAR% >&2
  exit /b 1
)

"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  -classpath "%WRAPPER_JAR%" ^
  "%WRAPPER_LAUNCHER%" %*

exit /b %ERRORLEVEL%