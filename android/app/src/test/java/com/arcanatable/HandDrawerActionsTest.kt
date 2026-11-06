package com.arcanatable

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HandDrawerActionsTest {
    private fun action(type: String, card: String = "hand") = JSONObject()
        .put("action", JSONObject().put("type", type).put("cardId", card))

    @Test fun busyDecisionAndNetworkWaitExposeNoStaleActions() {
        assertTrue(handDrawerActions(listOf(action("CastSpell")), busy = true).isEmpty())
    }
    @Test fun actionsExcludePlaceholdersUnaffordableMalformedAndUnsupportedChoices() {
        val valid = action("PassPriority")
        val mana = action("ActivateAbility").put("isManaAbility", true)
        val unaffordable = action("CastSpell").put("isAffordable", false)
        val noTargets = action("CastSpell").put("requiresTargets", true).put("validTargets", org.json.JSONArray())
        assertEquals(listOf(valid), handDrawerActions(listOf(valid, mana, unaffordable, noTargets,
            JSONObject().put("__none", true), JSONObject()), false))
    }
    @Test fun castPickerIncludesOnlyHandSpellsAndLandsNotOtherZonesOrAbilities() {
        val spell = action("CastSpell")
        val land = action("PlayLand", "land")
        val result = handDrawerCastActions(handDrawerActions(listOf(spell, land,
            action("CastSpell", "grave"), action("ActivateAbility"), action("PassPriority")), false), setOf("hand", "land"))
        assertEquals(listOf(spell, land), result)
        assertTrue(handDrawerCastActions(result, emptySet()).isEmpty())
    }
    @Test fun referencePhoneHasFourColumnsAndTwoRowsForSevenCards() {
        val grid = HandLayout.drawer(7, 380f, 280f, HandArrangement.AUTO_FIT)
        assertEquals(4, grid.columns); assertEquals(2, grid.rows)
        assertEquals(92f, grid.cardWidth, 1f); assertEquals(132f, grid.cardHeight, 1f)
    }
    @Test fun emptyHugeFoldAndScaledHandsStayInsideWidthAndScrollWhenNeeded() {
        for (width in listOf(280f, 328f, 380f, 641f, 850f)) for (count in listOf(0, 1, 7, 20, 80)) {
            val grid = HandLayout.drawer(count, width, 150f, HandArrangement.AUTO_FIT)
            assertTrue(grid.cardWidth > 0)
            assertTrue(grid.columns * grid.cardWidth + (grid.columns - 1) * 4f <= width + .1f)
            assertTrue(grid.columns * grid.rows >= count)
            if (count == 80) assertTrue(grid.scrolls)
        }
        assertTrue(HandLayout.drawer(7, 380f, 280f, HandArrangement.AUTO_FIT, 1.5f).cardWidth >
            HandLayout.drawer(7, 380f, 280f, HandArrangement.AUTO_FIT, .7f).cardWidth)
    }
    @Test fun explicitSingleRowPreferenceIsPreserved() {
        assertEquals(HandLayout.compute(7, 380f, 280f, HandArrangement.SINGLE_ROW),
            HandLayout.drawer(7, 380f, 280f, HandArrangement.SINGLE_ROW))
    }
}
