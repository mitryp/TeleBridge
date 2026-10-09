package dev.mitryp.telebridge.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarkdownTest {
    @Test
    void escapesEverySpecialCharacter() {
        assertEquals("\\_\\*\\[\\]\\(\\)\\~\\`\\>\\#\\+\\-\\=\\|\\{\\}\\.\\!", Markdown.escapeV2("_*[]()~`>#+-=|{}.!"));
    }

    @Test
    void escapesPlayerNamesWithUnderscores() {
        assertEquals("<B0brik\\_976\\> hi", Markdown.escapeV2ServiceAware("<B0brik_976> hi"));
    }

    @Test
    void keepsLeadingQuoteOnEachLine() {
        assertEquals("> a\\.\nb \\> c\n> d", Markdown.escapeV2ServiceAware("> a.\nb > c\n> d"));
    }

    @Test
    void handlesEmptyInput() {
        assertNull(Markdown.escapeV2ServiceAware(null));
        assertEquals("", Markdown.escapeV2ServiceAware(""));
    }
}
