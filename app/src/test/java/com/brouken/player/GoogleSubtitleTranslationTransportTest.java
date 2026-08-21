package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Request;

public class GoogleSubtitleTranslationTransportTest {

    @Test
    public void concatenatesGoogleTranslationFragments() throws Exception {
        assertEquals("Привіт, світе!",
                GoogleSubtitleTranslationTransport.parseResponse(
                        "[[[\"Привіт, \",\"Hello, \",null,null,1],"
                                + "[\"світе!\",\"world!\",null,null,1]],null,\"en\"]"));
    }

    @Test
    public void buildsFixedUkrainianHttpsFormPost() throws Exception {
        GoogleSubtitleTranslationTransport transport =
                new GoogleSubtitleTranslationTransport();
        Request request = transport.buildRequest("en", "uk", "Hello.");

        assertEquals("POST", request.method());
        assertEquals("https", request.url().scheme());
        assertEquals("translate.googleapis.com", request.url().host());
        assertTrue(request.body() instanceof FormBody);
        FormBody body = (FormBody) request.body();
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < body.size(); index++) {
            values.put(body.name(index), body.value(index));
        }
        assertEquals("gtx", values.get("client"));
        assertEquals("en", values.get("sl"));
        assertEquals("uk", values.get("tl"));
        assertEquals("t", values.get("dt"));
        assertEquals("Hello.", values.get("q"));
        assertEquals(5, values.size());
    }

    @Test
    public void rejectsMalformedResponseAndNonUkrainianTarget() throws Exception {
        rejectsResponse("{}");
        rejectsResponse("[]");
        rejectsResponse("[[[]]]");
        try {
            new GoogleSubtitleTranslationTransport().buildRequest("en", "pl", "Hello");
            fail("Expected non-Ukrainian target rejection");
        } catch (IOException expected) {
            // Expected.
        }
    }

    private static void rejectsResponse(String body) {
        try {
            GoogleSubtitleTranslationTransport.parseResponse(body);
            fail("Expected malformed response rejection");
        } catch (IOException expected) {
            // Expected.
        }
    }
}
