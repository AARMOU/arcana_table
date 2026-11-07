package com.arcanatable

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tutorials cope with Arcana Studio replacing Adjust UI (owner D33) without editing the shared catalog. */
class StudioTutorialLessonsTest {
    private val catalog = parseTutorialLessons(File("src/main/assets/tutorials/catalog.json").readText())

    @Test fun studioBuildsPointTheSettingsStepAtStudioAndDropTheRetiredAdjustUiPage() {
        val adapted = studioTutorialLessons(catalog)
        val steps = adapted.flatMap { it.steps }
        assertTrue("the old Adjust UI page is no longer reachable", steps.none { it.screen == "layout" })
        assertTrue("no lesson still says Adjust UI", steps.none { "Adjust UI" in it.title + it.body + it.hint })
        val studio = steps.filter { it.target == "setting-adjust-ui" }
        assertEquals(2, studio.size)
        studio.forEach { assertEquals("Arcana Studio", it.title); assertEquals("next", it.actionKind) }
        // Every other step is kept exactly as written.
        val kept = catalog.flatMap { it.steps }.filter { it.screen != "layout" && it.target != "setting-adjust-ui" && "Adjust UI" !in it.title + it.body + it.hint }
        assertTrue(steps.containsAll(kept))
        assertEquals(catalog.size, adapted.size)
        assertFalse(adapted.any { it.steps.isEmpty() })
    }
}
