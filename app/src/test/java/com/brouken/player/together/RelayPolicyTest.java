package com.brouken.player.together;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RelayPolicyTest {
    @After
    public void resetRelay() {
        Relay.setBase(null);
    }

    @Test
    public void relayNormalizesAndRejectsUnsafeSchemes() {
        Relay.setBase("wss://relay.example/channel");
        assertEquals("wss://relay.example/channel/", Relay.base());

        Relay.setBase("https://not-a-websocket.example/");
        assertEquals(Relay.DEFAULT_BASE, Relay.base());

        Relay.setBase("");
        assertEquals("wss://itty.ws/c/", Relay.base());
    }
}
