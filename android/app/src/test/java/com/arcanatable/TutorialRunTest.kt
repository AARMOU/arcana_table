package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** How a practice lesson moves on when the real game gets ahead of (or around) the text. */
class TutorialRunTest {
    private val lessons = parseTutorialLessons(File("src/main/assets/tutorials/catalog.json").readText())
    private fun run(lesson: String, atStep: String): TutorialRun {
        val l = lessons.first { it.id == lesson }
        return TutorialRun(l, practice = true, startIndex = l.steps.indexOfFirst { it.id == atStep })
    }

    @Test fun castingWithoutTappingForManaFirstDoesNotStrandTheLesson() {
        val r = run("first-turn", "make-mana")
        assertTrue(r.signal("event", "SpellCast"))
        assertEquals("spell-stack", r.step?.id)
    }

    @Test fun skippedCombatDoesNotStrandTheLesson() {
        val r = run("first-turn", "combat-skip")
        assertTrue(r.signal("event", "TurnAdvanced"))
        assertEquals("opponent", r.step?.id)
    }

    @Test fun explanationsAreNeverSkippedByEarlyMilestones() {
        val r = run("first-turn", "board-land")
        assertFalse(r.signal("event", "SpellCast"))
        assertEquals("board-land", r.step?.id)
        val combat = run("combat", "block")
        assertFalse(combat.signal("event", "CombatDamageDealt"))
        assertEquals("block", combat.step?.id)
    }

    @Test fun aPriorityPassNeverCompletesALaterPassPrompt() {
        val r = run("first-turn", "resolve")
        assertFalse(r.signal("event", "PriorityPassed"))
        assertEquals("resolve", r.step?.id)
        assertTrue(r.signal("event", "SpellResolved"))
        assertEquals("inspect", r.step?.id)
        // Holding to read the card and reading the next tip still has to happen.
        assertTrue(r.signal("hold", "battlefield-card:Elvish Mystic"))
        r.next()
        assertEquals("pass", r.step?.id)
    }

    @Test fun milestonesSeenDuringAnExplanationCompleteTheirStepOnArrival() {
        val r = run("first-turn", "inspect")
        r.signal("event", "TurnAdvanced") // the turn ended while the learner was reading
        assertEquals("inspect", r.step?.id)
        r.signal("hold", "battlefield-card:Elvish Mystic")
        r.next() // sickness -> pass
        assertEquals("pass", r.step?.id)
    }

    @Test fun guidedReadingOnlyMovesOnTheMatchingGestureOrNext() {
        val l = lessons.first { it.id == "first-turn" }
        val r = TutorialRun(l, practice = false, startIndex = l.steps.indexOfFirst { it.id == "make-mana" })
        assertFalse(r.signal("event", "SpellCast"))
        r.next()
        assertEquals("pool", r.step?.id)
    }

    @Test fun finishingTheLastStepEndsTheRun() {
        val l = lessons.first { it.id == "stack" }
        val r = TutorialRun(l, practice = true, startIndex = l.steps.lastIndex)
        assertTrue(r.signal("tap", "stack-close"))
        assertTrue(r.finished)
        assertEquals(null, r.step)
    }
}
