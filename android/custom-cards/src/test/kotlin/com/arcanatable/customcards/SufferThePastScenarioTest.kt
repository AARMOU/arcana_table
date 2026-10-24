package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SufferThePast
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.player.PlayerHexproofComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class SufferThePastScenarioTest {
    @Test
    fun xTargetsAreExiledAndLifeChangesByTheNumberActuallyExiled() {
        val d = customScenario(SufferThePast, "Grizzly Bears", "Forest")
        val p = d.player1
        val opponent = d.player2
        val bear = d.putCardInGraveyard(opponent, "Grizzly Bears")
        val forest = d.putCardInGraveyard(opponent, "Forest")
        d.putCardInGraveyard(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Suffer the Past")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        val playerTarget = ChosenTarget.Player(opponent)
        val cardTargets = listOf(
            ChosenTarget.Card(bear, opponent, Zone.GRAVEYARD),
            ChosenTarget.Card(forest, opponent, Zone.GRAVEYARD),
        )

        fun cast(targets: List<ChosenTarget>) = d.submit(
            CastSpell(
                playerId = p,
                cardId = spell,
                targets = targets,
                xValue = 2,
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )

        assertNotNull("X cards must be selected as targets", cast(listOf(playerTarget, cardTargets.first())).error)
        assertNull(cast(listOf(playerTarget) + cardTargets).error)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(opponent))
        assertEquals(22, d.getLifeTotal(p))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Grizzly Bears", "Forest")))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).isEmpty())
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).containsAll(listOf("Grizzly Bears", "Suffer the Past")))
    }
    @Test
    fun canTargetYourOwnGraveyard() {
        val d = customScenario(SufferThePast, "Grizzly Bears")
        val caster = d.player1
        val bear = d.putCardInGraveyard(caster, "Grizzly Bears")
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)
        d.giveColorlessMana(caster, 1)
        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(ChosenTarget.Player(caster), ChosenTarget.Card(bear, caster, Zone.GRAVEYARD)),
            xValue = 1,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNull("Oracle permits targeting yourself", result.error)
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(caster))
        assertTrue(d.zoneNames(caster, Zone.EXILE).contains("Grizzly Bears"))
    }
    @Test
    fun zeroXExilesNothingAndChangesNoLife() {
        val d = customScenario(SufferThePast, "Grizzly Bears")
        val caster = d.player1
        val opponent = d.player2
        d.putCardInGraveyard(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)

        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(ChosenTarget.Player(opponent)),
            xValue = 0,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNull("X=0 requires no graveyard card targets", result.error)
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(caster))
        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).isEmpty())
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun cannotCastWithMoreTargetsThanCardsInChosenGraveyard() {
        val d = customScenario(SufferThePast, "Grizzly Bears")
        val caster = d.player1
        val opponent = d.player2
        val bear = d.putCardInGraveyard(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)
        d.giveColorlessMana(caster, 2)

        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(
                ChosenTarget.Player(opponent),
                ChosenTarget.Card(bear, opponent, Zone.GRAVEYARD),
            ),
            xValue = 2,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNotNull("X=2 requires two distinct legal graveyard targets", result.error)
        assertEquals(20, d.getLifeTotal(caster))
        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun oneTargetLeavingGraveyardBeforeResolutionOnlyCountsRemainingExile() {
        val d = customScenario(SufferThePast, "Grizzly Bears", "Forest")
        val caster = d.player1
        val opponent = d.player2
        val bear = d.putCardInGraveyard(opponent, "Grizzly Bears")
        val forest = d.putCardInGraveyard(opponent, "Forest")
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)
        d.giveColorlessMana(caster, 2)

        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(
                ChosenTarget.Player(opponent),
                ChosenTarget.Card(bear, opponent, Zone.GRAVEYARD),
                ChosenTarget.Card(forest, opponent, Zone.GRAVEYARD),
            ),
            xValue = 2,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNull(result.error)

        // Another effect removes one target while Suffer the Past is on the stack.
        d.moveToExile(bear)
        d.resolveAll()

        assertEquals(21, d.getLifeTotal(caster))
        assertEquals(19, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Grizzly Bears", "Forest")))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).isEmpty())
    }

    @Test
    fun cardTargetsMustComeFromTheTargetedPlayersGraveyard() {
        val d = customScenario(SufferThePast, "Grizzly Bears", "Forest")
        val caster = d.player1
        val opponent = d.player2
        val opponentsBear = d.putCardInGraveyard(opponent, "Grizzly Bears")
        val castersForest = d.putCardInGraveyard(caster, "Forest")
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)
        d.giveColorlessMana(caster, 2)

        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(
                ChosenTarget.Player(opponent),
                ChosenTarget.Card(opponentsBear, opponent, Zone.GRAVEYARD),
                ChosenTarget.Card(castersForest, caster, Zone.GRAVEYARD),
            ),
            xValue = 2,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNotNull("Every card target must be in the targeted player's graveyard", result.error)
        assertEquals(20, d.getLifeTotal(caster))
        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(caster, Zone.GRAVEYARD).contains("Forest"))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun threePlayersOnlyTheTargetedPlayersGraveyardAndLifeAreAffected() {
        val d = CardPool.driver("Grizzly Bears", "Forest")
        val (p1, p2, p3) = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p1)
        val p2Bear = d.putCardInGraveyard(p2, "Grizzly Bears")
        val p3Bear = d.putCardInGraveyard(p3, "Grizzly Bears")
        val p3Forest = d.putCardInGraveyard(p3, "Forest")
        val spell = d.putCardInHand(p1, "Suffer the Past")
        d.giveMana(p1, Color.BLACK)
        d.giveColorlessMana(p1, 2)

        fun cast(player: EntityId, cards: List<ChosenTarget>) = d.submit(CastSpell(
            playerId = p1,
            cardId = spell,
            targets = listOf(ChosenTarget.Player(player)) + cards,
            xValue = 2,
            paymentStrategy = PaymentStrategy.FromPool,
        ))

        val mixed = cast(p2, listOf(
            ChosenTarget.Card(p2Bear, p2, Zone.GRAVEYARD),
            ChosenTarget.Card(p3Bear, p3, Zone.GRAVEYARD),
        ))
        assertNotNull("Cannot pull a card from a third player's graveyard", mixed.error)

        assertNull(cast(p3, listOf(
            ChosenTarget.Card(p3Bear, p3, Zone.GRAVEYARD),
            ChosenTarget.Card(p3Forest, p3, Zone.GRAVEYARD),
        )).error)
        d.resolveAll()

        assertEquals(22, d.getLifeTotal(p1))
        assertEquals(20, d.getLifeTotal(p2))
        assertEquals(18, d.getLifeTotal(p3))
        assertTrue(d.zoneNames(p3, Zone.GRAVEYARD).isEmpty())
        assertTrue(d.zoneNames(p2, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun theSameCardCannotBeChosenTwiceForX() {
        val d = customScenario(SufferThePast, "Grizzly Bears", "Forest")
        val caster = d.player1
        val opponent = d.player2
        val bear = d.putCardInGraveyard(opponent, "Grizzly Bears")
        d.putCardInGraveyard(opponent, "Forest") // enough cards exist, so only duplication can fail
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)
        d.giveColorlessMana(caster, 2)

        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(
                ChosenTarget.Player(opponent),
                ChosenTarget.Card(bear, opponent, Zone.GRAVEYARD),
                ChosenTarget.Card(bear, opponent, Zone.GRAVEYARD),
            ),
            xValue = 2,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNotNull("X targets must be X different cards", result.error)
        assertEquals(20, d.getLifeTotal(caster))
        assertEquals(20, d.getLifeTotal(opponent))
        assertEquals(2, d.zoneNames(opponent, Zone.GRAVEYARD).size)
    }
    @Test
    fun playerBecomingHexproofOnTheStackStillExilesCardsButOnlyGainsLife() {
        val d = customScenario(SufferThePast, "Grizzly Bears", "Forest")
        val caster = d.player1
        val targetPlayer = d.player2
        val bear = d.putCardInGraveyard(targetPlayer, "Grizzly Bears")
        val forest = d.putCardInGraveyard(targetPlayer, "Forest")
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)
        d.giveColorlessMana(caster, 2)

        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(
                ChosenTarget.Player(targetPlayer),
                ChosenTarget.Card(bear, targetPlayer, Zone.GRAVEYARD),
                ChosenTarget.Card(forest, targetPlayer, Zone.GRAVEYARD),
            ),
            xValue = 2,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNull("All targets are legal when the spell is cast", result.error)

        // The opponent gains hexproof after casting. Only the player target becomes illegal;
        // the two graveyard card targets remain legal (CR 608.2b and Suffer the Past ruling).
        d.replaceState(d.state.updateEntity(targetPlayer) { it.with(PlayerHexproofComponent()) })
        d.resolveAll()

        assertEquals("Caster still gains 2 life for both cards actually exiled", 22, d.getLifeTotal(caster))
        assertEquals("Illegal player target must not lose life", 20, d.getLifeTotal(targetPlayer))
        assertTrue(d.zoneNames(targetPlayer, Zone.EXILE).containsAll(listOf("Grizzly Bears", "Forest")))
        assertTrue(d.zoneNames(targetPlayer, Zone.GRAVEYARD).isEmpty())
    }

}
