package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GargoyleFlock
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class GargoyleFlockIndependentCertificationTest {
    private val name = "Gargoyle Flock"

    private fun GameTestDriver.tokens(player: EntityId): List<EntityId> =
        getPermanents(player).filter { token ->
            state.getEntity(token)?.has<TokenComponent>() == true
        }

    private fun assertGargoyleToken(d: GameTestDriver, token: EntityId) {
        assertTrue(d.state.getEntity(token)!!.has<TokenComponent>())
        assertTrue(d.projected.hasType(token, "CREATURE"))
        assertTrue(d.projected.hasSubtype(token, "TYRANID"))
        assertTrue(d.projected.hasSubtype(token, "GARGOYLE"))
        val colors = d.projected.getColors(token)
        assertEquals(1, colors.size)
        assertTrue(colors.contains(Color.BLUE))
        assertEquals(1, d.projected.getPower(token))
        assertEquals(1, d.projected.getToughness(token))
        assertTrue(d.projected.hasKeyword(token, Keyword.FLYING))
    }

    @Test
    fun castingRequiresTwoGenericGreenAndBlueAndEntersAsAFlyingTwoTwo() {
        val missingGreen = customScenario(GargoyleFlock)
        val greenUnpaidCard = missingGreen.putCardInHand(missingGreen.player1, name)
        missingGreen.giveMana(missingGreen.player1, Color.BLUE)
        missingGreen.giveColorlessMana(missingGreen.player1, 2)
        assertNotNull(missingGreen.castSpell(missingGreen.player1, greenUnpaidCard).error)

        val missingBlue = customScenario(GargoyleFlock)
        val blueUnpaidCard = missingBlue.putCardInHand(missingBlue.player1, name)
        missingBlue.giveMana(missingBlue.player1, Color.GREEN)
        missingBlue.giveColorlessMana(missingBlue.player1, 2)
        assertNotNull(missingBlue.castSpell(missingBlue.player1, blueUnpaidCard).error)

        val d = customScenario(GargoyleFlock)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertTrue(d.projected.hasSubtype(card, "TYRANID"))
        assertTrue(d.projected.hasSubtype(card, "GARGOYLE"))
        assertEquals(2, d.projected.getPower(card))
        assertEquals(2, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
    }

    @Test
    fun ownCreatureEnteringThisTurnCreatesOneBlueFlyingGargoyleAtYourEndStep() {
        val d = customScenario(GargoyleFlock, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, name)
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").contains(bear))
        assertTrue(d.tokens(p).isEmpty())

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()

        val token = d.tokens(p).single()
        assertGargoyleToken(d, token)
        assertTrue(d.tokens(d.player2).isEmpty())
    }

    @Test
    fun noCreatureEnteringUnderYourControlThisTurnCreatesNoToken() {
        val d = customScenario(GargoyleFlock)
        val p = d.player1
        d.putCreatureOnBattlefield(p, name)

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()

        assertTrue(d.tokens(p).isEmpty())
    }

    @Test
    fun anOpponentsCreatureEnteringThisTurnDoesNotSatisfySkyswarm() {
        val d = customScenario(GargoyleFlock, "Ambush Viper")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, name)
        val viper = d.putCardInHand(opponent, "Ambush Viper")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)

        assertNull(d.passPriority(p).error)
        assertNull(d.castSpell(opponent, viper).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(opponent, "Ambush Viper").contains(viper))

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()

        assertTrue(d.tokens(p).isEmpty())
    }

    @Test
    fun skyswarmDoesNotTriggerAtAnOpponentsEndStep() {
        val d = customScenario(GargoyleFlock, "Ambush Viper")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        val viper = d.putCardInHand(p, "Ambush Viper")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.passPriority(opponent).error)
        assertNull(d.castSpell(p, viper).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Ambush Viper").contains(viper))

        d.passPriorityUntil(Step.END, activePlayer = opponent)
        d.resolveAll()

        assertTrue(d.tokens(p).isEmpty())
    }
}
