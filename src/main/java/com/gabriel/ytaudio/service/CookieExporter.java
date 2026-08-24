package com.gabriel.ytaudio.service;

import java.io.IOException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Writes cookies from a java.net.CookieStore into the Netscape cookies.txt format yt-dlp
 *  expects (via --cookies), so an in-app WebView login can feed it a real session. */
public class CookieExporter {

    public static void exportNetscape(CookieStore store, Path outFile) throws IOException {
        long farFuture = Instant.now().plus(365, ChronoUnit.DAYS).getEpochSecond();
        StringBuilder sb = new StringBuilder("# Netscape HTTP Cookie File\n");
        for (HttpCookie cookie : store.getCookies()) {
            String domain = cookie.getDomain();
            if (domain == null) continue;
            boolean includeSubdomains = domain.startsWith(".");
            String path = cookie.getPath() == null ? "/" : cookie.getPath();
            long expiry = cookie.getMaxAge() > 0
                    ? Instant.now().getEpochSecond() + cookie.getMaxAge()
                    : farFuture;
            sb.append(domain).append('\t')
                    .append(includeSubdomains ? "TRUE" : "FALSE").append('\t')
                    .append(path).append('\t')
                    .append(cookie.getSecure() ? "TRUE" : "FALSE").append('\t')
                    .append(expiry).append('\t')
                    .append(cookie.getName()).append('\t')
                    .append(cookie.getValue()).append('\n');
        }
        Files.createDirectories(outFile.getParent());
        Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8);
    }

    public static Path defaultCookiesFile() {
        String localAppData = System.getenv("LOCALAPPDATA");
        String base = (localAppData != null && !localAppData.isBlank())
                ? localAppData
                : System.getProperty("user.home");
        return Path.of(base, "yt-audio-downloader", "cookies.txt");
    }
}
