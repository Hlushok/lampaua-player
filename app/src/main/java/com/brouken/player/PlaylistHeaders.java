package com.brouken.player;

import java.util.Map;

/** Exact source mapping also covers a prefetched next item; fragments use the current item. */
final class PlaylistHeaders {
    static Map<String, String> forUri(PlaylistApi request, int current, String uri) {
        for (PlaylistApi.Item item : request.items) {
            if (item.source != null && matches(item.source, uri)) return item.headers;
            for (PlaylistApi.Source voice : item.voices) if (matches(voice, uri)) return item.headers;
            for (PlaylistApi.Subtitle subtitle : item.subtitles) if (subtitle.uri.equals(uri)) return item.headers;
        }
        return request.items.get(Math.max(0, Math.min(request.items.size() - 1, current))).headers;
    }
    private static boolean matches(PlaylistApi.Source source, String uri) {
        if (source.uri.equals(uri) || source.qualities.containsValue(uri)) return true;
        if (source.subtitles != null) for (PlaylistApi.Subtitle subtitle : source.subtitles)
            if (subtitle.uri.equals(uri)) return true;
        return false;
    }
}
