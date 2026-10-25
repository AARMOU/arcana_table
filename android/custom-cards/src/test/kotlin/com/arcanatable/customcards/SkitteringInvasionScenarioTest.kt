package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SkitteringInvasion
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import org.junit.Assert.*
import org.junit.Test

class SkitteringInvasionScenarioTest {
    @Test
    fun createsFiveManaCapableEldraziSpawn() {
        val d = customScenario(SkitteringInvasion)
        val p = d.player1
        val spell = d.putCardInHand(p, "Skittering Invasion")
        d.giveColorlessMana(p, 7)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        val spawn = d.permanentsNamed(p, "Eldrazi Spawn")
        assertEquals(5, spawn.size)
        assertTrue(spawn.all { d.abilityActions(p, it).isNotEmpty() })
    }

    @Test
    fun spawnSacrificeImmediatelyProducesSpendableColorlessAndCannotBeRepeatedOrUsedByOpponent() {
        val d = customScenario(SkitteringInvasion, "Sol Ring")
        val p = d.player1
        val spell = d.putCardInHand(p, "Skittering Invasion")
        d.giveColorlessMana(p, 7)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        val token = d.permanentsNamed(p, "Eldrazi Spawn").first()
        assertEquals(0, d.projected.getPower(token))
        assertEquals(1, d.projected.getToughness(token))
        assertTrue(d.projected.getColors(token).isEmpty())
        val action = d.abilityActions(p, token).first().action as ActivateAbility

        assertNotNull("Opponent cannot sacrifice my Spawn", d.submit(action.copy(playerId = d.player2)).error)
        assertEquals(5, d.permanentsNamed(p, "Eldrazi Spawn").size)
        assertEquals(0, d.manaPool(p).colorless)
        assertNull("No tap cost: summoning sickness cannot prevent this mana ability", d.submit(action).error)
        assertEquals("Sacrifice is paid immediately", 4, d.permanentsNamed(p, "Eldrazi Spawn").size)
        assertEquals("Mana ability does not use the stack", 0, d.stackSize)
        assertEquals(1, d.manaPool(p).colorless)
        assertNotNull("A sacrificed token cannot be used twice", d.submit(action).error)
        assertEquals(1, d.manaPool(p).colorless)

        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull("The produced mana must actually pay a cost", d.castSpell(p, ring).error)
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
    }
}
