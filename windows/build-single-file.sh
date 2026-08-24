#!/bin/bash
# Builds the Windows single-file launcher: a .bat that is also a valid runnable jar.
# Concatenating a batch-script header before a jar's zip bytes works because java.util.zip
# tolerates (and adjusts for) an arbitrary prefix when locating the zip's central directory —
# the same trick Spring Boot uses for "fully executable jars".
set -euo pipefail
cd "$(dirname "$0")/.."

mvn clean package -Djavafx.platform=win
cat windows/bat-header.bat target/yt-audio-downloader-1.0.0.jar > YT-Audio-Downloader.bat

echo "Built YT-Audio-Downloader.bat ($(du -h YT-Audio-Downloader.bat | cut -f1))"
