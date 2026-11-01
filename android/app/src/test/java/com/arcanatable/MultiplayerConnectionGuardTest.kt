package com.arcanatable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B137 #1: the connection service must protect waiting hosts, not only running games. */
class MultiplayerConnectionGuardTest {
    @Test fun newWifiHostLobbyStartsGuardBeforeArgentum() {
        assertTrue(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = true, argentumEnabled = false,
        ))
    }

    @Test fun newOnlineHostLobbyStartsGuardBeforeArgentum() {
        // Both new host types occupy TableController.wifiServer (ArgentumHostLink).
        assertTrue(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = true, argentumEnabled = false,
        ))
    }

    @Test fun runningArgentumGameStillStartsGuard() {
        assertTrue(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = false, argentumEnabled = true,
        ))
    }

    @Test fun legacyHostAndClientsRemainProtected() {
        for (legacy in 0..2) {
            assertTrue(shouldKeepMultiplayerConnectionGuard(
                legacyHost = legacy == 0, legacyClient = legacy == 1,
                cloudClient = legacy == 2, multiTransport = false, argentumEnabled = false,
            ))
        }
    }

    @Test fun idleTableDoesNotKeepForegroundGuardRunning() {
        assertFalse(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = false, argentumEnabled = false,
        ))
    }
}
