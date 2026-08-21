package com.brouken.player;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Minimal transport for Google's unofficial translate endpoint. */
final class GoogleSubtitleTranslationTransport implements SubtitleTranslationTransport {
    static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;

    private final OkHttpClient client;

    GoogleSubtitleTranslationTransport() {
        this(SubtitleSearch.CLIENT);
    }

    GoogleSubtitleTranslationTransport(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public String translate(String sourceIso2, String targetIso2, String payload)
            throws IOException {
        Request request = buildRequest(sourceIso2, targetIso2, payload);
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null
                    || body.contentLength() > MAX_RESPONSE_CHARS) {
                throw new IOException("Translation HTTP " + response.code());
            }
            String json = body.string();
            if (json.length() > MAX_RESPONSE_CHARS) {
                throw new IOException("Translation response too large");
            }
            return parseResponse(json);
        }
    }

    Request buildRequest(String sourceIso2, String targetIso2, String payload)
            throws IOException {
        if (sourceIso2 == null || !sourceIso2.matches("[a-z]{2}")
                || !UkrainianSubtitlePolicy.targetIso2().equals(targetIso2)
                || payload == null || payload.trim().isEmpty()
                || payload.getBytes(StandardCharsets.UTF_8).length
                > SubRipDocument.MAX_BATCH_BYTES) {
            throw new IOException("Invalid translation request");
        }
        FormBody body = new FormBody.Builder()
                .add("client", "gtx")
                .add("sl", sourceIso2)
                .add("tl", UkrainianSubtitlePolicy.targetIso2())
                .add("dt", "t")
                .add("q", payload)
                .build();
        return new Request.Builder()
                .url(ENDPOINT)
                .header("User-Agent", "UA-Player/" + BuildConfig.VERSION_NAME)
                .post(body)
                .build();
    }

    static String parseResponse(String body) throws IOException {
        try {
            JSONArray root = new JSONArray(body);
            JSONArray fragments = root.optJSONArray(0);
            if (fragments == null || fragments.length() == 0) {
                throw new IOException("Missing translation fragments");
            }
            StringBuilder translated = new StringBuilder();
            for (int index = 0; index < fragments.length(); index++) {
                JSONArray fragment = fragments.optJSONArray(index);
                if (fragment == null) throw new IOException("Malformed translation fragment");
                String value = fragment.optString(0, null);
                if (value == null) throw new IOException("Missing translated text");
                translated.append(value);
            }
            if (translated.toString().trim().isEmpty()) {
                throw new IOException("Empty translated text");
            }
            return translated.toString();
        } catch (JSONException | RuntimeException error) {
            throw new IOException("Malformed translation JSON", error);
        }
    }
}
