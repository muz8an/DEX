package com.dex.browser;

import com.dex.WindowsController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browser Automation and Web Intelligence Engine for DEX.
 * Provides web navigation, accessible content extraction, structured search,
 * and seamless coordination with the default Windows desktop browser.
 */
public class BrowserAutomation {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern TITLE_PATTERN = Pattern.compile("(?i)<title[^>]*>(.*?)</title>");
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("(?i)<(script|style)[^>]*>.*?</\\1>", Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)<a\\s+(?:[^>]*?\\s+)?href=[\"'](https?://[^\"']+)[\"'][^>]*>(.*?)</a>");

    private static String lastVisitedUrl = null;
    private static String lastPageContent = null;

    private BrowserAutomation() {}

    /**
     * Navigates to a URL, extracts readable textual content, and records state.
     */
    public static String navigate(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return "Error: Please specify a valid URL.";
        }

        String url = urlString.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DEX/1.0 PersonalAssistant")
                    .header("Accept", "text/html,application/xhtml+xml,text/plain")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                return "Browser Navigation Failed: HTTP " + statusCode + " from " + url;
            }

            String html = response.body();
            String title = extractTitle(html);
            String readableText = extractReadableText(html);

            lastVisitedUrl = url;
            lastPageContent = readableText;

            StringBuilder sb = new StringBuilder();
            sb.append("=== Webpage Content ===\n");
            sb.append("• URL: ").append(url).append("\n");
            sb.append("• Title: ").append(title != null ? title : "(No Title)").append("\n");
            sb.append("• HTTP Status: ").append(statusCode).append(" OK\n\n");
            sb.append("--- Extracted Content (Summary) ---\n");

            if (readableText.length() > 2500) {
                sb.append(readableText.substring(0, 2500)).append("\n... [Content truncated. Total: ")
                        .append(readableText.length()).append(" characters]");
            } else {
                sb.append(readableText);
            }

            return sb.toString();

        } catch (Exception e) {
            return "Browser automation error navigating to " + url + ": " + e.getMessage();
        }
    }

    /**
     * Constructs search URL for the specified platform.
     */
    public static String buildSearchUrl(String query, String platform) {
        if (query == null) return "";
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String plat = platform != null ? platform.trim().toLowerCase() : "google";
        switch (plat) {
            case "youtube":
            case "yt":
                return "https://www.youtube.com/results?search_query=" + encoded;
            case "github":
            case "gh":
                return "https://github.com/search?q=" + encoded;
            case "duckduckgo":
            case "ddg":
                return "https://duckduckgo.com/?q=" + encoded;
            case "google":
            default:
                return "https://www.google.com/search?q=" + encoded;
        }
    }

    /**
     * Searches a web platform (Google, YouTube, GitHub, or DuckDuckGo) and launches browser or extracts results.
     */
    public static String search(String query, String platform) {
        if (query == null || query.isBlank()) {
            return "Error: Search query cannot be empty.";
        }

        String targetUrl = buildSearchUrl(query, platform);
        String plat = platform != null ? platform.trim().toLowerCase() : "google";

        // Open in desktop browser for interactive viewing
        WindowsController.openDefaultBrowser(targetUrl);

        // Also fetch webpage text for AI context
        String pageSummary = navigate(targetUrl);
        return "Search initiated on " + plat.toUpperCase() + " for: \"" + query + "\"\n"
                + "Opened in desktop browser: " + targetUrl + "\n\n"
                + pageSummary;
    }

    public static String cleanHtmlText(String html) {
        return extractReadableText(html);
    }

    /**
     * Reads accessible text from the last visited webpage.
     */
    public static String readLastPage() {
        if (lastVisitedUrl == null || lastPageContent == null) {
            return "No webpage has been navigated to in this session yet. Use browser_navigate first.";
        }
        return "Content from " + lastVisitedUrl + ":\n" + lastPageContent;
    }

    public static String getLastVisitedUrl() {
        return lastVisitedUrl;
    }

    private static String extractTitle(String html) {
        if (html == null) return null;
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            return cleanHtml(m.group(1)).trim();
        }
        return null;
    }

    private static String extractReadableText(String html) {
        if (html == null) return "";
        // 1. Remove scripts and styles
        String withoutScripts = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll(" ");
        // 2. Strip HTML tags
        String text = HTML_TAG_PATTERN.matcher(withoutScripts).replaceAll(" ");
        // 3. Clean up HTML entities & whitespace
        return cleanHtml(text);
    }

    private static String cleanHtml(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
