package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NoiseMarine
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class NoiseMarineIndependentCertificationTest {
    private fun GameTestDriver.payMarine(player: EntityId) {
        giveColorlessMana(player, 4)
        giveMana(player, Color.RED)
    }

    private fun GameTestDriver.marineDamage(marine: EntityId): List<DamageDealtEvent> =
        events.filterIsInstance<DamageDealtEvent>().filter { it.sourceId == marine }

    @Test fun castingForFourAndRedProducesAThreeTwoAstartesWarrior() {
        val d = customScenario(NoiseMarine)
        val p = d.player1
        val marine = d.putCardInHand(p, "Noise Marine")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        assertEquals(2, d.stackSize)
        d.resolveAll(Choices(targets = listOf(d.player2)))

        assertTrue(d.getPermanents(p).contains(marine))
        assertEquals(3, d.projected.getPower(marine))
        assertEquals(2, d.projected.getToughness(marine))
        assertTrue(d.projected.hasColor(marine, Color.RED))
        assertTrue(d.projected.hasSubtype(marine, "ASTARTES"))
        assertTrue(d.projected.hasSubtype(marine, "WARRIOR"))
        assertEquals(listOf(d.player2 to 1), d.marineDamage(marine).map { it.targetId to it.amount })
    }

    @Test fun redAndFourGenericManaAreBothRequired() {
        val noRed = customScenario(NoiseMarine)
        val first = noRed.putCardInHand(noRed.player1, "Noise Marine")
        noRed.giveColorlessMana(noRed.player1, 5)
        assertNotNull(noRed.castSpell(noRed.player1, first).error)
        assertTrue(noRed.getHand(noRed.player1).contains(first))
        assertEquals(0, noRed.stackSize)

        val short = customScenario(NoiseMarine)
        val second = short.putCardInHand(short.player1, "Noise Marine")
        short.giveColorlessMana(short.player1, 3)
        short.giveMana(short.player1, Color.RED)
        assertNotNull(short.castSpell(short.player1, second).error)
        assertTrue(short.getHand(short.player1).contains(second))
        assertEquals(0, short.stackSize)
    }

    @Test fun creatureSpellCannotBeCastOnAnOpponentsTurn() {
        val d = customScenario(NoiseMarine)
        val marine = d.putCardInHand(d.player1, "Noise Marine")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        d.payMarine(d.player1)
        assertNotNull(d.castSpell(d.player1, marine).error)
        assertTrue(d.getHand(d.player1).contains(marine))
        assertEquals(0, d.stackSize)
    }

    @Test fun cascadeFindsFirstLowerNonlandCastsItForFreeAndCountsIt() {
        val d = customScenario(NoiseMarine, "Forest", "Serra Angel", "Grizzly Bears", "Island")
        val p = d.player1
        val marine = d.putCardInHand(p, "Noise Marine")
        val originalLibrarySize = d.zoneNames(p, Zone.LIBRARY).size
        d.putCardOnTopOfLibrary(p, "Island")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Serra Angel")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.payMarine(p)

        assertNull(d.castSpell(p, marine).error)
        assertEquals(2, d.stackSize)
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears"), targets = listOf(d.player2)))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
        assertTrue(d.getPermanents(p).contains(marine))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).red)
        val library = d.zoneNames(p, Zone.LIBRARY)
        assertEquals(originalLibrarySize + 3, library.size)
        assertEquals("Island", library.first())
        assertEquals(setOf("Forest", "Serra Angel"), library.takeLast(2).toSet())
        assertTrue(d.zoneNames(p, Zone.EXILE).isEmpty())
        assertEquals(listOf(d.player2 to 2), d.marineDamage(marine).map { it.targetId to it.amount })
    }

    @Test fun decliningCascadeReturnsTheFoundCardAndDoesNotCountItAsCast() {
        val d = customScenario(NoiseMarine, "Grizzly Bears")
        val p = d.player1
        val marine = d.putCardInHand(p, "Noise Marine")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        d.resolveAll(Choices(yes = false, targets = listOf(d.player2)))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertEquals("Grizzly Bears", d.zoneNames(p, Zone.LIBRARY).last())
        assertTrue(d.zoneNames(p, Zone.EXILE).isEmpty())
        assertEquals(listOf(d.player2 to 1), d.marineDamage(marine).map { it.targetId to it.amount })
    }

    @Test fun cascadeResolvesBeforeTheCreatureEnters() {
        val d = customScenario(NoiseMarine, "Grizzly Bears")
        val p = d.player1
        val marine = d.putCardInHand(p, "Noise Marine")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        assertEquals(2, d.stackSize)
        d.bothPass()
        while (d.state.pendingDecision != null) d.decide(Choices(yes = true, pick = listOf("Grizzly Bears")))
        assertFalse(d.getPermanents(p).contains(marine))
        assertTrue(d.marineDamage(marine).isEmpty())
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(listOf(d.player2 to 2), d.marineDamage(marine).map { it.targetId to it.amount })
    }

    @Test fun damageCountsEarlierSpellsCastByTheController() {
        val d = customScenario(NoiseMarine, "Sol Ring", "Ornithopter")
        val p = d.player1
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        val ornithopter = d.putCardInHand(p, "Ornithopter")
        assertNull(d.castSpell(p, ornithopter).error)
        d.resolveAll()
        val marine = d.putCardInHand(p, "Noise Marine")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(listOf(d.player2 to 3), d.marineDamage(marine).map { it.targetId to it.amount })
    }

    @Test fun previousTurnsSpellsDoNotCount() {
        val d = customScenario(NoiseMarine, "Sol Ring")
        val p = d.player1
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        val marine = d.putCardInHand(p, "Noise Marine")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(listOf(d.player2 to 1), d.marineDamage(marine).map { it.targetId to it.amount })
    }

    @Test fun aSpellCastAfterTheTargetIsChosenCountsAtResolution() {
        val d = customScenario(NoiseMarine, "Shock")
        val p = d.player1
        val marine = d.putCardInHand(p, "Noise Marine")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        assertNull(d.bothPass().error)
        assertNull(d.bothPass().error)
        assertTrue(d.state.pendingDecision is ChooseTargetsDecision)
        assertNull(d.submitTargetSelection(p, listOf(d.player2)).error)
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        d.resolveAll()
        assertEquals(listOf(d.player2 to 2), d.marineDamage(marine).map { it.targetId to it.amount })
        assertEquals(16, d.getLifeTotal(d.player2))
    }

    @Test fun anOpponentsSpellDoesNotCount() {
        val d = customScenario(NoiseMarine, "Shock")
        val p = d.player1
        val marine = d.putCardInHand(p, "Noise Marine")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        assertNull(d.bothPass().error)
        assertNull(d.bothPass().error)
        assertTrue(d.state.pendingDecision is ChooseTargetsDecision)
        assertNull(d.submitTargetSelection(p, listOf(d.player2)).error)
        val shock = d.putCardInHand(d.player2, "Shock")
        d.giveMana(d.player2, Color.RED)
        assertNull(d.passPriority(p).error)
        assertNull(d.castSpell(d.player2, shock, listOf(p)).error)
        d.resolveAll()
        assertEquals(listOf(d.player2 to 1), d.marineDamage(marine).map { it.targetId to it.amount })
        assertEquals(18, d.getLifeTotal(p))
    }

    @Test fun sonicBlasterCanTargetACreature() {
        val d = customScenario(NoiseMarine, "Grizzly Bears")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val marine = d.putCardInHand(p, "Noise Marine")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        d.resolveAll(Choices(targets = listOf(target)))
        assertEquals(listOf(target to 1), d.marineDamage(marine).map { it.targetId to it.amount })
        assertTrue(d.getPermanents(d.player2).contains(target))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test fun sonicBlasterCanTargetAPlaneswalkerButNotAnArtifact() {
        val d = customScenario(NoiseMarine, "Jace Beleren", "Sol Ring")
        val p = d.player1
        val walker = d.putCardInHand(p, "Jace Beleren")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLUE, 2)
        assertNull(d.castSpell(p, walker).error)
        d.resolveAll()
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val marine = d.putCardInHand(p, "Noise Marine")
        d.payMarine(p)
        assertNull(d.castSpell(p, marine).error)
        assertNull(d.bothPass().error)
        assertNull(d.bothPass().error)
        val decision = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull(decision)
        val legal = decision!!.legalTargets.values.flatten()
        assertTrue(legal.contains(walker))
        assertTrue(legal.contains(p))
        assertTrue(legal.contains(d.player2))
        assertFalse(legal.contains(artifact))
        assertNull(d.submitTargetSelection(p, listOf(walker)).error)
        d.resolveAll()
        assertEquals(listOf(walker to 2), d.marineDamage(marine).map { it.targetId to it.amount })
    }
}
