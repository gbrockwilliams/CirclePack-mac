#!/bin/bash
cd "$(dirname "$0")"
exec java \
  --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED \
  -cp "out:cpcore.jar:jars/*" \
  allMains.SplashMain "$@"
