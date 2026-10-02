#!/bin/sh
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
if [ -f "$BASE_DIR/.mvn/wrapper/maven-wrapper.properties" ]; then
  while IFS='=' read -r key value; do
    [ "$key" = "wrapperUrl" ] && WRAPPER_URL="$value"
  done < "$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
fi
if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p "$BASE_DIR/.mvn/wrapper"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR" || exit 1
  elif command -v wget >/dev/null 2>&1; then wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR" || exit 1
  else echo "Error: curl or wget is required to download Maven Wrapper." >&2; exit 1; fi
fi
if [ -n "$JAVA_HOME" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD="java"; fi
exec "$JAVACMD" $MAVEN_OPTS -classpath "$WRAPPER_JAR" "-Dmaven.multiModuleProjectDirectory=$BASE_DIR" org.apache.maven.wrapper.MavenWrapperMain "$@"
