package com.brouken.player;

import android.app.Application;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class SubtitleTextDataSourceTest {
    private static final String SRT = "1\n00:00:01,000 --> 00:00:03,000\n"
            + "Привіт! Українські субтитри: їжак, ґанок, єдність, історія пригод.\n\n";

    @Test public void windows1251FromAnOpaquePlaylistUrlBecomesReadableUtf8() throws Exception {
        byte[] input = (SRT + SRT + SRT).getBytes(Charset.forName("windows-1251"));
        SubtitleTextDataSource source = new SubtitleTextDataSource(new ByteArrayDataSource(input), uri -> true);
        DataSpec spec = new DataSpec(Uri.parse("https://media/subtitle?id=42"));
        long size = source.open(spec);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[17]; int count;
        while ((count = source.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) output.write(buffer, 0, count);
        assertEquals(SRT + SRT + SRT, output.toString("UTF-8"));
        assertEquals(output.size(), size);
        source.close();
    }

    @Test public void utf8AndUtf16PreserveLettersAndMarkup() throws Exception {
        String ass = "[Script Info]\n[Events]\nDialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\\i1}Привіт!";
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, StandardCharsets.UTF_16})
            assertEquals(ass, new String(SubtitleTextDataSource.utf8(ass.getBytes(charset)), StandardCharsets.UTF_8));
    }

    @Test public void byteRangesReferToTheConvertedTextAndVideoBytesAreUntouched() throws Exception {
        byte[] text = SRT.getBytes(StandardCharsets.UTF_8);
        SubtitleTextDataSource subtitle = new SubtitleTextDataSource(new ByteArrayDataSource(text), uri -> true);
        assertEquals(7, subtitle.open(new DataSpec.Builder().setUri("file:///subtitle.srt").setPosition(2).setLength(7).build()));
        byte[] result = new byte[20]; assertEquals(7, subtitle.read(result, 0, 20));
        assertArrayEquals(java.util.Arrays.copyOfRange(text, 2, 9), java.util.Arrays.copyOf(result, 7));
        assertEquals(C.RESULT_END_OF_INPUT, subtitle.read(result, 0, 20)); subtitle.close();
        byte[] binary = new byte[]{0, (byte) 255, (byte) 128, 42};
        SubtitleTextDataSource video = new SubtitleTextDataSource(new ByteArrayDataSource(binary), uri -> false);
        assertEquals(4, video.open(new DataSpec(Uri.parse("https://media/video"))));
        assertEquals(4, video.read(result, 0, 20));
        assertArrayEquals(binary, java.util.Arrays.copyOf(result, 4)); video.close();
    }
}
