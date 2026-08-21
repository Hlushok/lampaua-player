package com.brouken.player.together;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RoomTest {
    @After
    public void resetInvitePage() {
        Room.setInvitePage(null);
    }

    @Test
    public void lampaLetterAndUaNumericCodesAreAccepted() {
        assertTrue(Room.isCode("ABC234"));
        assertTrue(Room.isCode("123456"));
        assertFalse(Room.isCode("12 456"));
        assertFalse(Room.isCode("12345"));
    }

    @Test
    public void passwordChangesThePrivateRelayChannel() {
        Room open = new Room("ABC234", "");
        Room locked = new Room("ABC234", "secret");

        assertNotEquals(open.channel(), locked.channel());
        assertTrue(open.channel().startsWith("lparty-r-"));
        assertEquals(24, open.channel().substring("lparty-r-".length()).length());
    }

    @Test
    public void invalidInvitePageFallsBackToUaDefault() {
        Room.setInvitePage("javascript:alert(1)");
        assertEquals(Room.DEFAULT_INVITE_PAGE, Room.invitePage());

        Room.setInvitePage("https://example.test/lparty/?old=1");
        assertEquals("https://example.test/lparty/", Room.invitePage());
    }
}
