package com.gabriel.ytaudio.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/** Persists user preferences between runs using the JVM Preferences API (no extra files needed). */
public class AppSettings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(AppSettings.class);

    private static final String KEY_OUTPUT_DIR = "outputDir";
    private static final String KEY_FORMAT = "format";
    private static final String KEY_BITRATE = "bitrate";
    private static final String KEY_YTDLP_PATH = "ytDlpPath";
    private static final String KEY_FFMPEG_PATH = "ffmpegPath";
    private static final String KEY_CONCURRENCY = "concurrency";
    private static final String KEY_COOKIES_BROWSER = "cookiesBrowser";
    private static final String KEY_COOKIES_FILE = "cookiesFile";

    public String getOutputDir() {
        String def = System.getProperty("user.home") + File.separator + "Musica" + File.separator + "YT-Audio";
        String value = PREFS.get(KEY_OUTPUT_DIR, def);
        try {
            Files.createDirectories(Path.of(value));
        } catch (Exception ignored) { }
        return value;
    }

    public void setOutputDir(String dir) { PREFS.put(KEY_OUTPUT_DIR, dir); }

    public String getFormat() { return PREFS.get(KEY_FORMAT, "mp3"); }
    public void setFormat(String format) { PREFS.put(KEY_FORMAT, format); }

    public String getBitrate() { return PREFS.get(KEY_BITRATE, "192K"); }
    public void setBitrate(String bitrate) { PREFS.put(KEY_BITRATE, bitrate); }

    public int getConcurrency() { return PREFS.getInt(KEY_CONCURRENCY, 2); }
    public void setConcurrency(int value) { PREFS.putInt(KEY_CONCURRENCY, value); }

    public String getYtDlpPath() { return PREFS.get(KEY_YTDLP_PATH, ""); }
    public void setYtDlpPath(String path) { PREFS.put(KEY_YTDLP_PATH, path); }

    public String getFfmpegPath() { return PREFS.get(KEY_FFMPEG_PATH, ""); }
    public void setFfmpegPath(String path) { PREFS.put(KEY_FFMPEG_PATH, path); }

    /** Browser to pull YouTube auth cookies from ("" = disabled), e.g. "chrome", "firefox".
     *  Only works if that browser is installed/logged-in in the same OS yt-dlp runs in. */
    public String getCookiesBrowser() { return PREFS.get(KEY_COOKIES_BROWSER, ""); }
    public void setCookiesBrowser(String browser) { PREFS.put(KEY_COOKIES_BROWSER, browser); }

    /** Path to a cookies.txt file exported from a real logged-in browser ("" = disabled).
     *  Takes precedence over cookiesBrowser; useful under WSL where the browser lives on Windows. */
    public String getCookiesFile() { return PREFS.get(KEY_COOKIES_FILE, ""); }
    public void setCookiesFile(String path) { PREFS.put(KEY_COOKIES_FILE, path); }
}
