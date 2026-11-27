package com.brouken.player;

import android.app.Application;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TunnelingAudioFlushTest {
    @Test public void seekFlushKeepsTheOutputAndResetsItsMediaClock() throws Exception {
        DefaultAudioSink sink = new DefaultAudioSink.Builder(RuntimeEnvironment.getApplication()).build();
        sink.configure(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setSampleRate(48000).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build(), 0, null);
        List<String> calls = new ArrayList<>();
        AudioOutput output = (AudioOutput) Proxy.newProxyInstance(AudioOutput.class.getClassLoader(),
                new Class<?>[]{AudioOutput.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
        ReflectionHelpers.setField(sink, "audioOutput", output);
        ReflectionHelpers.setField(sink, "playing", true);
        assertTrue(TunnelingAudioFlush.flush(sink));
        assertSame(output, ReflectionHelpers.getField(sink, "audioOutput"));
        assertTrue(ReflectionHelpers.<Boolean>getField(sink, "startMediaTimeUsNeedsInit"));
        assertEquals(java.util.Arrays.asList("pause", "flush", "play"), calls);
        assertFalse(calls.contains("release"));
    }
    @Test public void absentOutputUsesTheOrdinaryFlushPath() {
        DefaultAudioSink sink = new DefaultAudioSink.Builder(RuntimeEnvironment.getApplication()).build();
        assertFalse(TunnelingAudioFlush.flush(sink));
    }
}
