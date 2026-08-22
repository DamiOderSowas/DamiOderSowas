package de.justsimplenetworks.antivpn.bypass;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BypassServiceTest {

    @TempDir
    Path tempDir;

    private BypassService service;

    private BypassService newService() {
        service = new BypassService(tempDir.resolve("bypass.json"));
        return service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    @Test
    void grantedBypassIsActive() {
        BypassService s = newService();
        UUID uuid = UUID.randomUUID();
        s.grant(uuid, "1.2.3.4", Duration.ofMinutes(30), "test", "staff");
        assertTrue(s.isActive(uuid));
    }

    @Test
    void unknownPlayerHasNoBypass() {
        BypassService s = newService();
        assertFalse(s.isActive(UUID.randomUUID()));
    }

    @Test
    void bypassExpiresAutomatically() throws InterruptedException {
        BypassService s = newService();
        UUID uuid = UUID.randomUUID();
        s.grant(uuid, "1.2.3.4", Duration.ofMillis(50), "test", "staff");
        assertTrue(s.isActive(uuid));
        Thread.sleep(150);
        assertFalse(s.isActive(uuid));
    }

    @Test
    void revokeRemovesBypassImmediately() {
        BypassService s = newService();
        UUID uuid = UUID.randomUUID();
        s.grant(uuid, "1.2.3.4", Duration.ofMinutes(30), "test", "staff");
        s.revoke(uuid);
        assertFalse(s.isActive(uuid));
    }
}
