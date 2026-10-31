package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AncientStoneIdol
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import org.junit.Assert.*
import org.junit.Test

class AncientStoneIdolIndependentCertificationTest {
    @Test
    fun tenManaCastsTheTwelveTwelveIdol() {
        val d = customScenario(AncientStoneIdol)
        val player = d.player1
        val idol = d.putCardInHand(player, "Ancient Stone Idol")
        d.giveColorlessMana(player, 10)

        assertNull(d.castSpell(player, idol).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(idol))
        assertEquals(12, d.projected.getPower(idol))
        assertEquals(12, d.projected.getToughness(idol))
    }

    @Test
    fun nineManaIsNotEnoughWhenNoCreaturesAreAttacking() {
        val d = customScenario(AncientStoneIdol)
        val player = d.player1
        val idol = d.putCardInHand(player, "Ancient Stone Idol")
        d.giveColorlessMana(player, 9)

        assertNotNull(d.castSpell(player, idol).error)
        assertTrue(d.getHand(player).contains(idol))
        assertFalse(d.getPermanents(player).contains(idol))
    }

    @Test
    fun onlyAttackingCreaturesReduceTheGenericCastingCost() {
        val d = customScenario(AncientStoneIdol, "Grizzly Bears", "Giant Spider")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(player, "Giant Spider")
        val idol = d.putCardInHand(player, "Ancient Stone Idol")
        d.giveColorlessMana(player, 9)

        assertNotNull(d.castSpell(player, idol).error)
        assertTrue(d.getHand(player).contains(idol))
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, idol).error)
    }

    @Test
    fun eachAttackingCreatureReducesTheCostByOneDuringCombat() {
        val d = customScenario(AncientStoneIdol, "Grizzly Bears", "Giant Spider")
        val player = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spider = d.putCreatureOnBattlefield(opponent, "Giant Spider")
        d.removeSummoningSickness(bear)
        d.removeSummoningSickness(spider)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.declareAttackers(opponent, listOf(bear, spider), player).error)
        assertNull(d.passPriority(opponent).error)

        val idol = d.putCardInHand(player, "Ancient Stone Idol")
        d.giveColorlessMana(player, 7)
        assertNotNull(d.castSpell(player, idol).error)
        assertTrue(d.getHand(player).contains(idol))
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, idol).error)
    }

    @Test
    fun flashAllowsCastingTheIdolDuringAnOpponentsTurn() {
        val d = customScenario(AncientStoneIdol)
        val player = d.player1
        val idol = d.putCardInHand(player, "Ancient Stone Idol")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        d.giveColorlessMana(player, 10)

        assertNull(d.castSpell(player, idol).error)
        d.resolveAll()
        assertTrue(d.getPermanents(player).contains(idol))
    }

    @Test
    fun trampleDealsExcessCombatDamageToTheDefendingPlayer() {
        val d = customScenario(AncientStoneIdol, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val idol = d.putCreatureOnBattlefield(player, "Ancient Stone Idol")
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(idol)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(idol), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(idol))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(idol, Keyword.TRAMPLE))
        assertEquals(10, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.getPermanents(player).contains(idol))
    }

    @Test
    fun whenTheIdolDiesItCreatesTheExactTramplingColorlessArtifactCreature() {
        val d = customScenario(AncientStoneIdol, "Murder")
        val player = d.player1
        val idol = d.putCreatureOnBattlefield(player, "Ancient Stone Idol")
        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(idol))).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Ancient Stone Idol"))
        val constructs = d.permanentsNamed(player, "Construct")
        assertEquals(1, constructs.size)
        val construct = constructs.single()
        assertEquals(6, d.projected.getPower(construct))
        assertEquals(12, d.projected.getToughness(construct))
        assertTrue(d.projected.hasType(construct, "ARTIFACT"))
        assertTrue(d.projected.hasType(construct, "CREATURE"))
        assertTrue(d.projected.hasKeyword(construct, Keyword.TRAMPLE))
        listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN).forEach {
            assertFalse(d.projected.hasColor(construct, it))
        }
    }

    @Test
    fun anotherCreatureDyingDoesNotCreateAnIdolConstruct() {
        val d = customScenario(AncientStoneIdol, "Murder", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val idol = d.putCreatureOnBattlefield(player, "Ancient Stone Idol")
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(bear))).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(idol))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.permanentsNamed(player, "Construct").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Construct").isEmpty())
    }
}
