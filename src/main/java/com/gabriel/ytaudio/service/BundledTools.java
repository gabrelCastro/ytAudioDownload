package com.gabriel.ytaudio.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Extracts yt-dlp/ffmpeg executables bundled as jar resources (Windows build only),
 *  so that build needs nothing pre-installed besides a JRE. No-op if not bundled
 *  (e.g. the Linux/WSL build, which relies on system-installed tools instead). */
public class BundledTools {

    private static final String APP_DIR_NAME = "yt-audio-downloader";

    /** Extracts the named tool (without ".exe") to a per-user cache dir and returns its
     *  path, or null if this build has no bundled copy of it. */
    public static String extractIfBundled(String executableName) {
        String resourcePath = "/native-bin/win/" + executableName + ".exe";
        try (InputStream in = BundledTools.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            Path dir = Path.of(cacheRoot(), APP_DIR_NAME, "bin");
            Files.createDirectories(dir);
            Path target = dir.resolve(executableName + ".exe");
            if (!Files.exists(target)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (executableName.equals("ffmpeg")) {
                // yt-dlp looks for ffprobe next to ffmpeg; extract it alongside, best-effort.
                extractSibling(dir, "ffprobe");
            }
            return target.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static void extractSibling(Path dir, String executableName) {
        Path target = dir.resolve(executableName + ".exe");
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = BundledTools.class.getResourceAsStream("/native-bin/win/" + executableName + ".exe")) {
            if (in != null) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private static String cacheRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        return (localAppData != null && !localAppData.isBlank())
                ? localAppData
                : System.getProperty("user.home");
    }
}
