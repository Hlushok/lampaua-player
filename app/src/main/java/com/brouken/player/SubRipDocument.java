package com.brouken.player;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A bounded SubRip document that exposes cue text without structural metadata. */
final class SubRipDocument {
    static final int MAX_INPUT_BYTES = 2 * 1024 * 1024;
    static final int MAX_CUES = 10_000;
    static final int MAX_BATCH_CUES = 40;
    static final int MAX_BATCH_BYTES = 3_500;

    private static final Pattern TIMING = Pattern.compile(
            "\\d{2}:\\d{2}:\\d{2},\\d{3}[ \\t]+-->[ \\t]+"
                    + "\\d{2}:\\d{2}:\\d{2},\\d{3}");
    private static final Pattern FORMATTING = Pattern.compile(
            "<[^>\\r\\n]+>|\\{\\\\[^}\\r\\n]+\\}");
    private static final Pattern BOUNDARY = Pattern.compile(
            "\\[\\[\\[UA_PLAYER_\\d{6}\\]\\]\\]");
    private static final Pattern FORMAT_PLACEHOLDER = Pattern.compile(
            "\\[\\[\\[UA_FMT_\\d{6}_\\d{3}\\]\\]\\]");

    private final String source;
    private final String newline;
    private final List<CueBlock> cues;

    private SubRipDocument(String source, String newline, List<CueBlock> cues) {
        this.source = source;
        this.newline = newline;
        this.cues = cues;
    }

    static SubRipDocument parse(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("Unsupported SubRip size");
        }
        for (byte value : data) {
            if (value == 0) throw new IllegalArgumentException("Binary SubRip input");
        }

        String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("SubRip is not UTF-8", error);
        }

        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        List<Line> lines = lines(source);
        List<CueBlock> cues = new ArrayList<>();
        int index = 0;
        boolean first = true;
        while (index < lines.size()) {
            while (index < lines.size() && lines.get(index).blank(source)) index++;
            if (index >= lines.size()) break;

            Line identifierLine = lines.get(index++);
            String identifier = identifierLine.text(source);
            if (first && identifier.startsWith("\uFEFF")) identifier = identifier.substring(1);
            first = false;
            if (!identifier.matches("[0-9]+") || index >= lines.size()) {
                throw new IllegalArgumentException("Malformed SubRip identifier");
            }

            Line timingLine = lines.get(index++);
            if (!TIMING.matcher(timingLine.text(source)).matches()) {
                throw new IllegalArgumentException("Malformed SubRip timing");
            }
            if (index >= lines.size() || lines.get(index).blank(source)) {
                throw new IllegalArgumentException("SubRip cue has no text");
            }

            int textStart = lines.get(index).start;
            Line lastText = null;
            while (index < lines.size() && !lines.get(index).blank(source)) {
                lastText = lines.get(index++);
            }
            int textEnd = lastText == null ? textStart : lastText.end;
            String originalText = normalizeNewlines(source.substring(textStart, textEnd));
            if (originalText.contains("[[[UA_PLAYER_") || originalText.contains("[[[UA_FMT_")) {
                throw new IllegalArgumentException("Reserved subtitle marker");
            }
            cues.add(new CueBlock(cues.size(), textStart, textEnd, originalText));
            if (cues.size() > MAX_CUES) {
                throw new IllegalArgumentException("Too many SubRip cues");
            }
        }
        if (cues.isEmpty()) throw new IllegalArgumentException("No SubRip cues");
        return new SubRipDocument(source, newline, cues);
    }

    int cueCount() {
        return cues.size();
    }

    Batch batch(int fromCue, int requestedCues) {
        if (fromCue < 0 || fromCue >= cues.size() || requestedCues < 1) {
            throw new IllegalArgumentException("Invalid SubRip batch");
        }
        int limit = Math.min(cues.size(), fromCue + Math.min(requestedCues, MAX_BATCH_CUES));
        StringBuilder payload = new StringBuilder();
        List<String> markers = new ArrayList<>();
        int toCue = fromCue;
        for (int index = fromCue; index < limit; index++) {
            String marker = index == fromCue ? null : boundary(index);
            int previousLength = payload.length();
            if (marker != null) payload.append('\n').append(marker).append('\n');
            payload.append(cues.get(index).payload);
            if (payload.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BATCH_BYTES) {
                payload.setLength(previousLength);
                if (index == fromCue) {
                    throw new IllegalArgumentException("SubRip cue exceeds batch limit");
                }
                break;
            }
            if (marker != null) markers.add(marker);
            toCue = index + 1;
        }
        return new Batch(this, fromCue, toCue, payload.toString(), markers);
    }

    boolean acceptTranslation(Batch batch, String translated) {
        if (batch == null || batch.owner != this || translated == null
                || batch.fromCue < 0 || batch.toCue > cues.size()
                || batch.fromCue >= batch.toCue || translated.indexOf('\0') >= 0
                || translated.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
            return false;
        }
        String normalized = normalizeNewlines(translated);
        List<String> seenBoundaries = matches(BOUNDARY, normalized);
        if (!seenBoundaries.equals(batch.markerIds)) return false;

        List<String> parts = splitBatch(normalized, batch.markerIds);
        if (parts.size() != batch.toCue - batch.fromCue) return false;
        List<String> restored = new ArrayList<>(parts.size());
        for (int offset = 0; offset < parts.size(); offset++) {
            CueBlock cue = cues.get(batch.fromCue + offset);
            String part = parts.get(offset);
            List<String> placeholders = matches(FORMAT_PLACEHOLDER, part);
            if (!placeholders.equals(cue.placeholders)) return false;
            if (part.contains("[[[UA_FMT_") && placeholders.isEmpty()) return false;

            String value = part;
            for (int token = 0; token < cue.placeholders.size(); token++) {
                value = value.replace(cue.placeholders.get(token), cue.formatting.get(token));
            }
            if (FORMATTING.matcher(value).replaceAll("").trim().isEmpty()) return false;
            restored.add(value);
        }

        for (int offset = 0; offset < restored.size(); offset++) {
            cues.get(batch.fromCue + offset).translation = restored.get(offset);
        }
        return true;
    }

    byte[] renderUtf8() {
        StringBuilder rendered = new StringBuilder(source.length());
        int cursor = 0;
        for (CueBlock cue : cues) {
            rendered.append(source, cursor, cue.textStart);
            if (cue.translation == null) {
                rendered.append(source, cue.textStart, cue.textEnd);
            } else {
                rendered.append(cue.translation.replace("\n", newline));
            }
            cursor = cue.textEnd;
        }
        rendered.append(source, cursor, source.length());
        return rendered.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> splitBatch(String translated, List<String> markers) {
        List<String> parts = new ArrayList<>(markers.size() + 1);
        int cursor = 0;
        for (String marker : markers) {
            int at = translated.indexOf(marker, cursor);
            if (at < 0) return Collections.emptyList();
            parts.add(trimBoundaryNewline(translated.substring(cursor, at), false, true));
            cursor = at + marker.length();
        }
        parts.add(trimBoundaryNewline(translated.substring(cursor), !markers.isEmpty(), false));
        for (int index = 1; index < parts.size() - 1; index++) {
            parts.set(index, trimBoundaryNewline(parts.get(index), true, true));
        }
        return parts;
    }

    private static String trimBoundaryNewline(String value, boolean leading, boolean trailing) {
        if (leading && value.startsWith("\n")) value = value.substring(1);
        if (trailing && value.endsWith("\n")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static List<String> matches(Pattern pattern, String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static String boundary(int cueIndex) {
        return String.format(java.util.Locale.US, "[[[UA_PLAYER_%06d]]]", cueIndex);
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<Line> lines(String source) {
        List<Line> result = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int end = start;
            while (end < source.length()
                    && source.charAt(end) != '\r' && source.charAt(end) != '\n') {
                end++;
            }
            int next = end;
            if (next < source.length() && source.charAt(next) == '\r') next++;
            if (next < source.length() && source.charAt(next) == '\n') next++;
            result.add(new Line(start, end));
            start = next;
        }
        return result;
    }

    static final class Batch {
        final int fromCue;
        final int toCue;
        final String payload;
        final List<String> markerIds;
        private final SubRipDocument owner;

        private Batch(SubRipDocument owner, int fromCue, int toCue, String payload,
                      List<String> markerIds) {
            this.owner = owner;
            this.fromCue = fromCue;
            this.toCue = toCue;
            this.payload = payload;
            this.markerIds = Collections.unmodifiableList(new ArrayList<>(markerIds));
        }
    }

    private static final class CueBlock {
        final int textStart;
        final int textEnd;
        final String payload;
        final List<String> placeholders;
        final List<String> formatting;
        String translation;

        CueBlock(int cueIndex, int textStart, int textEnd, String originalText) {
            this.textStart = textStart;
            this.textEnd = textEnd;
            List<String> placeholders = new ArrayList<>();
            List<String> formatting = new ArrayList<>();
            Matcher matcher = FORMATTING.matcher(originalText);
            StringBuffer payload = new StringBuffer();
            int token = 0;
            while (matcher.find()) {
                String placeholder = String.format(java.util.Locale.US,
                        "[[[UA_FMT_%06d_%03d]]]", cueIndex, token++);
                placeholders.add(placeholder);
                formatting.add(matcher.group());
                matcher.appendReplacement(payload, Matcher.quoteReplacement(placeholder));
            }
            matcher.appendTail(payload);
            if (payload.toString().trim().isEmpty()) {
                throw new IllegalArgumentException("SubRip cue has no visible text");
            }
            this.payload = payload.toString();
            this.placeholders = Collections.unmodifiableList(placeholders);
            this.formatting = Collections.unmodifiableList(formatting);
        }
    }

    private static final class Line {
        final int start;
        final int end;

        Line(int start, int end) {
            this.start = start;
            this.end = end;
        }

        String text(String source) {
            return source.substring(start, end);
        }

        boolean blank(String source) {
            return text(source).trim().isEmpty();
        }
    }
}
