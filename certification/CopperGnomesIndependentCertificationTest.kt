package com.arcanatable.customcards

// Independent real-Argentum acceptance for Copper Gnomes (queue #508).
// Derived from the trusted feeder's Oracle record, not Copilot's implementation:
// "{4}, Sacrifice this creature: You may put an artifact card from your hand
// onto the battlefield." A legal activation pays its costs even if declined.
// No card source, shared engine, or application behavior is changed.
import com.arcanatable.customcards.cards.CopperGnomes
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class CopperGnomesIndependentCertificationTest {
    @Test
    fun paidActivationSacrificesGnomesAndPutsArtifactFromOwnHandOntoBattlefield() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 4)

        assertNull(d.activate(p, gnomes).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertTrue("Activation must pay the sacrifice cost", d.getGraveyard(p).contains(gnomes))
        assertFalse(d.getPermanents(p).contains(gnomes))
        assertTrue("Artifact is put onto the battlefield without casting", d.getPermanents(p).contains(ring))
        assertFalse(d.findCardsInHand(p, "Sol Ring").contains(ring))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun decliningOptionalEffectStillPaysManaAndSacrificeCosts() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 4)

        assertNull(d.activate(p, gnomes).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.getGraveyard(p).contains(gnomes))
        assertTrue("Declining must leave the artifact in hand", d.findCardsInHand(p, "Sol Ring").contains(ring))
        assertFalse(d.getPermanents(p).contains(ring))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun selectionCannotPutNonartifactCreatureFromHandOntoBattlefield() {
        val d = customScenario(CopperGnomes, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        val ring = d.putCardInHand(p, "Sol Ring")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveColorlessMana(p, 4)

        assertNull(d.activate(p, gnomes).error)
        // Deliberately request an ineligible card. A correctly filtered optional
        // selection may choose the eligible artifact OR choose nothing (minimum 0),
        // but it must never move the nonartifact creature.
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears")))

        assertTrue(d.getGraveyard(p).contains(gnomes))
        assertTrue("Eligible artifact must stay in hand or enter the battlefield",
            d.findCardsInHand(p, "Sol Ring").contains(ring) || d.getPermanents(p).contains(ring))
        assertTrue("Nonartifact card must remain in hand", d.findCardsInHand(p, "Grizzly Bears").contains(bear))
        assertFalse(d.getPermanents(p).contains(bear))
    }

    @Test
    fun insufficientManaRejectsActivationWithoutSacrificingSource() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 3)

        assertNotNull(d.activate(p, gnomes).error)
        assertTrue(d.getPermanents(p).contains(gnomes))
        assertFalse(d.getGraveyard(p).contains(gnomes))
        assertTrue(d.findCardsInHand(p, "Sol Ring").contains(ring))
        assertEquals(3, d.manaPool(p).colorless)
    }

    @Test
    fun opponentCannotActivateAnotherPlayersGnomesEvenWithMana() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val owner = d.player1
        val opponent = d.player2
        val gnomes = d.putCreatureOnBattlefield(owner, "Copper Gnomes")
        val ring = d.putCardInHand(opponent, "Sol Ring")
        d.giveColorlessMana(opponent, 4)
        // Give the opponent priority, so failure cannot be explained by priority.
        assertNull(d.passPriority(owner).error)

        assertNotNull(d.activate(opponent, gnomes).error)
        assertTrue(d.getPermanents(owner).contains(gnomes))
        assertTrue(d.findCardsInHand(opponent, "Sol Ring").contains(ring))
        assertEquals(4, d.manaPool(opponent).colorless)
    }

    @Test
    fun newlyCastGnomesCanActivateWithoutWaitingForSummoningSickness() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val p = d.player1
        val gnomes = d.putCardInHand(p, "Copper Gnomes")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, gnomes).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(gnomes))
        assertEquals(1, d.projected.getPower(gnomes))
        assertEquals(1, d.projected.getToughness(gnomes))

        d.giveColorlessMana(p, 4)
        assertNull("No tap symbol: summoning sickness does not prohibit this ability", d.activate(p, gnomes).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))
        assertTrue(d.getGraveyard(p).contains(gnomes))
        assertTrue(d.getPermanents(p).contains(ring))
    }

    @Test
    fun abilityCanBeActivatedWithNoArtifactCardInHandAndDeclined() {
        val d = customScenario(CopperGnomes, "Grizzly Bears")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveColorlessMana(p, 4)

        assertNull("Ability has no target or artifact-in-hand activation condition", d.activate(p, gnomes).error)
        d.resolveAll(Choices(yes = false))
        assertTrue(d.getGraveyard(p).contains(gnomes))
        assertTrue(d.findCardsInHand(p, "Grizzly Bears").contains(bear))
        assertFalse(d.getPermanents(p).contains(bear))
    }
}
