package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DrownerOfHope
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import org.junit.Assert.*
import org.junit.Test

class DrownerOfHopeIndependentCertificationTest {
    private val name = "Drowner of Hope"

    private fun castDrowner(d: GameTestDriver, player: EntityId): EntityId {
        val drowner = d.putCardInHand(player, name)
        d.giveMana(player, Color.BLUE)
        d.giveColorlessMana(player, 5)
        assertNull(d.castSpell(player, drowner).error)
        d.resolveAll()
        return drowner
    }

    private fun scions(d: GameTestDriver, player: EntityId): List<EntityId> =
        d.getPermanents(player).filter { token ->
            d.state.getEntity(token)?.has<TokenComponent>() == true &&
                d.projected.hasType(token, "CREATURE") &&
                d.projected.hasSubtype(token, "ELDRAZI") &&
                d.projected.hasSubtype(token, "SCION")
        }

    private fun activateTapAbility(
        d: GameTestDriver,
        player: EntityId,
        drowner: EntityId,
        target: EntityId,
    ) = d.submit(
        ActivateAbility(
            player,
            drowner,
            d.cardRegistry.getCard(name)!!.script.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(target)),
        )
    )

    @Test
    fun castingRequiresBlueAndFiveGenericManaAndDevoidLeavesDrownerColorless() {
        val d = customScenario(DrownerOfHope)
        val p = d.player1
        val drowner = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 6)

        assertNotNull(d.castSpell(p, drowner).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(name))
        assertTrue(d.permanentsNamed(p, name).isEmpty())

        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, drowner).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(drowner))
        assertTrue(d.projected.getColors(drowner).isEmpty())
        assertEquals(5, d.projected.getPower(drowner))
        assertEquals(5, d.projected.getToughness(drowner))
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).blue)
    }

    @Test
    fun enteringCreatesExactlyTwoColorlessOneOneEldraziScions() {
        val d = customScenario(DrownerOfHope)
        val p = d.player1
        castDrowner(d, p)

        val tokens = scions(d, p)
        assertEquals(2, tokens.size)
        for (token in tokens) {
            assertTrue(d.state.getEntity(token)!!.has<TokenComponent>())
            assertTrue(d.projected.getColors(token).isEmpty())
            assertEquals(1, d.projected.getPower(token))
            assertEquals(1, d.projected.getToughness(token))
        }
        assertTrue(scions(d, d.player2).isEmpty())
    }

    @Test
    fun eachScionCanBeSacrificedForOneColorlessMana() {
        val d = customScenario(DrownerOfHope)
        val p = d.player1
        castDrowner(d, p)
        val tokens = scions(d, p)

        for ((index, token) in tokens.withIndex()) {
            assertNull(d.activate(p, token).error)
            d.resolveAll()

            assertEquals(index + 1, d.manaPool(p).colorless)
            assertFalse(d.getPermanents(p).contains(token))
            assertEquals(tokens.size - index - 1, scions(d, p).size)
        }
    }

    @Test
    fun aTappedScionCanBeSacrificedToTapAnOpponentsCreatureAtInstantSpeed() {
        val d = customScenario(DrownerOfHope, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val drowner = castDrowner(d, p)
        val scion = scions(d, p).first()
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.tapPermanent(scion)

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        assertNull(activateTapAbility(d, p, drowner, target).error)

        assertFalse(d.getPermanents(p).contains(scion))
        assertFalse(d.isTapped(target))
        d.resolveAll()
        assertTrue(d.isTapped(target))
        assertTrue(d.getPermanents(p).contains(drowner))
        assertEquals(1, scions(d, p).size)
    }

    @Test
    fun theTapAbilityCannotTargetANoncreature() {
        val d = customScenario(DrownerOfHope, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val drowner = castDrowner(d, p)
        val scion = scions(d, p).first()
        val artifact = d.putPermanentOnBattlefield(opponent, "Sol Ring")

        assertFalse(d.projected.hasType(artifact, "CREATURE"))
        assertNotNull(activateTapAbility(d, p, drowner, artifact).error)

        assertTrue(d.getPermanents(p).contains(scion))
        assertEquals(2, scions(d, p).size)
        assertFalse(d.isTapped(artifact))
    }
}
