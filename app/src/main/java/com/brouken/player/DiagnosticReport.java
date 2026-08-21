package com.brouken.player;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiagnosticReport {
    private static final Pattern URI_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?|rtsp|rtmp|udp|content|file)://[^\\s<>\\\"']+");
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?im)(\\b(?:authorization|proxy-authorization)\\s*[:=]\\s*)"
                    + "(?:bearer\\s+)?[^\\s\\r\\n]+");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?im)(\\b(?:api[-_ ]?key|access[-_ ]?token|token|password|passwd|secret)"
                    + "\\s*[:=]\\s*)[^\\s&\\r\\n]+");
    private static final String NETWORK_SCHEMES = "|http|https|rtsp|rtmp|udp|";

    private DiagnosticReport() {
    }

    static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;

        Matcher matcher = URI_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer(text.length());
        while (matcher.find()) {
            String candidate = matcher.group();
            int end = candidate.length();
            while (end > 0 && ".,;)]}".indexOf(candidate.charAt(end - 1)) >= 0) end--;
            String suffix = candidate.substring(end);
            String replacement = sanitizeNetworkUri(candidate.substring(0, end)) + suffix;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        String sanitized = AUTHORIZATION_PATTERN.matcher(result).replaceAll("$1[redacted]");
        return SECRET_PATTERN.matcher(sanitized).replaceAll("$1[redacted]");
    }

    static String sanitizeNetworkUri(String value) {
        if (value == null || value.trim().isEmpty()) return "(none)";
        String trimmed = value.trim();
        int separator = trimmed.indexOf("://");
        if (separator <= 0) return sanitizeText(trimmed);

        String scheme = trimmed.substring(0, separator).toLowerCase(Locale.ROOT);
        if ("content".equals(scheme) || "file".equals(scheme)) {
            return scheme + " (local)";
        }
        if (!NETWORK_SCHEMES.contains("|" + scheme + "|")) {
            return scheme + " (external)";
        }

        try {
            URI parsed = new URI(trimmed);
            if (parsed.getHost() != null) {
                String origin = new URI(scheme, null, parsed.getHost(), parsed.getPort(),
                        null, null, null).toASCIIString();
                return origin + redactedPath(parsed.getRawPath());
            }
        } catch (URISyntaxException ignored) {
            // The fallback below still removes authority credentials and URL parameters.
        }

        String remainder = trimmed.substring(separator + 3);
        int cut = firstPositive(remainder.indexOf('?'), remainder.indexOf('#'));
        if (cut >= 0) remainder = remainder.substring(0, cut);
        int path = remainder.indexOf('/');
        String authority = path < 0 ? remainder : remainder.substring(0, path);
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        boolean hasPath = path >= 0 && path + 1 < remainder.length();
        return scheme + "://" + authority + (hasPath ? "/[redacted]" : "");
    }

    static String rootMessage(Throwable error) {
        if (error == null) return "";
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        String deepest = "";
        while (current != null && visited.add(current)) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) deepest = message.trim();
            current = current.getCause();
        }
        return sanitizeText(deepest);
    }

    static String stackTrace(Throwable error) {
        if (error == null) return "";
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return sanitizeText(writer.toString());
    }

    private static int firstPositive(int first, int second) {
        if (first < 0) return second;
        if (second < 0) return first;
        return Math.min(first, second);
    }

    private static String redactedPath(String path) {
        return path == null || path.isEmpty() || "/".equals(path) ? "" : "/[redacted]";
    }
}
