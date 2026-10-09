package dev.mitryp.telebridge.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodesTest {
    private long now = 1_000_000;
    private final LinkCodes codes = new LinkCodes(() -> now);

    @Test
    void codeIsSixDigits() {
        assertTrue(codes.issue("Steve").matches("\\d{6}"));
    }

    @Test
    void codeRedeemsOnceForItsPlayer() {
        String code = codes.issue("Steve");
        assertEquals("Steve", codes.redeem(1, code));
        assertNull(codes.redeem(1, code));
    }

    @Test
    void whitespaceInCodeIsIgnored() {
        String code = codes.issue("Steve");
        assertEquals("Steve", codes.redeem(1, " " + code.substring(0, 3) + " " + code.substring(3) + "\n"));
    }

    @Test
    void newCodeReplacesPlayersPreviousOne() {
        String first = codes.issue("Steve");
        String second = codes.issue("Steve");
        assertNull(codes.redeem(1, first));
        assertEquals("Steve", codes.redeem(1, second));
    }

    @Test
    void codeExpiresAfterTenMinutes() {
        String code = codes.issue("Steve");
        now += 601;
        assertNull(codes.redeem(1, code));
    }

    @Test
    void fiveWrongCodesLockOutForTheWindow() {
        for (int i = 0; i < 4; i++) codes.redeem(1, "wrong");
        assertFalse(codes.lockedOut(1));
        codes.redeem(1, "wrong");
        assertTrue(codes.lockedOut(1));
        assertFalse(codes.lockedOut(2));

        now += 601;
        assertFalse(codes.lockedOut(1));
    }

    @Test
    void successClearsFailures() {
        for (int i = 0; i < 4; i++) codes.redeem(1, "wrong");
        codes.redeem(1, codes.issue("Steve"));
        codes.redeem(1, "wrong");
        assertFalse(codes.lockedOut(1));
    }
}
