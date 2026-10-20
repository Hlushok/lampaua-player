package com.brouken.player;

import android.app.Application;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.AudioSink;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TunnelingAudioSinkTest {
    private AudioSink wrap(TunnelingStereoProcessor processor, boolean forceDecode,
                           List<String> calls) throws Exception {
        final AudioSink delegate = (AudioSink) Proxy.newProxyInstance(AudioSink.class.getClassLoader(),
                new Class<?>[]{AudioSink.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getReturnType() == boolean.class) return true;
                    if (method.getReturnType() == int.class) return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
        final Constructor<?> constructor = Class.forName(
                "com.brouken.player.PlayerActivity$AudioPassthroughDenylistSink")
                .getDeclaredConstructor(AudioSink.class, Set.class, boolean.class,
                        int.class, int.class, TunnelingStereoProcessor.class);
        constructor.setAccessible(true);
        return (AudioSink) constructor.newInstance(delegate, Collections.emptySet(), forceDecode, 0, 0, processor);
    }

    @Test public void sinkTunnelingEventsActuallyControlTheInstalledProcessor() throws Exception {
        final TunnelingStereoProcessor processor = new TunnelingStereoProcessor(true);
        final List<String> calls = new ArrayList<>();
        final AudioSink sink = wrap(processor, false, calls);
        final AudioProcessor.AudioFormat six = new AudioProcessor.AudioFormat(48000, 6, C.ENCODING_PCM_16BIT);
        assertSame(AudioProcessor.AudioFormat.NOT_SET, processor.configure(six));
        sink.enableTunnelingV21();
        assertTrue(ReflectionHelpers.<Boolean>callInstanceMethod(sink, "isTunneling"));
        assertEquals(2, processor.configure(six).channelCount);
        sink.disableTunneling();
        assertFalse(ReflectionHelpers.<Boolean>callInstanceMethod(sink, "isTunneling"));
        assertSame(AudioProcessor.AudioFormat.NOT_SET, processor.configure(six));
        assertEquals(java.util.Arrays.asList("enableTunnelingV21", "disableTunneling"), calls);
    }

    @Test public void forcedDecodingStillAcceptsPcmAndRefusesBitstreams() throws Exception {
        final AudioSink sink = wrap(new TunnelingStereoProcessor(true), true, new ArrayList<>());
        final Format compressed = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build();
        final Format pcm = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48000).setChannelCount(6).build();
        assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(compressed));
        assertFalse(sink.supportsFormat(compressed));
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(pcm));
        assertTrue(sink.supportsFormat(pcm));
    }
}
