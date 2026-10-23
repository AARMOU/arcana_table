package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogAndHandLayoutTest {
    @Test fun redactorRemovesAddressesSecretsAndIds() {
        val raw = "Connecting to wss://magic.wingedsheep.com/game from 192.168.1.20:8080 host game.example.org " +
            "token=abcdEFGH1234 password: hunter2 room code: ABCD-12 player 45eef9d8-dc1f-4a3e-a264-0fc59005c048 " +
            "mail me@example.com key ${"x".repeat(40)}"
        val clean = DiagnosticRedactor.redact(raw)
        listOf("wingedsheep", "192.168", "example.org", "abcdEFGH1234", "hunter2", "ABCD-12", "45eef9d8", "me@example.com", "x".repeat(40))
            .forEach { assertFalse("leaked $it in: $clean", clean.contains(it)) }
        assertTrue(clean.contains("<server>"))
    }

    @Test fun redactorKeepsGameplayText() {
        val text = "Bello, Bard of the Brambles cast; version 0.15.53 build 86; turn 3 PRECOMBAT_MAIN"
        assertEquals(text, DiagnosticRedactor.redact(text))
    }

    @Test fun searchFiltersByTermsAndCategory() {
        val log = DiagnosticLog(clock = { 0L })
        log.record(DiagnosticCategory.CONNECTION, "Argentum connection status: connected")
        log.record(DiagnosticCategory.UNSUPPORTED, "Needs handling: SplitPilesDecision")
        log.record(DiagnosticCategory.ACTION, "Sent PassPriority")
        assertEquals(1, log.search("split piles").size)
        assertEquals(1, log.search("SPLITPILES").size)
        assertEquals(1, log.search("", setOf(DiagnosticCategory.ACTION)).size)
        assertEquals(1, log.search("needs handling").size)
        assertEquals(3, log.search("").size)
    }

    @Test fun exportMasksPrivateEntriesAndSurvivesRoundTrip() {
        val log = DiagnosticLog(clock = { 1_700_000_000_000L })
        log.record(DiagnosticCategory.ENGINE, "Hand: Lightning Bolt, Forest", private = true)
        log.record(DiagnosticCategory.ERROR, "Failed to reach https://secret.example.com/api")
        val exported = log.exportText()
        assertFalse(exported.contains("Lightning Bolt"))
        assertFalse(exported.contains("secret.example.com"))
        assertTrue(exported.contains("[private game detail omitted]"))
        val restored = DiagnosticLog().apply { restore(log.serialize()) }
        assertEquals(2, restored.entries.size)
        assertTrue(restored.entries[0].private)
        assertEquals(DiagnosticCategory.ERROR, restored.entries[1].category)
        // New entries continue the sequence instead of reusing numbers.
        assertEquals(3, restored.record(DiagnosticCategory.APP, "next").sequence)
    }

    @Test fun legacyLinesRestoreAsAppEntries() {
        val log = DiagnosticLog().apply { restore("#00012  2026-10-16 12:00:00.000  Application opened") }
        assertEquals("Application opened", log.entries.single().message)
    }

    @Test fun capacityIsBounded() {
        val log = DiagnosticLog(capacity = 3)
        repeat(10) { log.record(DiagnosticCategory.APP, "e$it") }
        assertEquals(listOf("e7", "e8", "e9"), log.entries.map { it.message })
    }

    // ---- Hand drawer layout ----

    @Test fun expandingTheTrayGrowsCardsAndUsesMoreRows() {
        val small = HandLayout.compute(7, areaWidth = 360f, areaHeight = 150f, arrangement = HandArrangement.AUTO_FIT)
        val large = HandLayout.compute(7, areaWidth = 360f, areaHeight = 560f, arrangement = HandArrangement.AUTO_FIT)
        assertTrue("larger tray should give larger cards", large.cardWidth > small.cardWidth)
        assertTrue("larger tray should wrap into more rows", large.rows > small.rows)
        assertFalse(large.scrolls)
        // Everything fits inside the area.
        assertTrue(large.columns * large.cardWidth + (large.columns - 1) * HandLayout.GAP <= 360f + 0.5f)
        assertTrue(large.rows * large.cardHeight + (large.rows - 1) * HandLayout.GAP <= 560f + 0.5f)
    }

    @Test fun foldableWideTrayUsesFewerRows() {
        val phone = HandLayout.compute(10, 360f, 420f, HandArrangement.AUTO_FIT)
        val unfolded = HandLayout.compute(10, 840f, 420f, HandArrangement.AUTO_FIT)
        assertTrue(unfolded.rows <= phone.rows)
        assertTrue(unfolded.cardWidth >= phone.cardWidth)
    }

    @Test fun hugeHandFallsBackToReadableScrollingGrid() {
        val grid = HandLayout.compute(40, 320f, 120f, HandArrangement.AUTO_FIT)
        assertTrue(grid.scrolls)
        assertTrue(grid.cardWidth >= 54f)
        assertTrue(grid.columns * grid.rows >= 40)
    }

    @Test fun sizeScaleCapsCardWidth() {
        val capped = HandLayout.compute(1, 800f, 900f, HandArrangement.AUTO_FIT, sizeScale = 0.7f)
        val full = HandLayout.compute(1, 800f, 900f, HandArrangement.AUTO_FIT, sizeScale = 1.4f)
        assertTrue(capped.cardWidth < full.cardWidth)
        assertEquals(HandLayout.BASE_MAX_CARD_WIDTH * 0.7f, capped.cardWidth, 1f)
    }

    @Test fun singleRowFollowsTrayHeightAndScrollsWhenWide() {
        val short = HandLayout.compute(7, 360f, 180f, HandArrangement.SINGLE_ROW)
        val tall = HandLayout.compute(7, 360f, 300f, HandArrangement.SINGLE_ROW)
        assertEquals(1, short.rows)
        assertTrue(tall.cardWidth > short.cardWidth)
        assertTrue(tall.scrolls)
    }

    @Test fun emptyHandIsSafe() {
        val grid = HandLayout.compute(0, 0f, 0f, HandArrangement.AUTO_FIT)
        assertEquals(0, grid.rows)
    }
}
