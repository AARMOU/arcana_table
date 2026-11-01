package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CanoptekTombSentinel
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CanoptekTombSentinelIndependentCertificationTest {
    private val name = "Canoptek Tomb Sentinel"

    @Test
    fun castingRequiresFourGenericManaAndEntersAsAFourThreeArtifactInsectWithVigilance() {
        val d = customScenario(CanoptekTombSentinel)
        val p = d.player1
        val sentinel = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, sentinel).error)
        assertTrue(d.getHand(p).contains(sentinel))
        assertTrue(d.permanentsNamed(p, name).isEmpty())

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, sentinel).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(sentinel))
        assertEquals(4, d.projected.getPower(sentinel))
        assertEquals(3, d.projected.getToughness(sentinel))
        assertTrue(d.projected.hasType(sentinel, "ARTIFACT"))
        assertTrue(d.projected.hasType(sentinel, "CREATURE"))
        assertTrue(d.projected.hasSubtype(sentinel, "INSECT"))
        assertTrue(d.projected.hasKeyword(sentinel, Keyword.VIGILANCE))
    }

    @Test
    fun vigilanceKeepsTheSentinelUntappedAfterAttacking() {
        val d = customScenario(CanoptekTombSentinel)
        val p = d.player1
        val sentinel = d.putCreatureOnBattlefield(p, name)
        d.removeSummoningSickness(sentinel)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(sentinel), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(16, d.getLifeTotal(d.player2))
        assertFalse(d.isTapped(sentinel))
    }

    @Test
    fun castingFromHandDoesNotTriggerExileCannon() {
        val d = customScenario(CanoptekTombSentinel, "Grizzly Bears")
        val p = d.player1
        val sentinel = d.putCardInHand(p, name)
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(p, 4)

        assertNull(d.castSpell(p, sentinel).error)
        d.resolveAll(Choices(targets = listOf(bear)))

        assertTrue(d.permanentsNamed(p, name).contains(sentinel))
        assertTrue(d.getPermanents(d.player2).contains(bear))
        assertFalse(d.zoneNames(d.player2, Zone.EXILE).contains("Grizzly Bears"))
    }

    @Test
    fun unearthCostsSevenReturnsFromTheGraveyardWithHasteAndExileCannon() {
        val d = customScenario(CanoptekTombSentinel, "Grizzly Bears")
        val p = d.player1
        val sentinel = d.putCardInGraveyard(p, name)
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(p, 6)

        assertNotNull(d.activate(p, sentinel).error)
        assertTrue(d.getGraveyard(p).contains(sentinel))
        assertEquals(6, d.manaPool(p).colorless)

        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, sentinel).error)
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll(Choices(targets = listOf(target)))

        assertTrue(d.permanentsNamed(p, name).contains(sentinel))
        assertFalse(d.getGraveyard(p).contains(sentinel))
        assertTrue(d.projected.hasKeyword(sentinel, Keyword.HASTE))
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Grizzly Bears"))
        assertFalse(d.getPermanents(d.player2).contains(target))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(sentinel), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(16, d.getLifeTotal(d.player2))
        assertFalse(d.isTapped(sentinel))
    }

    @Test
    fun unearthCannotBeActivatedFromHand() {
        val d = customScenario(CanoptekTombSentinel)
        val p = d.player1
        val sentinel = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 7)

        assertNotNull(d.activate(p, sentinel).error)
        assertTrue(d.getHand(p).contains(sentinel))
        assertEquals(7, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, name).isEmpty())
    }

    @Test
    fun unearthCanOnlyBeActivatedAtSorceryTiming() {
        val d = customScenario(CanoptekTombSentinel)
        val p = d.player1
        val sentinel = d.putCardInGraveyard(p, name)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.giveColorlessMana(p, 7)

        assertNotNull(d.activate(p, sentinel).error)
        assertTrue(d.getGraveyard(p).contains(sentinel))
        assertEquals(7, d.manaPool(p).colorless)
    }

    @Test
    fun unearthCannotChooseALandAsTheExileCannonTarget() {
        val d = customScenario(CanoptekTombSentinel, "Plains")
        val p = d.player1
        val sentinel = d.putCardInGraveyard(p, name)
        val land = d.putPermanentOnBattlefield(d.player2, "Plains")
        d.giveColorlessMana(p, 7)

        assertNull(d.activate(p, sentinel).error)
        d.resolveAll(Choices(targets = listOf(land)))

        assertTrue(d.permanentsNamed(p, name).contains(sentinel))
        assertTrue(d.getPermanents(d.player2).contains(land))
        assertFalse(d.zoneNames(d.player2, Zone.EXILE).contains("Plains"))
    }

    @Test
    fun exileCannonMayChooseNoTargetEvenWhenANonlandPermanentIsAvailable() {
        val d = customScenario(CanoptekTombSentinel, "Grizzly Bears")
        val p = d.player1
        val sentinel = d.putCardInGraveyard(p, name)
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(p, 7)

        assertNull(d.activate(p, sentinel).error)
        d.resolveAll(Choices(targets = emptyList()))

        assertTrue(d.permanentsNamed(p, name).contains(sentinel))
        assertTrue(d.getPermanents(d.player2).contains(bear))
        assertFalse(d.zoneNames(d.player2, Zone.EXILE).contains("Grizzly Bears"))
    }

    @Test
    fun unearthExilesTheSentinelAtTheBeginningOfTheNextEndStep() {
        val d = customScenario(CanoptekTombSentinel)
        val p = d.player1
        val sentinel = d.putCardInGraveyard(p, name)
        d.giveColorlessMana(p, 7)

        assertNull(d.activate(p, sentinel).error)
        d.resolveAll(Choices(targets = emptyList()))
        assertTrue(d.permanentsNamed(p, name).contains(sentinel))
        assertFalse(d.zoneNames(p, Zone.EXILE).contains(name))

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertFalse(d.getGraveyard(p).contains(sentinel))
    }

    @Test
    fun unearthExilesTheSentinelInsteadOfPuttingItInTheGraveyardWhenItWouldLeave() {
        val d = customScenario(CanoptekTombSentinel, "Murder")
        val p = d.player1
        val sentinel = d.putCardInGraveyard(p, name)
        d.giveColorlessMana(p, 7)

        assertNull(d.activate(p, sentinel).error)
        d.resolveAll(Choices(targets = emptyList()))
        assertTrue(d.permanentsNamed(p, name).contains(sentinel))

        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, com.wingedsheep.sdk.core.Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(
            d.castSpellWithTargets(p, murder, listOf(ChosenTarget.Permanent(sentinel))).error,
        )
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
        assertFalse(d.getGraveyard(p).contains(sentinel))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
    }
}
