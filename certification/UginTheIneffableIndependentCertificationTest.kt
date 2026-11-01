package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UginTheIneffable
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class UginTheIneffableIndependentCertificationTest {
    private val uginName = "Ugin, the Ineffable"

    private fun castUgin(d: GameTestDriver, player: EntityId): EntityId {
        val card = d.putCardInHand(player, uginName)
        d.giveColorlessMana(player, 6)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()
        return d.permanentsNamed(player, uginName).single()
    }

    private fun loyalty(d: GameTestDriver, ugin: EntityId): Int =
        d.state.getEntity(ugin)?.get<CountersComponent>()?.getCount(CounterType.LOYALTY) ?: 0

    private fun GameTestDriver.activateMinusThree(
        player: EntityId,
        ugin: EntityId,
        target: EntityId,
    ) = submit(
        ActivateAbility(
            player,
            ugin,
            cardRegistry.getCard(uginName)!!.script.activatedAbilities[1].id,
            targets = listOf(ChosenTarget.Permanent(target)),
        ),
    )

    @Test
    fun colorlessSpellsCostTwoLessButColoredSpellsDoNot() {
        val d = customScenario(UginTheIneffable, "Hedron Archive", "Grizzly Bears")
        val p = d.player1
        val uginCard = d.putCardInHand(p, uginName)
        d.giveColorlessMana(p, 5)

        assertNotNull(d.castSpell(p, uginCard).error)
        assertTrue(d.getHand(p).contains(uginCard))
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, uginCard).error)
        d.resolveAll()
        val ugin = d.permanentsNamed(p, uginName).single()

        val archive = d.putCardInHand(p, "Hedron Archive")
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, archive).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Hedron Archive").isNotEmpty())

        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        assertNotNull(d.castSpell(p, bears).error)
        assertTrue(d.getHand(p).contains(bears))
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
        assertTrue(d.getPermanents(p).contains(ugin))
    }

    @Test
    fun plusOneExilesTheTopCardFaceDownCreatesAColorlessSpiritAndReturnsTheCardWhenItLeaves() {
        val d = customScenario(UginTheIneffable, "Grizzly Bears", "Shock")
        val p = d.player1
        val ugin = castUgin(d, p)
        val topCard = d.putCardOnTopOfLibrary(p, "Grizzly Bears")

        assertEquals(4, loyalty(d, ugin))
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()

        assertEquals(5, loyalty(d, ugin))
        assertTrue(d.getExile(p).contains(topCard))
        assertTrue(d.state.getEntity(topCard)!!.has<FaceDownComponent>())
        assertEquals("Grizzly Bears", d.state.getEntity(topCard)!!.get<CardComponent>()!!.name)

        val spirit = d.permanentsNamed(p, "Spirit").single()
        assertTrue(d.state.getEntity(spirit)!!.has<TokenComponent>())
        assertTrue(d.projected.hasType(spirit, "CREATURE"))
        assertTrue(d.projected.hasSubtype(spirit, "SPIRIT"))
        assertEquals(2, d.projected.getPower(spirit))
        assertEquals(2, d.projected.getToughness(spirit))
        assertTrue(d.projected.getColors(spirit).isEmpty())

        assertNotNull(d.activate(p, ugin, index = 0).error)
        assertEquals(5, loyalty(d, ugin))

        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpellWithTargets(p, shock, listOf(ChosenTarget.Permanent(spirit))).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Spirit").isEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Grizzly Bears"))
        assertFalse(d.getExile(p).contains(topCard))
    }

    @Test
    fun minusThreeDestroysAColoredPermanentAndPaysThreeLoyalty() {
        val d = customScenario(UginTheIneffable, "Serra Angel", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val ugin = castUgin(d, p)
        val angel = d.putCreatureOnBattlefield(opponent, "Serra Angel")

        assertNull(d.activateMinusThree(p, ugin, angel).error)
        d.resolveAll()

        assertEquals(1, loyalty(d, ugin))
        assertFalse(d.getPermanents(opponent).contains(angel))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Serra Angel"))
    }

    @Test
    fun minusThreeCannotTargetAColorlessPermanent() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val ugin = castUgin(d, p)
        val ring = d.putPermanentOnBattlefield(opponent, "Sol Ring")

        assertNotNull(d.activateMinusThree(p, ugin, ring).error)

        assertEquals(4, loyalty(d, ugin))
        assertTrue(d.getPermanents(opponent).contains(ring))
    }

    @Test
    fun minusThreeCannotBeActivatedWhenItsControllerDoesNotHavePriorityInTheirMainPhase() {
        val d = customScenario(UginTheIneffable, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val ugin = castUgin(d, p)
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        assertNotNull(d.activateMinusThree(p, ugin, bear).error)

        assertEquals(4, loyalty(d, ugin))
        assertTrue(d.getPermanents(opponent).contains(bear))
    }

    @Test
    fun minusThreeCannotBeActivatedWithOnlyOneLoyaltyOnTheNextTurn() {
        val d = customScenario(UginTheIneffable, "Serra Angel", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val ugin = castUgin(d, p)
        val angel = d.putCreatureOnBattlefield(opponent, "Serra Angel")
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        assertNull(d.activateMinusThree(p, ugin, angel).error)
        d.resolveAll()
        assertEquals(1, loyalty(d, ugin))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        assertNotNull(d.activateMinusThree(p, ugin, bear).error)

        assertEquals(1, loyalty(d, ugin))
        assertTrue(d.getPermanents(opponent).contains(bear))
    }
}
