#!/bin/bash
cd "$(dirname "$0")"

# CirclePack requires Java 17 or newer.
# Use JAVA_HOME if set, otherwise find 'java' on PATH and verify the version.
if [ -n "$JAVA_HOME" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(which java 2>/dev/null)"
fi

if [ -z "$JAVA" ] || [ ! -x "$JAVA" ]; then
  echo "Error: java not found. Install Java 17+ (e.g. Temurin from https://adoptium.net)" >&2
  echo "and either add it to PATH or set JAVA_HOME." >&2
  exit 1
fi

# Check version
JAVA_VER=$("$JAVA" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
  echo "Error: Java $JAVA_VER found but Java 17+ is required." >&2
  echo "Set JAVA_HOME to point to a Java 17+ installation." >&2
  exit 1
fi

exec "$JAVA" \
  -cp "out:cpcore.jar:jars/*" \
  allMains.SplashMain "$@"
