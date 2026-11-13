package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ScreamerKiller
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ScreamerKillerIndependentCertificationTest {
    private fun hasScreamTriggerOnStack(d: GameTestDriver, screamer: com.wingedsheep.sdk.model.EntityId): Boolean =
        d.state.stack.any { id ->
            d.state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()?.sourceId == screamer
        }

    @Test
    fun castingCostsFourGenericAndOneRedAndEntersAsAFiveFiveWithTrample() {
        val d = customScenario(ScreamerKiller)
        val player = d.player1
        val card = d.putCardInHand(player, "Screamer-Killer")
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.RED)

        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Screamer-Killer").contains(card))
        assertEquals(5, d.projected.getPower(card))
        assertEquals(5, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.TRAMPLE))
    }

    @Test
    fun fourColorlessManaCannotPayItsRedManaSymbol() {
        val d = customScenario(ScreamerKiller)
        val player = d.player1
        val card = d.putCardInHand(player, "Screamer-Killer")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, card).error)

        assertTrue(d.getHand(player).contains(card))
        assertTrue(d.permanentsNamed(player, "Screamer-Killer").isEmpty())
    }

    @Test
    fun threeGenericAndOneRedManaCannotPayItsFourGenericManaCost() {
        val d = customScenario(ScreamerKiller)
        val player = d.player1
        val card = d.putCardInHand(player, "Screamer-Killer")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.RED)

        assertNotNull(d.castSpell(player, card).error)

        assertTrue(d.getHand(player).contains(card))
        assertTrue(d.permanentsNamed(player, "Screamer-Killer").isEmpty())
    }

    @Test
    fun trampleDealsExcessCombatDamagePastABlocker() {
        val d = customScenario(ScreamerKiller, "Llanowar Elves")
        val screamer = d.putCreatureOnBattlefield(d.player1, "Screamer-Killer")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        d.removeSummoningSickness(screamer)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(screamer), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(screamer))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(16, d.getLifeTotal(d.player2))
        assertFalse(d.getPermanents(d.player2).contains(blocker))
        assertTrue(d.getPermanents(d.player1).contains(screamer))
    }

    @Test
    fun castingACreatureWithManaValueFiveTriggersFiveDamageToAPlayer() {
        val d = customScenario(ScreamerKiller, "Air Elemental")
        val player = d.player1
        val opponent = d.player2
        val screamer = d.putCreatureOnBattlefield(player, "Screamer-Killer")
        val spell = d.putCardInHand(player, "Air Elemental")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLUE, 2)

        assertNull(d.castSpell(player, spell).error)
        d.decide(Choices(targets = listOf(opponent)))
        assertTrue("the ability triggers as the creature spell is cast", hasScreamTriggerOnStack(d, screamer))
        assertTrue(d.state.stack.any { d.getCardName(it) == "Air Elemental" })

        d.resolveAll()

        assertEquals(15, d.getLifeTotal(opponent))
        assertTrue(d.permanentsNamed(player, "Air Elemental").contains(spell))
    }

    @Test
    fun bioPlasmicScreamCanTargetACreature() {
        val d = customScenario(ScreamerKiller, "Phyrexian Hulk", "Llanowar Elves")
        val player = d.player1
        val screamer = d.putCreatureOnBattlefield(player, "Screamer-Killer")
        val target = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        val spell = d.putCardInHand(player, "Phyrexian Hulk")
        d.giveColorlessMana(player, 6)

        assertNull(d.castSpell(player, spell).error)
        d.decide(Choices(targets = listOf(target)))
        assertTrue(hasScreamTriggerOnStack(d, screamer))
        d.resolveAll()

        assertTrue(d.getGraveyardCardNames(d.player2).contains("Llanowar Elves"))
        assertTrue(d.permanentsNamed(player, "Phyrexian Hulk").contains(spell))
    }

    @Test
    fun aFourManaCreatureSpellDoesNotTriggerBioPlasmicScream() {
        val d = customScenario(ScreamerKiller, "Juggernaut")
        val player = d.player1
        val opponent = d.player2
        val screamer = d.putCreatureOnBattlefield(player, "Screamer-Killer")
        val spell = d.putCardInHand(player, "Juggernaut")
        d.giveColorlessMana(player, 4)

        assertNull(d.castSpell(player, spell).error)
        assertFalse(hasScreamTriggerOnStack(d, screamer))
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.permanentsNamed(player, "Juggernaut").contains(spell))
    }

    @Test
    fun aSixManaNoncreatureSpellDoesNotTriggerBioPlasmicScream() {
        val d = customScenario(ScreamerKiller, "The Immortal Sun")
        val player = d.player1
        val opponent = d.player2
        val screamer = d.putCreatureOnBattlefield(player, "Screamer-Killer")
        val spell = d.putCardInHand(player, "The Immortal Sun")
        d.giveColorlessMana(player, 6)

        assertNull(d.castSpell(player, spell).error)
        assertFalse(hasScreamTriggerOnStack(d, screamer))
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.permanentsNamed(player, "The Immortal Sun").contains(spell))
    }
}
