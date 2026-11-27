package com.brouken.player;

import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Compatibility adapter for the pinned Media3 AAR's seek reset, without reopening the HDMI route. */
final class TunnelingAudioFlush {
    private TunnelingAudioFlush() { }

    static boolean flush(AudioSink sink) {
        if (!(sink instanceof DefaultAudioSink)) return false;
        try {
            Class<?> type = DefaultAudioSink.class;
            Field outputField = field(type, "audioOutput");
            AudioOutput output = (AudioOutput) outputField.get(sink);
            // A changed format must get a new output, just as a normal flush would give it.
            if (output == null || field(type, "pendingConfiguration").get(sink) != null) return false;
            Method reset = type.getDeclaredMethod("resetSinkStateForFlush");
            reset.setAccessible(true);
            Field start = field(type, "startMediaTimeUsNeedsInit");
            Field sync = field(type, "startMediaTimeUsNeedsSync");
            boolean playing = field(type, "playing").getBoolean(sink);
            output.pause();
            output.flush();
            reset.invoke(sink);
            start.setBoolean(sink, true);
            sync.setBoolean(sink, false);
            if (playing) output.play();
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // A different library or output must still take the ordinary, supported flush path.
            return false;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
