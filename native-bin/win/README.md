# Windows native binaries (not tracked in git)

The Windows single-file build bundles `yt-dlp.exe`, `ffmpeg.exe`, and `ffprobe.exe` from this
folder as jar resources, so the resulting `.bat` needs nothing pre-installed besides a JRE.
These binaries (~220MB total) are gitignored — download them here before running
`windows/build-single-file.sh`:

- `yt-dlp.exe` — https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe
- `ffmpeg.exe` and `ffprobe.exe` — from the `bin/` folder inside
  https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip

Place all three directly in this folder (`native-bin/win/`).
