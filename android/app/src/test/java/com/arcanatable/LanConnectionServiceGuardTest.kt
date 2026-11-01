package com.arcanatable

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for losing the live game connection while opening Arcana Control.
 *
 * Socket/host instances change during joining and reconnecting. Compose disposes the
 * old instance each time, so its cleanup must not stop the foreground connection service.
 * Check the actual effect wiring rather than a copy of the Boolean policy.
 */
class LanConnectionServiceGuardTest {
    private val effects by lazy {
        File("src/main/java/com/arcanatable/TableEffects.kt").readText()
    }

    @Test fun roomClientReplacementNeverStopsForegroundGuardFromKeyedCleanup() {
        val keyedCleanup = effects.substringAfter(
            "DisposableEffect(hostOwnedByEffect, clientOwnedByEffect, cloudClientOwnedByEffect)"
        ).substringBefore("LaunchedEffect(players)")
        assertTrue("Check that the room's keyed cleanup is still present", keyedCleanup.contains("hostOwnedByEffect?.close()"))
        assertTrue("Check that cloud sockets still close on replacement", keyedCleanup.contains("cloudClientOwnedByEffect?.close()"))
        assertFalse(
            "Replacing a socket must not stop the foreground game guard",
            keyedCleanup.contains("LanConnectionService.stop(context)")
        )
    }

    @Test fun wifiHostLobbyStartsConnectionGuardBeforeGameStarts() {
        assertTrue(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = true, argentumEnabled = false,
        ))
    }

    @Test fun directedCloudHostLobbyStartsConnectionGuardBeforeGameStarts() {
        // Cloud host lives in wifiServer (ArgentumHostLink), not legacy cloudRoomClient.
        assertTrue(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = true, argentumEnabled = false,
        ))
    }

    @Test fun activeArgentumGameStillStartsConnectionGuard() {
        assertTrue(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = false, argentumEnabled = true,
        ))
    }

    @Test fun legacyMultiplayerConnectionsStillStartGuard() {
        for (host in 0..2) {
            assertTrue(shouldKeepMultiplayerConnectionGuard(
                legacyHost = host == 0, legacyClient = host == 1,
                cloudClient = host == 2, multiTransport = false, argentumEnabled = false,
            ))
        }
    }

    @Test fun idleTableWithoutAnyRoomStopsConnectionGuard() {
        assertFalse(shouldKeepMultiplayerConnectionGuard(
            legacyHost = false, legacyClient = false, cloudClient = false,
            multiTransport = false, argentumEnabled = false,
        ))
    }

    @Test fun actualWifiHostStateIsObservableAndIncludedInServiceLifecycle() {
        val controller = File("src/main/java/com/arcanatable/TableController.kt").readText()
        assertTrue(
            "wifiServer must be Compose-observable or the foreground service misses lobby joins",
            controller.contains("var wifiServer by run { mutableStateOf<ArgentumHostLink?>(null) }")
        )
        assertTrue(
            "Live host must count as active even before Argentum is enabled",
            effects.contains("multiTransport = wifiServer != null")
        )
    }

    @Test fun foregroundRecoveryOnlyForPausedRemoteArgentumGame() {
        assertTrue(shouldResumePausedArgentumOnForeground(
            true, true, false, ArgentumConnectionStatus.PAUSED))
        assertFalse(shouldResumePausedArgentumOnForeground(
            false, true, false, ArgentumConnectionStatus.PAUSED))
        assertFalse(shouldResumePausedArgentumOnForeground(
            true, false, false, ArgentumConnectionStatus.PAUSED))
        assertFalse(shouldResumePausedArgentumOnForeground(
            true, true, true, ArgentumConnectionStatus.PAUSED))
        for (status in listOf(
            ArgentumConnectionStatus.CONNECTED,
            ArgentumConnectionStatus.CONNECTING,
            ArgentumConnectionStatus.DISCONNECTED,
            ArgentumConnectionStatus.REPLACED,
        )) {
            assertFalse(shouldResumePausedArgentumOnForeground(true, true, false, status))
        }
    }

    @Test fun foregroundRecoveryEffectUsesSocketRetryAndNotNewSession() {
        val branch = effects.substringAfter(
            "LaunchedEffect(appInForeground, argentumStatus, argentumEnabled, argentumOffline)"
        ).substringBefore("LaunchedEffect(argentumQueue)")
        assertTrue(branch.contains("shouldResumePausedArgentumOnForeground("))
        assertTrue(branch.contains("argentumSocket.retryNow()"))
        assertFalse(branch.contains("argentumSocket.connect("))
    }

    @Test fun guardStopsOnlyAfterLastGameEndsOrScreenActuallyDisposes() {
        val activeGuard = effects.substringAfter(
            "LaunchedEffect(keepGameConnectionActive)"
        ).substringBefore("LaunchedEffect(argentumEnabled)")
        assertTrue("Game starts connection guard", activeGuard.contains("if (keepGameConnectionActive) LanConnectionService.start(context)"))
        assertTrue("No game stops connection guard", activeGuard.contains("else LanConnectionService.stop(context)"))
        assertTrue(
            "Actual screen teardown must also release the connection guard",
            effects.contains("DisposableEffect(context) {\n        onDispose { LanConnectionService.stop(context) }")
        )
    }
}
