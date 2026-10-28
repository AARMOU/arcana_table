package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UlamogsNullifier
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

/**
 * First-pass real-engine acceptance probes for the processor ETB.
 *
 * The source remains intentionally unregistered: these tests, including
 * negative behavior, must pass before the card is shipped/exported.
 */
class UlamogsNullifierScenarioTest {
    @Test
    fun flashCastProcessesExactlyTwoOpponentOwnedCardsThenCountersTargetSpell() {
        val d = customScenario(UlamogsNullifier, "Grizzly Bears", "Island", "Swamp")
        val p = d.player1
        val opponent = d.player2

        d.putCardInExile(opponent, "Island")
        d.putCardInExile(opponent, "Swamp")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val bears = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN, 2)
        assertNull(d.castSpell(opponent, bears).error)
        val stackTarget = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val nullifier = d.putCardInHand(p, "Ulamog's Nullifier")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        assertNull("Flash must permit casting on opponent's turn", d.castSpell(p, nullifier).error)
        assertNull(d.bothPass().error) // resolve creature, leave opposing spell on stack
        d.resolveAll(Choices(
            targets = listOf(stackTarget),
            yes = true,
            pick = listOf("Island", "Swamp"),
        ))

        assertNotNull(d.findPermanent(p, "Ulamog's Nullifier"))
        assertTrue("Target spell was countered, not resolved", d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).containsAll(listOf("Island", "Swamp")))
        assertFalse(d.zoneNames(opponent, Zone.EXILE).contains("Island"))
        assertFalse(d.zoneNames(opponent, Zone.EXILE).contains("Swamp"))
        val def = d.cardRegistry.getCard("Ulamog's Nullifier")!!
        assertTrue(def.colors.isEmpty())
        assertTrue(def.hasKeyword(Keyword.DEVOID))
        assertTrue(def.hasKeyword(Keyword.FLASH))
        assertTrue(def.hasKeyword(Keyword.FLYING))
    }

    @Test
    fun decliningOptionalProcessingLeavesExileIntactAndSpellResolves() {
        val d = customScenario(UlamogsNullifier, "Grizzly Bears", "Island", "Swamp")
        val p = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Island")
        d.putCardInExile(opponent, "Swamp")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val bears = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN, 2)
        assertNull(d.castSpell(opponent, bears).error)
        val stackTarget = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)
        val nullifier = d.putCardInHand(p, "Ulamog's Nullifier")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, nullifier).error)
        assertNull(d.bothPass().error)
        d.resolveAll(Choices(targets = listOf(stackTarget), yes = false))

        assertEquals(setOf("Island", "Swamp"), d.zoneNames(opponent, Zone.EXILE).toSet())
        assertNotNull("Declining processing must not counter the spell", d.findPermanent(opponent, "Grizzly Bears"))
    }

    @Test
    fun controllersOwnExiledCardDoesNotCountTowardTwoOpponentOwnedCards() {
        val d = customScenario(UlamogsNullifier, "Grizzly Bears", "Island", "Swamp")
        val p = d.player1
        val opponent = d.player2
        d.putCardInExile(p, "Island")
        d.putCardInExile(opponent, "Swamp")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val bears = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN, 2)
        assertNull(d.castSpell(opponent, bears).error)
        val stackTarget = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)
        val nullifier = d.putCardInHand(p, "Ulamog's Nullifier")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, nullifier).error)
        assertNull(d.bothPass().error)
        d.resolveAll(Choices(targets = listOf(stackTarget), yes = true, pick = listOf("Island", "Swamp")))

        assertEquals(listOf("Island"), d.zoneNames(p, Zone.EXILE))
        assertEquals(listOf("Swamp"), d.zoneNames(opponent, Zone.EXILE))
        assertNotNull("Only one opponent-owned exile: processing is impossible", d.findPermanent(opponent, "Grizzly Bears"))
    }

    @Test
    fun entersWithNoSpellOnStackAndDoesNotProcessExile() {
        val d = customScenario(UlamogsNullifier, "Island", "Swamp")
        val p = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Island")
        d.putCardInExile(opponent, "Swamp")

        // Nullifier may enter even without a legal spell to target. Its
        // optional processing cannot move exiled cards without that target.
        d.putCreatureOnBattlefield(p, "Ulamog's Nullifier")
        d.resolveAll()

        assertNotNull("Nullifier must remain on the battlefield", d.findPermanent(p, "Ulamog's Nullifier"))
        assertEquals(
            "without a target spell, both opponent-owned cards remain exiled",
            setOf("Island", "Swamp"),
            d.zoneNames(opponent, Zone.EXILE).toSet(),
        )
        assertFalse(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Island"))
        assertFalse(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Swamp"))
    }
}
