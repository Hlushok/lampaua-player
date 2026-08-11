package com.brouken.player;

final class TrackMetadata {
    enum Type { VIDEO, AUDIO, SUBTITLE, UNKNOWN }

    final int trackId;
    final String name;
    final String language;
    final Type type;

    TrackMetadata(int trackId, String name, String language, Type type) {
        this.trackId = trackId;
        this.name = name;
        this.language = language;
        this.type = type;
    }
}
