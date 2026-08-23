package de.justsimplenetworks.antivpn.blacklist;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BlacklistServiceTest {

    @TempDir
    Path tempDir;

    private BlacklistService service;

    private BlacklistService newService() {
        service = new BlacklistService(tempDir.resolve("blacklist.json"));
        return service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    @Test
    void matchesExactIp() {
        BlacklistService s = newService();
        s.addIp("1.2.3.4", null, "test", "tester");
        assertTrue(s.check("1.2.3.4", null, null).isPresent());
    }

    @Test
    void matchesCidr() {
        BlacklistService s = newService();
        s.addCidr("1.2.0.0/16", null, "test", "tester");
        assertTrue(s.check("1.2.3.4", null, null).isPresent());
        assertTrue(s.check("1.3.3.4", null, null).isEmpty());
    }

    @Test
    void matchesPlayerUuid() {
        BlacklistService s = newService();
        UUID uuid = UUID.randomUUID();
        s.addPlayer(uuid, null, "test", "tester");
        assertTrue(s.check("9.9.9.9", uuid, null).isPresent());
    }

    @Test
    void matchesAsn() {
        BlacklistService s = newService();
        s.addAsn(12345, null, "test", "tester");
        assertTrue(s.checkAsn(12345).isPresent());
        assertTrue(s.checkAsn(54321).isEmpty());
    }

    @Test
    void temporaryEntryExpires() throws InterruptedException {
        BlacklistService s = newService();
        s.addIp("1.2.3.4", Duration.ofMillis(50), "test", "tester");
        assertTrue(s.check("1.2.3.4", null, null).isPresent());
        Thread.sleep(150);
        assertTrue(s.check("1.2.3.4", null, null).isEmpty());
    }

    @Test
    void configEntriesIncludeAsns() {
        BlacklistService s = newService();
        s.reloadConfigEntries(List.of(), List.of(), List.of(), List.of(999));
        assertTrue(s.checkAsn(999).isPresent());
    }
}
