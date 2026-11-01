package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DenyTheWitch
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DenyTheWitchIndependentCertificationTest {
    private fun giveDenyTheWitchMana(d: GameTestDriver, player: com.wingedsheep.sdk.model.EntityId) {
        d.giveColorlessMana(player, 1)
        d.giveMana(player, Color.WHITE)
        d.giveMana(player, Color.BLUE)
        d.giveMana(player, Color.BLACK)
    }

    @Test
    fun countersASpellOnAnOpponentsTurnAndItsControllerLosesLifeForYourCreatures() {
        val d = customScenario(DenyTheWitch, "Shock", "Grizzly Bears", "Giant Spider")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Giant Spider")
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        val shock = d.putCardInHand(opponent, "Shock")
        d.giveMana(opponent, Color.RED)
        assertNull(d.castSpell(opponent, shock, listOf(p)).error)
        val spell = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val deny = d.putCardInHand(p, "Deny the Witch")
        giveDenyTheWitchMana(d, p)
        assertNull(d.castSpellWithTargets(p, deny, listOf(ChosenTarget.Spell(spell))).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(opponent).contains(shock))
        assertEquals(0, d.stackSize)
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(opponent))
    }

    @Test
    fun countersAnActivatedAbilityAndItsControllerLosesLifeForYourCreatures() {
        val d = customScenario(DenyTheWitch, "Sakura-Tribe Elder", "Grizzly Bears", "Giant Spider")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Giant Spider")
        val elder = d.putCreatureOnBattlefield(opponent, "Sakura-Tribe Elder")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        assertNull(d.activate(opponent, elder).error)
        assertTrue(d.getGraveyard(opponent).contains(elder))
        val ability = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val deny = d.putCardInHand(p, "Deny the Witch")
        giveDenyTheWitchMana(d, p)
        assertNull(d.castSpellWithTargets(p, deny, listOf(ChosenTarget.Spell(ability))).error)
        d.resolveAll()

        assertEquals(0, d.stackSize)
        assertTrue(d.permanentsNamed(opponent, "Plains").isEmpty())
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(opponent))
    }

    @Test
    fun countersATriggeredAbilityAndItsControllerLosesLifeForYourCreatures() {
        val d = customScenario(DenyTheWitch, "Elvish Visionary", "Grizzly Bears", "Giant Spider")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Giant Spider")
        val cardToDraw = d.putCardOnTopOfLibrary(opponent, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        val visionary = d.putCardInHand(opponent, "Elvish Visionary")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, visionary).error)
        d.bothPass()
        assertEquals(1, d.stackSize)
        val trigger = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val deny = d.putCardInHand(p, "Deny the Witch")
        giveDenyTheWitchMana(d, p)
        assertNull(d.castSpellWithTargets(p, deny, listOf(ChosenTarget.Spell(trigger))).error)
        d.resolveAll()

        assertEquals(0, d.stackSize)
        assertTrue(d.getPermanents(opponent).any { d.getCardName(it) == "Elvish Visionary" })
        assertFalse(d.getHand(opponent).contains(cardToDraw))
        assertTrue(d.zoneNames(opponent, Zone.LIBRARY).contains("Grizzly Bears"))
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(opponent))
    }

    @Test
    fun cannotCastWithoutEachColoredManaSymbol() {
        for (missing in listOf(Color.WHITE, Color.BLUE, Color.BLACK)) {
            val d = customScenario(DenyTheWitch, "Shock")
            val p = d.player1
            val opponent = d.player2
            d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
            val shock = d.putCardInHand(opponent, "Shock")
            d.giveMana(opponent, Color.RED)
            assertNull(d.castSpell(opponent, shock, listOf(p)).error)
            val spell = d.getTopOfStack()!!
            assertNull(d.passPriority(opponent).error)

            val deny = d.putCardInHand(p, "Deny the Witch")
            d.giveColorlessMana(p, 1)
            listOf(Color.WHITE, Color.BLUE, Color.BLACK)
                .filterNot { it == missing }
                .forEach { d.giveMana(p, it) }
            d.giveMana(p, Color.GREEN)

            assertNotNull(d.castSpellWithTargets(p, deny, listOf(ChosenTarget.Spell(spell))).error)
            assertTrue(d.getHand(p).contains(deny))
            assertEquals(1, d.stackSize)
            assertEquals(20, d.getLifeTotal(opponent))
        }
    }

    @Test
    fun cannotCastWithoutTheGenericMana() {
        val d = customScenario(DenyTheWitch, "Shock")
        val p = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val shock = d.putCardInHand(opponent, "Shock")
        d.giveMana(opponent, Color.RED)
        assertNull(d.castSpell(opponent, shock, listOf(p)).error)
        val spell = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val deny = d.putCardInHand(p, "Deny the Witch")
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        assertNotNull(d.castSpellWithTargets(p, deny, listOf(ChosenTarget.Spell(spell))).error)

        assertTrue(d.getHand(p).contains(deny))
        assertEquals(1, d.stackSize)
        assertEquals(20, d.getLifeTotal(opponent))
    }

    @Test
    fun cannotTargetAPermanent() {
        val d = customScenario(DenyTheWitch, "Grizzly Bears")
        val p = d.player1
        val permanent = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val deny = d.putCardInHand(p, "Deny the Witch")
        giveDenyTheWitchMana(d, p)

        assertNotNull(d.castSpellWithTargets(p, deny, listOf(ChosenTarget.Permanent(permanent))).error)

        assertTrue(d.getHand(p).contains(deny))
        assertTrue(d.getPermanents(d.player2).contains(permanent))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun cannotCastWithoutPriorityEvenWhenAnOpponentSpellIsOnTheStack() {
        val d = customScenario(DenyTheWitch, "Shock")
        val p = d.player1
        val opponent = d.player2
        val shock = d.putCardInHand(opponent, "Shock")
        d.giveMana(opponent, Color.RED)
        assertNull(d.castSpell(opponent, shock, listOf(p)).error)
        val spell = d.getTopOfStack()!!

        val deny = d.putCardInHand(p, "Deny the Witch")
        giveDenyTheWitchMana(d, p)
        assertNotNull(d.castSpellWithTargets(p, deny, listOf(ChosenTarget.Spell(spell))).error)

        assertTrue(d.getHand(p).contains(deny))
        assertEquals(1, d.stackSize)
        assertEquals(20, d.getLifeTotal(opponent))
    }
}
