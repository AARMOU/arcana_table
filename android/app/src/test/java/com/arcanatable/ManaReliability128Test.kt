package com.arcanatable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 128 regression contract: manual mana activations remain possible, and
 * Food/Goose is not confused with self-sacrificing Treasure mana.
 */
class ManaReliability128Test {
    private fun ability(description: String, sacrificeChoices: List<String> = emptyList()): JSONObject {
        val cost = JSONObject().put("description", "Sacrifice Food")
            .put("sacrificeCount", if (sacrificeChoices.isEmpty()) 0 else 1)
            .put("validSacrificeTargets", JSONArray(sacrificeChoices))
        return JSONObject()
            .put("isManaAbility", true)
            .put("isAffordable", true)
            .put("description", description)
            .put("requiresManaColorChoice", true)
            .put("availableManaColors", JSONArray(listOf("RED", "GREEN")))
            .put("additionalCostInfo", cost)
            .put("action", JSONObject().put("type", "ActivateAbility")
                .put("sourceId", "mana-source").put("playerId", "player-1"))
    }

    @Test fun treasureManaActionRemainsExposedInsteadOfBeingHiddenWithLandMana() {
        val treasure = ability("{T}, Sacrifice this artifact: Add one mana of any color.")
        val land = JSONObject().put("isManaAbility", true).put("isAffordable", true)
            .put("description", "{T}: Add {G}.")
        assertEquals(listOf(treasure), ArgentumActions.visibleChoices(listOf(treasure, land)))
        assertTrue(ArgentumActions.isSacrificeMana(treasure))
        assertTrue(ArgentumActions.menuLabel(treasure).contains("this source"))
    }

    @Test fun gildedGooseRequiresTheFoodChoiceAndColorBeforeSubmission() {
        val goose = ability("{T}, Sacrifice a Food: Add one mana of any color.", listOf("food-1", "food-2"))
        assertTrue(ArgentumActions.isSacrificeMana(goose))
        assertTrue(ArgentumActions.menuLabel(goose).contains("Food"))
        val plan = ArgentumActionPlan.from(goose)
        assertTrue(plan.needsInput)
        assertEquals(listOf("food-1", "food-2"), plan.slots.single().valid)
        assertEquals("sacrificedPermanents", plan.slots.single().costField)
        assertTrue(plan.problem(emptyMap(), emptyMap(), "RED") != null)
        val chosen = mapOf(plan.slots.single().index to listOf("food-2"))
        assertNull(plan.problem(chosen, emptyMap(), "RED"))
        val action = plan.build(chosen, null, emptyMap(), "RED") { null }
        assertEquals("RED", action.getString("manaColorChoice"))
        assertEquals("food-2", action.getJSONObject("costPayment")
            .getJSONArray("sacrificedPermanents").getString(0))
    }

    @Test fun nonManaSacrificeAbilityIsNotRelabeledOrClassifiedAsMana() {
        val sacrificeCreature = JSONObject().put("isManaAbility", false)
            .put("description", "Sacrifice a creature: Draw two cards.")
        assertFalse(ArgentumActions.isSacrificeMana(sacrificeCreature))
        assertEquals("Sacrifice a creature: Draw two cards.", ArgentumActions.menuLabel(sacrificeCreature))
    }
}
