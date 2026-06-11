#!/bin/bash
cd "$(dirname "$0")"

# CirclePack needs Java 17 or newer (for --add-opens and the recompiled
# classes in out/). The old Java 8 browser-plugin install won't work, so
# locate a modern JDK explicitly instead of trusting whatever 'java' is.
JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null)"
if [ -z "$JAVA_HOME" ]; then
  echo "Error: no Java 17+ JDK found." >&2
  echo "Install one (e.g. Temurin from https://adoptium.net) into" >&2
  echo "~/Library/Java/JavaVirtualMachines and run this script again." >&2
  exit 1
fi

exec "$JAVA_HOME/bin/java" \
  --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED \
  -cp "out:cpcore.jar:jars/*" \
  allMains.SplashMain "$@"
