package com.arcanatable

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Build 102 quick fixes: readable counter names, triggers in the game log. */
class QuickFixesTest {
    @Test fun counterNamesReadLikePlayersSayThem() {
        assertEquals("+1/+1", counterLabel("PLUS_ONE_PLUS_ONE"))
        assertEquals("-1/-1", counterLabel("MINUS_ONE_MINUS_ONE"))
        assertEquals("Loyalty", counterLabel("LOYALTY"))
        assertEquals("Lore", counterLabel("LORE"))
        assertEquals("Ice", counterLabel("ICE"))
        assertEquals("Stun", counterLabel("Stun"))
    }

    @Test fun triggeredAbilitiesReachTheGameLog() {
        val trigger = JSONObject().put("type", "abilityTriggered").put("sourceName", "Impact Tremors").put("abilityDescription", "Deal 1 damage to each opponent").put("description", "")
        assertEquals("Trigger · Impact Tremors: Deal 1 damage to each opponent", PlayFeedParser.logLine(trigger))
        val described = JSONObject().put("type", "abilityTriggered").put("sourceName", "X").put("abilityDescription", "Draw a card").put("description", "X: Draw a card")
        assertEquals("X: Draw a card", PlayFeedParser.logLine(described))
        assertEquals("You cast Shock", PlayFeedParser.logLine(JSONObject().put("type", "spellCast").put("description", "You cast Shock")))
    }
}
