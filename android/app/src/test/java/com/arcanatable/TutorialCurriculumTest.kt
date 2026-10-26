package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class TutorialCurriculumTest {
    private fun catalog(): kotlinx.serialization.json.JsonObject {
        val path = listOf(
            File("src/main/assets/tutorials/catalog.json"),
            File("app/src/main/assets/tutorials/catalog.json"),
            File("android/app/src/main/assets/tutorials/catalog.json"),
        ).firstOrNull { it.exists() } ?: error("Bundled tutorial catalog missing")
        return Json.parseToJsonElement(path.readText()).jsonObject
    }

    @Test fun allTwentyChaptersAnd160StepsAreBundled() {
        val chapters = catalog().getValue("chapters").jsonArray
        assertEquals(20, chapters.size)
        val steps = chapters.flatMap { it.jsonObject.getValue("steps").jsonArray }
        assertEquals(160, steps.size)
        assertEquals(20, chapters.map { it.jsonObject.getValue("id").jsonPrimitive.content }.distinct().size)
        chapters.forEach { chapter ->
            val list = chapter.jsonObject.getValue("steps").jsonArray
            val ids = list.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            assertTrue("Each chapter has steps", ids.isNotEmpty())
            assertEquals("Steps have unique ids", ids.size, ids.distinct().size)
        }
    }

    @Test fun firstTurnHasEngineConfirmedActionGates() {
        val chapters = catalog().getValue("chapters").jsonArray
        val first = chapters.first { it.jsonObject.getValue("id").jsonPrimitive.content == "first-turn" }.jsonObject
        val steps = first.getValue("steps").jsonArray
        val events = steps.mapNotNull { step ->
            step.jsonObject.getValue("advance").jsonObject["event"]?.jsonPrimitive?.content
        }
        for (required in listOf("LandPlayed", "ManaAdded", "SpellCast", "SpellResolved",
            "PriorityPassed", "AttackersDeclared", "TurnAdvanced")) {
            assertTrue("Missing event: $required", required in events)
        }
    }

    @Test fun stepGateNeverConfusesTapWithAnArgentumEvent() {
        val step = ArcanaLessonStep("land", "board", "hand-card:Forest", "LandPlayed",
            "Play a land", "Choose Play Land", "Play it", "event")
        assertFalse(TutorialStepGate.accepts(step, "tap", "hand-card:Forest"))
        assertFalse(TutorialStepGate.accepts(step, "event", "SpellCast"))
        assertTrue(TutorialStepGate.accepts(step, "event", "LandPlayed"))
    }

    @Test fun arbitraryCardHoldMustMatchCorrectGesture() {
        val step = ArcanaLessonStep("inspect", "board", "battlefield-card:any", null,
            "Inspect", "Hold a card", "Hold", "hold")
        assertFalse(TutorialStepGate.accepts(step, "tap", "battlefield-card:Forest"))
        assertTrue(TutorialStepGate.accepts(step, "hold", "battlefield-card:Grizzly Bears"))
        assertFalse(TutorialStepGate.accepts(step, "hold", "hand-card:Grizzly Bears"))
    }

    @Test fun combatAndResolutionConfirmedDuringExplanationsCompleteLaterGates() {
        val milestones = TutorialMilestones()
        for (event in listOf("SpellResolved", "AttackersDeclared", "CombatDamageDealt", "TurnAdvanced")) {
            val step = ArcanaLessonStep(event, "board", null, event, "Wait", "", "", "event")
            assertFalse(milestones.completes(step))
            milestones.observe(event)
            assertTrue("An already confirmed $event must not strand the learner", milestones.completes(step))
            assertFalse("An explanation still needs Next", milestones.completes(step.copy(actionKind = "next")))
        }
        milestones.clear()
        assertFalse(milestones.completes(ArcanaLessonStep("damage", "board", null, "CombatDamageDealt", "", "", "", "event")))
    }

    @Test fun earlierPriorityPassCannotCompleteALaterPassPrompt() {
        val milestones = TutorialMilestones()
        milestones.observe("PriorityPassed")
        assertFalse(milestones.completes(ArcanaLessonStep("pass", "board", null, "PriorityPassed", "", "", "", "event")))
    }
}
