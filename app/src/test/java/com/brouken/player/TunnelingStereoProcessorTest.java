package com.brouken.player;

import android.app.Application;
import android.media.AudioFormat;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TunnelingStereoProcessorTest {
    private TunnelingStereoProcessor active(int channels, int encoding) throws Exception {
        final TunnelingStereoProcessor processor = new TunnelingStereoProcessor(true);
        processor.setTunneling(true);
        final AudioProcessor.AudioFormat output = processor.configure(
                new AudioProcessor.AudioFormat(48000, channels, encoding));
        assertEquals(2, output.channelCount);
        assertEquals(48000, output.sampleRate);
        assertEquals(encoding, output.encoding);
        processor.flush();
        return processor;
    }

    private ByteBuffer shorts(int... values) {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * 2).order(ByteOrder.nativeOrder());
        for (int value : values) buffer.putShort((short) value);
        buffer.flip();
        return buffer;
    }

    @Test public void centreDialogueReachesBothStereoChannelsAndFramesKeepTheirDuration() throws Exception {
        final TunnelingStereoProcessor processor = active(6, C.ENCODING_PCM_16BIT);
        final ByteBuffer input = shorts(0,0,1000,0,0,0, 1000,2000,0,0,0,0);
        processor.queueInput(input);
        assertFalse(input.hasRemaining());
        final ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(8, output.remaining()); // Two input frames still mean two output frames.
        assertEquals(707, output.getShort(), 1);
        assertEquals(707, output.getShort(), 1);
        assertEquals(1000, output.getShort());
        assertEquals(2000, output.getShort());
    }

    @Test public void rearAndSideChannelsStayOnTheirOwnSide() throws Exception {
        final TunnelingStereoProcessor processor = active(8, C.ENCODING_PCM_16BIT);
        processor.queueInput(shorts(0,0,0,0,1000,0,1000,0, 0,0,0,0,0,1000,0,1000));
        final ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(1414, output.getShort(), 1);
        assertEquals(0, output.getShort());
        assertEquals(0, output.getShort());
        assertEquals(1414, output.getShort(), 1);
    }

    @Test public void fullScaleMixClipsInsteadOfWrappingIntoNoise() throws Exception {
        final TunnelingStereoProcessor processor = active(6, C.ENCODING_PCM_16BIT);
        processor.queueInput(shorts(32767,32767,32767,32767,32767,32767,
                -32768,-32768,-32768,-32768,-32768,-32768));
        final ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(32767, output.getShort());
        assertEquals(32767, output.getShort());
        assertEquals(-32768, output.getShort());
        assertEquals(-32768, output.getShort());
    }

    @Test public void floatAudioIsMixedWithoutChangingItsEncoding() throws Exception {
        final TunnelingStereoProcessor processor = active(6, C.ENCODING_PCM_FLOAT);
        final ByteBuffer input = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder());
        for (float value : new float[]{0,0,1,0,0,0}) input.putFloat(value);
        input.flip();
        processor.queueInput(input);
        final ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(0.70710677f, output.getFloat(), 0.0001f);
        assertEquals(0.70710677f, output.getFloat(), 0.0001f);
    }

    @Test public void bypassesStereoMultichannelReceiversUnknownLayoutsAndNonPcm() throws Exception {
        final AudioProcessor.AudioFormat six = new AudioProcessor.AudioFormat(48000,6,C.ENCODING_PCM_16BIT);
        assertSame(AudioProcessor.AudioFormat.NOT_SET, new TunnelingStereoProcessor(true).configure(six));
        final TunnelingStereoProcessor wideRoute = new TunnelingStereoProcessor(false);
        wideRoute.setTunneling(true);
        assertSame(AudioProcessor.AudioFormat.NOT_SET, wideRoute.configure(six));
        final TunnelingStereoProcessor stereo = new TunnelingStereoProcessor(true);
        stereo.setTunneling(true);
        for (int channels : new int[]{1,2,10,12}) assertSame(AudioProcessor.AudioFormat.NOT_SET,
                stereo.configure(new AudioProcessor.AudioFormat(48000,channels,C.ENCODING_PCM_16BIT)));
        assertSame(AudioProcessor.AudioFormat.NOT_SET,
                stereo.configure(new AudioProcessor.AudioFormat(48000,6,C.ENCODING_AC3)));
    }

    @Test public void disablingTunnelingAndChangingTrackDoesNotKeepAnOldMatrix() throws Exception {
        final TunnelingStereoProcessor processor = active(6, C.ENCODING_PCM_16BIT);
        processor.setTunneling(false);
        assertSame(AudioProcessor.AudioFormat.NOT_SET,
                processor.configure(new AudioProcessor.AudioFormat(48000,6,C.ENCODING_PCM_16BIT)));
        processor.flush();
        assertFalse(processor.isActive());
        processor.setTunneling(true);
        assertEquals(2, processor.configure(new AudioProcessor.AudioFormat(44100,8,C.ENCODING_PCM_16BIT)).channelCount);
    }

    @Test public void speakerLayoutNotBitstreamLimitDeterminesTheRoute() {
        assertEquals(2, TunnelingStereoProcessor.routeChannels(Arrays.asList(AudioFormat.CHANNEL_OUT_STEREO)));
        assertEquals(6, TunnelingStereoProcessor.routeChannels(Arrays.asList(
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.CHANNEL_OUT_5POINT1)));
        assertEquals(0, TunnelingStereoProcessor.routeChannels(Arrays.asList(0)));
    }

    @Test public void queuedOldTrackUsesItsMatrixUntilTheConfigurationIsFlushed() throws Exception {
        final TunnelingStereoProcessor processor = active(6, C.ENCODING_PCM_16BIT);
        processor.configure(new AudioProcessor.AudioFormat(48000, 8, C.ENCODING_PCM_16BIT));
        processor.queueInput(shorts(0,0,1000,0,0,0));
        assertEquals(707, processor.getOutput().order(ByteOrder.nativeOrder()).getShort(), 1);
        processor.flush();
        processor.queueInput(shorts(0,0,1000,0,0,0,0,0));
        assertEquals(707, processor.getOutput().order(ByteOrder.nativeOrder()).getShort(), 1);
        processor.queueEndOfStream();
        assertTrue(processor.isEnded());
        processor.reset();
    }

    @Test public void alternateLayoutsDoNotConfuseCentreAndRearSpeakers() throws Exception {
        for (int channels : new int[]{3,5,7}) {
            final TunnelingStereoProcessor processor = active(channels, C.ENCODING_PCM_16BIT);
            final int[] frame = new int[channels];
            frame[2] = 1000;
            processor.queueInput(shorts(frame));
            final ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
            assertEquals(707, output.getShort(), 1);
            assertEquals(707, output.getShort(), 1);
        }
        final TunnelingStereoProcessor quad = active(4, C.ENCODING_PCM_16BIT);
        quad.queueInput(shorts(0,0,1000,0));
        final ByteBuffer output = quad.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(707, output.getShort(), 1);
        assertEquals(0, output.getShort());
    }
}
