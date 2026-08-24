package com.gabriel.ytaudio.service;

import com.gabriel.ytaudio.model.VideoItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpService {

    private static final Pattern PROGRESS_PATTERN =
            Pattern.compile("\\[download]\\s+(\\d+(?:\\.\\d+)?)%");

    private final String ytDlpPath;
    private final String ffmpegPath;
    private final String cookiesBrowser;
    private final String cookiesFile;

    public YtDlpService(String ytDlpPath, String ffmpegPath, String cookiesBrowser, String cookiesFile) {
        this.ytDlpPath = ytDlpPath;
        this.ffmpegPath = ffmpegPath;
        this.cookiesBrowser = cookiesBrowser;
        this.cookiesFile = cookiesFile;
    }

    /** Resolves a single video URL or a playlist URL into a list of downloadable items. */
    public List<VideoItem> fetchEntries(String url) throws IOException, InterruptedException {
        try {
            return fetchEntries(url, false);
        } catch (IOException firstError) {
            if (!hasCookiesConfigured()) {
                throw firstError;
            }
            return fetchEntries(url, true);
        }
    }

    private List<VideoItem> fetchEntries(String url, boolean useCookies) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                ytDlpPath, "-J", "--flat-playlist", "--no-warnings", "--ignore-errors", url
        ));
        if (useCookies) {
            addCookiesOption(command);
        }
        addExtractorArgs(command, useCookies);
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();

        String stdout = readAll(process.getInputStream());
        String errorOutput = readAll(process.getErrorStream());
        boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Tempo esgotado ao consultar o yt-dlp.");
        }
        if (stdout.isBlank()) {
            throw new IOException("yt-dlp não retornou dados. " + errorOutput);
        }

        // yt-dlp prints the JSON as a single line, but may print warning/notice
        // lines before it even with --no-warnings (e.g. bot-check or age notices).
        // Find the actual JSON line instead of assuming stdout is pure JSON.
        String json = stdout.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("{"))
                .findFirst()
                .orElse(null);
        if (json == null) {
            String detail = errorOutput.isBlank() ? stdout.trim() : errorOutput.trim();
            throw new IOException("yt-dlp não conseguiu obter esse link: " + detail);
        }

        JSONObject root = new JSONObject(json);
        List<VideoItem> items = new ArrayList<>();

        if (root.has("entries") && !root.isNull("entries")) {
            JSONArray entries = root.getJSONArray("entries");
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.isNull("id") && entry.isNull("url")) continue;
                items.add(toVideoItem(entry));
            }
        } else {
            items.add(toVideoItem(root));
        }
        return items;
    }

    private VideoItem toVideoItem(JSONObject entry) {
        String id = entry.optString("id", "");
        String title = entry.optString("title", "(sem título)");
        String webpageUrl = entry.optString("webpage_url",
                entry.optString("url", "https://www.youtube.com/watch?v=" + id));
        String duration = formatDuration(entry.optDouble("duration", -1));
        String thumbnailUrl = extractThumbnail(entry, id);
        return new VideoItem(id, title, webpageUrl, duration, thumbnailUrl);
    }

    // yt-dlp's own "thumbnail"/"thumbnails" fields are often signed, session-scoped URLs
    // (sqp=/rs= query tokens) that don't reliably load outside that session. The plain
    // per-video-id pattern below is unsigned and always resolves.
    private String extractThumbnail(JSONObject entry, String id) {
        if (!id.isBlank()) {
            return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
        }
        String thumbnail = entry.optString("thumbnail", "");
        if (!thumbnail.isBlank()) return thumbnail;
        JSONArray thumbnails = entry.optJSONArray("thumbnails");
        if (thumbnails != null && thumbnails.length() > 0) {
            return thumbnails.getJSONObject(0).optString("url", "");
        }
        return "";
    }

    private String formatDuration(double seconds) {
        if (seconds < 0) return "--";
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** Downloads one video's audio, reporting 0.0-1.0 progress and raw log lines. */
    public void downloadAudio(VideoItem item, String outputDir, String format, String bitrate,
                               Consumer<Double> onProgress, Consumer<String> onLog)
            throws IOException, InterruptedException {
        try {
            downloadAudio(item, outputDir, format, bitrate, false, onProgress, onLog);
        } catch (IOException firstError) {
            if (!hasCookiesConfigured()) {
                throw firstError;
            }
            onLog.accept("Tentativa sem cookies falhou, tentando novamente com cookies configurados...");
            onProgress.accept(0.0);
            downloadAudio(item, outputDir, format, bitrate, true, onProgress, onLog);
        }
    }

    private void downloadAudio(VideoItem item, String outputDir, String format, String bitrate, boolean useCookies,
                                Consumer<Double> onProgress, Consumer<String> onLog)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();
        command.add(ytDlpPath);
        command.add("-x");
        command.add("--audio-format");
        command.add(format);
        command.add("--audio-quality");
        command.add(bitrate);
        command.add("--newline");
        command.add("--no-warnings");
        command.add("--no-playlist");
        if (ffmpegPath != null && !ffmpegPath.isBlank()) {
            command.add("--ffmpeg-location");
            command.add(ffmpegPath);
        }
        if (useCookies) {
            addCookiesOption(command);
        }
        addExtractorArgs(command, useCookies);
        command.add("-o");
        command.add(outputDir + java.io.File.separator + "%(title)s.%(ext)s");
        command.add(item.getUrl());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onLog.accept(line);
                Matcher matcher = PROGRESS_PATTERN.matcher(line);
                if (matcher.find()) {
                    double percent = Double.parseDouble(matcher.group(1));
                    onProgress.accept(percent / 100.0);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("yt-dlp terminou com código " + exitCode);
        }
        onProgress.accept(1.0);
    }

    private boolean hasCookiesConfigured() {
        return (cookiesFile != null && !cookiesFile.isBlank())
                || (cookiesBrowser != null && !cookiesBrowser.isBlank());
    }

    private void addCookiesOption(List<String> command) {
        if (cookiesFile != null && !cookiesFile.isBlank()) {
            command.add("--cookies");
            command.add(cookiesFile);
        } else if (cookiesBrowser != null && !cookiesBrowser.isBlank()) {
            command.add("--cookies-from-browser");
            command.add(cookiesBrowser);
        }
    }

    private void addExtractorArgs(List<String> command, boolean useCookies) {
        // yt-dlp's "android" client reliably returns a real muxed audio+video format without
        // needing cookies or a JS signature-challenge solver — but yt-dlp refuses to use it at
        // all once cookies are supplied ("does not support cookies"), forcing back onto the
        // currently-broken web/web_embedded (no audio, SABR-locked) or tv_downgraded (standing
        // bug, https://github.com/yt-dlp/yt-dlp/issues/17389) clients. So: try android without
        // cookies first (handles ordinary bot-check videos); only the cookies retry (for videos
        // that genuinely need an authenticated session) drops android and just excludes
        // tv_downgraded from whatever default client set applies.
        command.add("--extractor-args");
        command.add(useCookies ? "youtube:player_client=-tv_downgraded" : "youtube:player_client=android");
    }

    private String readAll(java.io.InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
