#!/bin/bash
# Builds the Windows single-file launcher: a native .exe stub that is also a valid
# runnable jar. Concatenating the compiled launcher before the jar's zip bytes works
# because java.util.zip locates the zip's central directory by scanning backward from
# EOF (tolerating any prefix), while the Windows PE loader only reads the headers at the
# front of the file — neither format cares about the other's data. Being a real
# GUI-subsystem .exe (not a .bat), double-clicking it never flashes a console window.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v x86_64-w64-mingw32-gcc >/dev/null || {
    echo "mingw-w64 not found. Install it with: sudo apt-get install -y mingw-w64" >&2
    exit 1
}

x86_64-w64-mingw32-gcc -municode -mwindows -O2 -o windows/launcher.exe windows/launcher.c

mvn clean package -Djavafx.platform=win
cat windows/launcher.exe target/yt-audio-downloader-1.0.0.jar > YT-Audio-Downloader.exe

echo "Built YT-Audio-Downloader.exe ($(du -h YT-Audio-Downloader.exe | cut -f1))"
