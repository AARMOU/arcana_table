package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AspiringChampion
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AspiringChampionIndependentCertificationTest {
    private val name = "Aspiring Champion"

    @Test
    fun castingCostsThreeGenericAndRedAndEntersAsARedThreeThreeAstartesWarrior() {
        val d = customScenario(AspiringChampion)
        val p = d.player1
        val champion = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.RED)

        assertNull(d.castSpell(p, champion).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(champion))
        assertEquals(3, d.projected.getPower(champion))
        assertEquals(3, d.projected.getToughness(champion))
        assertTrue(d.projected.hasColor(champion, Color.RED))
        assertTrue(d.projected.hasSubtype(champion, "ASTARTES"))
        assertTrue(d.projected.hasSubtype(champion, "WARRIOR"))
    }

    @Test
    fun fourGenericManaCannotReplaceTheRequiredRedMana() {
        val d = customScenario(AspiringChampion)
        val p = d.player1
        val champion = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNotNull(d.castSpell(p, champion).error)

        assertTrue(d.getHand(p).contains(champion))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
    }

    @Test
    fun menaceCannotBeBlockedByOneCreatureButCanBeBlockedByTwo() {
        val d = customScenario(AspiringChampion, "Ornithopter")
        val champion = d.putCreatureOnBattlefield(d.player1, name)
        val firstBlocker = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val secondBlocker = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        d.removeSummoningSickness(champion)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(firstBlocker to listOf(champion))).error)
        assertNull(
            d.declareBlockers(
                d.player2,
                mapOf(firstBlocker to listOf(champion), secondBlocker to listOf(champion)),
            ).error,
        )
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(d.player2))
        assertTrue(d.permanentsNamed(d.player1, name).contains(champion))
    }

    @Test
    fun combatDamageToAPlayerSacrificesChampionAndFindsTheFirstCreature() {
        val d = customScenario(AspiringChampion, "Forest", "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val champion = d.putCreatureOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.removeSummoningSickness(champion)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, emptyMap()).error)
        assertTrue(d.permanentsNamed(p, name).contains(champion))
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(17, d.getLifeTotal(d.player2))
        assertTrue(d.getGraveyardCardNames(p).contains(name))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Sol Ring"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
    }

    @Test
    fun aDemonRevealedByRuinousAscensionDealsDamageEqualToItsPowerToTheOpponent() {
        val d = customScenario(AspiringChampion, "Demon of Catastrophes")
        val p = d.player1
        val champion = d.putCreatureOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Demon of Catastrophes")
        d.removeSummoningSickness(champion)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        val demon = d.permanentsNamed(p, "Demon of Catastrophes").single()
        assertEquals(6, d.projected.getPower(demon))
        assertEquals(11, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.getGraveyardCardNames(p).contains(name))
    }

    @Test
    fun aNonDemonCreatureDoesNotDealTheAdditionalDamage() {
        val d = customScenario(AspiringChampion, "Grizzly Bears")
        val p = d.player1
        val champion = d.putCreatureOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.removeSummoningSickness(champion)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(17, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
        assertTrue(d.getGraveyardCardNames(p).contains(name))
    }

    @Test
    fun ifTheLibraryHasNoCreatureChampionIsStillSacrificedAndRevealedCardsReturn() {
        val d = customScenario(AspiringChampion, "Forest", "Sol Ring")
        val p = d.player1
        val champion = d.putCreatureOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.removeSummoningSickness(champion)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(17, d.getLifeTotal(d.player2))
        assertTrue(d.getGraveyardCardNames(p).contains(name))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Sol Ring"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
    }
}
