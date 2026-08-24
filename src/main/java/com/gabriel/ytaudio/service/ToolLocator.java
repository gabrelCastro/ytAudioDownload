package com.gabriel.ytaudio.service;

import java.io.File;
import java.util.List;

/** Finds the yt-dlp / ffmpeg executables, since a GUI app may not inherit the full shell PATH
 *  (e.g. pipx installs to ~/.local/bin, which login shells add via .bashrc/.profile). */
public class ToolLocator {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    public static String locate(String explicitPath, String executableName) {
        if (explicitPath != null && !explicitPath.isBlank() && new File(explicitPath).canExecute()) {
            return explicitPath;
        }
        String bundled = BundledTools.extractIfBundled(executableName);
        if (bundled != null) {
            return bundled;
        }
        String exeName = IS_WINDOWS ? executableName + ".exe" : executableName;
        List<String> candidates = IS_WINDOWS ? windowsCandidates(exeName) : unixCandidates(exeName);
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.canExecute()) {
                return candidate;
            }
        }
        // Fall back to bare name and let ProcessBuilder search PATH.
        return executableName;
    }

    private static List<String> unixCandidates(String executableName) {
        String home = System.getProperty("user.home");
        return List.of(
                home + "/.local/bin/" + executableName,
                "/usr/local/bin/" + executableName,
                "/usr/bin/" + executableName,
                "/opt/homebrew/bin/" + executableName,
                "/snap/bin/" + executableName
        );
    }

    // winget/choco installs normally add themselves to PATH already (bare-name fallback
    // covers those); these extra candidates cover common manual/portable placements.
    private static List<String> windowsCandidates(String executableName) {
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        List<String> candidates = new java.util.ArrayList<>();
        if (localAppData != null) {
            candidates.add(localAppData + "\\Microsoft\\WinGet\\Links\\" + executableName);
        }
        if (programFiles != null) {
            candidates.add(programFiles + "\\ffmpeg\\bin\\" + executableName);
        }
        candidates.add("C:\\ffmpeg\\bin\\" + executableName);
        candidates.add("C:\\yt-dlp\\" + executableName);
        return candidates;
    }

    // ffmpeg only recognizes the single-dash "-version"; yt-dlp only recognizes "--version".
    // Try both so this works regardless of which tool is being checked.
    private static final List<String> VERSION_FLAGS = List.of("-version", "--version");

    public static boolean isAvailable(String path) {
        for (String flag : VERSION_FLAGS) {
            try {
                Process p = new ProcessBuilder(path, flag).redirectErrorStream(true).start();
                boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    return true;
                }
            } catch (Exception ignored) {
                // try the next flag / report unavailable below
            }
        }
        return false;
    }
}
