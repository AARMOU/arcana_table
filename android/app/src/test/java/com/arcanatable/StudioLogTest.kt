package com.arcanatable

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Owner, Nov 7: Studio keeps its own small log, separate from Arcana's debug logs, that can be shared and cleared. */
class StudioLogTest {
    private val dir = Files.createTempDirectory("studio-log").toFile()
    @After fun done() { StudioLog.clear(); dir.deleteRecursively() }

    @Test fun writesItsOwnFileDiffsEditsCapsAndClears() {
        StudioLog.init(dir)
        StudioLog.add("Opened Studio")
        StudioLog.add("Edit PASS: " + StudioLog.diff(StudioControlStyle(width = 156f, height = 40f), StudioControlStyle(width = 164f, height = 40f)))
        val text = StudioLog.text()
        assertTrue(text.contains("Opened Studio"))
        assertTrue(text.contains("Edit PASS: width 156→164"))
        assertTrue("only Studio's own file", dir.resolve("studio/studio-log.txt").exists() && dir.listFiles()!!.single().name == "studio")
        repeat(3_000) { StudioLog.add("filler line $it " + "x".repeat(40)) }
        assertTrue("capped", StudioLog.text().length <= StudioLog.CAP + 8_000 + 700)
        StudioLog.clear()
        assertEquals("", StudioLog.text())
    }

    @Test fun handDiffNamesWhatMoved() {
        val ref = StudioLayout.reference()
        val moved = ref.copy(elements = ref.elements.map { if (it.id == "undo") it.copy(h = 48f) else it })
        assertTrue(StudioLog.handDiff(ref, moved).contains("size"))
        assertEquals("hand: no change", StudioLog.handDiff(ref, ref))
    }
}
