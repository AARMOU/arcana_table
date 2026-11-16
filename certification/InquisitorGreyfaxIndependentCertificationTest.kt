package com.arcanatable.customcards

import com.arcanatable.customcards.cards.InquisitorGreyfax
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class InquisitorGreyfaxIndependentCertificationTest {
    private fun hunt(
        d: GameTestDriver,
        player: EntityId,
        greyfax: EntityId,
        target: EntityId,
    ) = d.submit(
        ActivateAbility(
            player,
            greyfax,
            d.cardRegistry.getCard("Inquisitor Greyfax")!!.script.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(target)),
        )
    )

    private fun clueTokens(d: GameTestDriver, player: EntityId) =
        d.getPermanents(player).filter {
            d.state.getEntity(it)?.has<TokenComponent>() == true && d.projected.hasType(it, "ARTIFACT")
        }

    private fun createClue(d: GameTestDriver): Pair<EntityId, EntityId> {
        val player = d.player1
        val greyfax = d.putCreatureOnBattlefield(player, "Inquisitor Greyfax")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(greyfax)
        d.giveColorlessMana(player, 1)
        assertNull(hunt(d, player, greyfax, target).error)
        d.resolveAll()
        return greyfax to clueTokens(d, player).single()
    }

    @Test
    fun castingRequiresOneGenericWhiteBlueAndBlackAndEntersAsThreeThreeWithVigilance() {
        val d = customScenario(InquisitorGreyfax)
        val p = d.player1
        val card = d.putCardInHand(p, "Inquisitor Greyfax")
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Inquisitor Greyfax"))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Inquisitor Greyfax").contains(card))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.VIGILANCE))
    }

    @Test
    fun vigilanceAndWisdomGrantOtherCreaturesPowerAndVigilance() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        val ally = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(greyfax)
        d.removeSummoningSickness(ally)

        assertEquals(3, d.projected.getPower(greyfax))
        assertEquals(3, d.projected.getPower(ally))
        assertEquals(2, d.projected.getToughness(ally))
        assertEquals(2, d.projected.getPower(enemy))
        assertEquals(2, d.projected.getToughness(enemy))
        assertTrue(d.projected.hasKeyword(greyfax, Keyword.VIGILANCE))
        assertTrue(d.projected.hasKeyword(ally, Keyword.VIGILANCE))
        assertFalse(d.projected.hasKeyword(enemy, Keyword.VIGILANCE))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(greyfax, ally), opponent).error)
        assertFalse(d.isTapped(greyfax))
        assertFalse(d.isTapped(ally))
    }

    @Test
    fun huntForHeresyPaysOneManaAndTapsGreyfaxAndAnOpponentsCreatureThenInvestigates() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(greyfax)
        d.giveColorlessMana(p, 1)

        assertNull(hunt(d, p, greyfax, target).error)
        assertTrue(d.isTapped(greyfax))
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()

        assertTrue(d.isTapped(target))
        assertEquals(1, clueTokens(d, p).size)
    }

    @Test
    fun huntForHeresyCannotBeActivatedWithoutOneMana() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(greyfax)

        assertNotNull(hunt(d, p, greyfax, target).error)
        assertFalse(d.isTapped(greyfax))
        assertFalse(d.isTapped(target))
    }

    @Test
    fun huntForHeresyCannotBeActivatedWithGreyfaxAlreadyTapped() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(greyfax)
        d.giveColorlessMana(p, 1)
        d.tapPermanent(greyfax)

        assertNotNull(hunt(d, p, greyfax, target).error)
        assertEquals(1, d.manaPool(p).colorless)
        assertFalse(d.isTapped(target))
    }

    @Test
    fun huntForHeresyCannotTargetYourCreatureOrAnOpponentsNoncreature() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        val ownCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponentLand = d.putPermanentOnBattlefield(opponent, "Plains")
        d.removeSummoningSickness(greyfax)
        d.giveColorlessMana(p, 1)

        assertNotNull(hunt(d, p, greyfax, ownCreature).error)
        assertNotNull(hunt(d, p, greyfax, opponentLand).error)
        assertFalse(d.isTapped(greyfax))
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun huntForHeresyCanBeActivatedDuringAnOpponentsTurn() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(greyfax)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        d.giveColorlessMana(p, 1)

        assertNull(hunt(d, p, greyfax, target).error)
        d.resolveAll()

        assertTrue(d.isTapped(greyfax))
        assertTrue(d.isTapped(target))
        assertEquals(1, clueTokens(d, p).size)
    }

    @Test
    fun sacrificingAClueForTwoManaDrawsACard() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val (_, clue) = createClue(d)
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.giveColorlessMana(p, 2)

        assertNull(d.activate(p, clue).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertFalse(d.getPermanents(p).contains(clue))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun aClueCannotBeSacrificedForOneMana() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val (_, clue) = createClue(d)
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.giveColorlessMana(p, 1)

        assertNotNull(d.activate(p, clue).error)
        assertTrue(d.getPermanents(p).contains(clue))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertEquals(1, d.manaPool(p).colorless)
    }
}
