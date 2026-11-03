package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ImotekhTheStormlord
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Imotekh the Stormlord (40K #5): Phaeron — whenever one or more artifact cards leave your graveyard,
 * create two 2/2 black Necron Warrior artifact creature tokens; Grand Strategist — at the beginning of
 * combat on your turn, another target artifact creature you control gets +2/+2 and gains menace until
 * end of turn. Ruling quoted in [ImotekhTheStormlord].
 */
class ImotekhTheStormlordScenarioTest {

    private fun game() = customScenario(
        ImotekhTheStormlord, "Ornithopter", "Memnite", "Sol Ring", "Grizzly Bears", "Tormod's Crypt", "Raise Dead",
    )

    private fun GameTestDriver.warriors(player: EntityId) = permanentsNamed(player, "Necron Warrior")

    /** Tormod's Crypt: exile [whose] whole graveyard in one event. */
    private fun GameTestDriver.crypt(player: EntityId, whose: EntityId) {
        val crypt = putPermanentOnBattlefield(player, "Tormod's Crypt")
        val ability = cardRegistry.getCard("Tormod's Crypt")!!.script.activatedAbilities[0].id
        assertNull(submit(ActivateAbility(player, crypt, ability, targets = listOf(ChosenTarget.Player(whose)))).error)
        resolveAll()
    }

    /** Raise Dead: return [card] from [player]'s graveyard to hand. */
    private fun GameTestDriver.raiseDead(player: EntityId, card: EntityId) {
        val spell = putCardInHand(player, "Raise Dead")
        giveMana(player, Color.BLACK, 1)
        assertNull(castSpellWithTargets(player, spell, listOf(ChosenTarget.Card(card, player, Zone.GRAVEYARD))).error)
        resolveAll()
    }

    @Test
    fun severalArtifactCardsLeavingTogetherTriggerOnce() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Imotekh the Stormlord")
        d.putCardInGraveyard(p, "Ornithopter")
        d.putCardInGraveyard(p, "Memnite")
        d.putCardInGraveyard(p, "Sol Ring")

        d.crypt(p, p)

        assertTrue(d.state.getZone(p, Zone.GRAVEYARD).none { d.getCardName(it) in setOf("Ornithopter", "Memnite", "Sol Ring") })
        val tokens = d.warriors(p)
        assertEquals("one trigger, two tokens (ruling 2022-10-07)", 2, tokens.size)
        tokens.forEach {
            assertTrue(d.projected.hasType(it, "ARTIFACT"))
            assertTrue(d.projected.isCreature(it))
            assertEquals(2, d.projected.getPower(it))
            assertEquals(2, d.projected.getToughness(it))
            assertTrue(d.projected.hasSubtype(it, "Necron"))
            assertEquals(setOf("BLACK"), d.projected.getColors(it))
        }
    }

    @Test
    fun aNonArtifactCardLeavingDoesNotTrigger() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Imotekh the Stormlord")
        val bears = d.putCardInGraveyard(p, "Grizzly Bears")

        d.raiseDead(p, bears)

        assertTrue(bears in d.getHand(p))
        assertTrue(d.warriors(p).isEmpty())
    }

    @Test
    fun artifactCardsLeavingTheOpponentsGraveyardDoNotTrigger() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Imotekh the Stormlord")
        d.putCardInGraveyard(d.player2, "Ornithopter")
        d.putCardInGraveyard(d.player2, "Sol Ring")

        d.crypt(p, d.player2)

        assertTrue(d.state.getZone(d.player2, Zone.GRAVEYARD).isEmpty())
        assertTrue(d.warriors(p).isEmpty())
        assertTrue(d.warriors(d.player2).isEmpty())
    }

    @Test
    fun withBiotransferenceACreatureCardLeavingYourGraveyardIsAnArtifactCardLeaving() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Imotekh the Stormlord")
        d.putPermanentOnBattlefield(p, "Biotransference")
        val bears = d.putCardInGraveyard(p, "Grizzly Bears")

        d.raiseDead(p, bears)

        assertTrue(bears in d.getHand(p))
        assertEquals("looks back: it was an artifact card in the graveyard (CR 603.10a)", 2, d.warriors(p).size)
        assertEquals("Raise Dead is not an artifact spell, so Biotransference didn't trigger", 20, d.getLifeTotal(p))
    }

    @Test
    fun grandStrategistTargetsOnlyAnotherArtifactCreatureYouControlUntilEndOfTurn() {
        val d = game()
        val p = d.player1
        val imotekh = d.putCreatureOnBattlefield(p, "Imotekh the Stormlord")
        val memnite = d.putCreatureOnBattlefield(p, "Memnite")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Ornithopter")

        d.passPriorityUntil(Step.BEGIN_COMBAT)
        val decision = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull("Grand Strategist asks for its target", decision)
        assertEquals(
            "not Imotekh itself, not a non-artifact creature, not an opponent's artifact creature",
            listOf(memnite), decision!!.legalTargets[0],
        )
        assertNull(d.submitTargetSelection(p, listOf(memnite)).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(memnite))
        assertEquals(3, d.projected.getToughness(memnite))
        assertTrue(d.projected.hasKeyword(memnite, Keyword.MENACE))
        assertEquals("Imotekh unchanged", 3, d.projected.getPower(imotekh))

        d.passPriorityUntil(Step.UPKEEP, d.player2)
        assertEquals(1, d.projected.getPower(memnite))
        assertEquals(1, d.projected.getToughness(memnite))
        assertFalse(d.projected.hasKeyword(memnite, Keyword.MENACE))
    }

    @Test
    fun grandStrategistWithNoOtherArtifactCreatureDoesNothing() {
        val d = game()
        val p = d.player1
        val imotekh = d.putCreatureOnBattlefield(p, "Imotekh the Stormlord")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")

        d.passPriorityUntil(Step.BEGIN_COMBAT)

        assertNull(d.state.pendingDecision)
        assertTrue(d.state.stack.isEmpty())
        assertEquals(3, d.projected.getPower(imotekh))
        assertEquals(2, d.projected.getPower(bears))
    }
}
