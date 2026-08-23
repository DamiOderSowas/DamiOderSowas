package de.justsimplenetworks.antivpn.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DurationUtilsTest {

    @Test
    void parsesSeconds() {
        assertEquals(Duration.ofSeconds(30), DurationUtils.parse("30s"));
    }

    @Test
    void parsesMinutes() {
        assertEquals(Duration.ofMinutes(10), DurationUtils.parse("10m"));
    }

    @Test
    void parsesHours() {
        assertEquals(Duration.ofHours(1), DurationUtils.parse("1h"));
    }

    @Test
    void parsesDays() {
        assertEquals(Duration.ofDays(1), DurationUtils.parse("1d"));
        assertEquals(Duration.ofDays(7), DurationUtils.parse("7d"));
    }

    @Test
    void parsesCombinedSegments() {
        assertEquals(Duration.ofDays(1).plusHours(2).plusMinutes(30), DurationUtils.parse("1d2h30m"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse("10"));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse("-5m"));
    }
}
