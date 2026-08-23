package de.justsimplenetworks.antivpn.whitelist;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhitelistServiceTest {

    @TempDir
    Path tempDir;

    private WhitelistService service;

    private WhitelistService newService() {
        service = new WhitelistService(tempDir.resolve("whitelist.json"));
        return service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    @Test
    void matchesExactIp() {
        WhitelistService s = newService();
        s.addIp("1.2.3.4", null, "test", "tester");
        assertTrue(s.check("1.2.3.4", null, null).isPresent());
        assertTrue(s.check("1.2.3.5", null, null).isEmpty());
    }

    @Test
    void matchesCidr() {
        WhitelistService s = newService();
        s.addCidr("192.168.0.0/16", null, "test", "tester");
        assertTrue(s.check("192.168.5.5", null, null).isPresent());
        assertTrue(s.check("10.0.0.1", null, null).isEmpty());
    }

    @Test
    void matchesPlayerUuid() {
        WhitelistService s = newService();
        UUID uuid = UUID.randomUUID();
        s.addPlayer(uuid, null, "test", "tester");
        assertTrue(s.check("9.9.9.9", uuid, null).isPresent());
        assertTrue(s.check("9.9.9.9", UUID.randomUUID(), null).isEmpty());
    }

    @Test
    void temporaryEntryExpires() throws InterruptedException {
        WhitelistService s = newService();
        s.addIp("1.2.3.4", Duration.ofMillis(50), "test", "tester");
        assertTrue(s.check("1.2.3.4", null, null).isPresent());
        Thread.sleep(150);
        assertTrue(s.check("1.2.3.4", null, null).isEmpty());
    }

    @Test
    void configEntriesAreReplacedOnReload() {
        WhitelistService s = newService();
        s.reloadConfigEntries(List.of("5.5.5.5"), List.of(), List.of());
        assertTrue(s.check("5.5.5.5", null, null).isPresent());
        s.reloadConfigEntries(List.of(), List.of(), List.of());
        assertTrue(s.check("5.5.5.5", null, null).isEmpty());
    }

    @Test
    void commandEntriesSurviveConfigReload() {
        WhitelistService s = newService();
        s.addIp("6.6.6.6", null, "test", "tester");
        s.reloadConfigEntries(List.of(), List.of(), List.of());
        assertTrue(s.check("6.6.6.6", null, null).isPresent());
    }

    @Test
    void removeDeletesCommandEntry() {
        WhitelistService s = newService();
        s.addIp("7.7.7.7", null, "test", "tester");
        assertTrue(s.remove(WhitelistEntryType.IP, "7.7.7.7"));
        assertTrue(s.check("7.7.7.7", null, null).isEmpty());
    }
}
