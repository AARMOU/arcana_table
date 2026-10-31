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
