package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KillMaimBurn
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class KillMaimBurnIndependentCertificationTest {
    private val name = "Kill! Maim! Burn!"

    private fun GameTestDriver.paySpell(player: EntityId) {
        giveColorlessMana(player, 3)
        giveMana(player, Color.BLACK)
        giveMana(player, Color.RED)
    }

    private fun GameTestDriver.castModes(
        player: EntityId,
        spell: EntityId,
        modes: List<Int>,
        targetsByMode: List<List<ChosenTarget>>,
    ) = submit(
        CastSpell(
            playerId = player,
            cardId = spell,
            targets = targetsByMode.flatten(),
            paymentStrategy = PaymentStrategy.FromPool,
            chosenModes = modes,
            modeTargetsOrdered = targetsByMode,
        ),
    )

    @Test
    fun artifactModeDestroysItsTarget() {
        val d = customScenario(KillMaimBurn, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.paySpell(p)

        assertNull(d.castModes(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Sol Ring"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
    }

    @Test
    fun creatureModeDestroysItsTargetAndLeavesOtherCreaturesAlone() {
        val d = customScenario(KillMaimBurn, "Grizzly Bears")
        val p = d.player1
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val other = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, name)
        d.paySpell(p)

        assertNull(d.castModes(p, spell, listOf(1), listOf(listOf(ChosenTarget.Permanent(victim)))).error)
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(victim))
        assertTrue(d.getPermanents(p).contains(other))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun playerModeDealsExactlyThreeDamageToItsTarget() {
        val d = customScenario(KillMaimBurn)
        val p = d.player1
        val spell = d.putCardInHand(p, name)
        d.paySpell(p)

        assertNull(d.castModes(p, spell, listOf(2), listOf(listOf(ChosenTarget.Player(d.player2)))).error)
        d.resolveAll()

        assertEquals(17, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
    }

    @Test
    fun allThreeModesCanBeChosenTogether() {
        val d = customScenario(KillMaimBurn, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, name)
        d.paySpell(p)

        assertNull(
            d.castModes(
                p,
                spell,
                listOf(0, 1, 2),
                listOf(
                    listOf(ChosenTarget.Permanent(artifact)),
                    listOf(ChosenTarget.Permanent(creature)),
                    listOf(ChosenTarget.Player(d.player2)),
                ),
            ).error,
        )
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(artifact))
        assertFalse(d.getPermanents(d.player2).contains(creature))
        assertEquals(17, d.getLifeTotal(d.player2))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
    }

    @Test
    fun artifactModeRejectsACreatureAsItsTarget() {
        val d = customScenario(KillMaimBurn, "Grizzly Bears")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, name)
        d.paySpell(p)

        assertNotNull(d.castModes(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(creature)))).error)
        assertTrue(d.getPermanents(d.player2).contains(creature))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun creatureModeRejectsANoncreatureArtifactAsItsTarget() {
        val d = customScenario(KillMaimBurn, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.paySpell(p)

        assertNotNull(d.castModes(p, spell, listOf(1), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun playerModeRejectsAPermanentAsItsTarget() {
        val d = customScenario(KillMaimBurn, "Grizzly Bears")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, name)
        d.paySpell(p)

        assertNotNull(d.castModes(p, spell, listOf(2), listOf(listOf(ChosenTarget.Permanent(creature)))).error)
        assertEquals(20, d.getLifeTotal(d.player2))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun threeGenericAndBlackWithoutRedCannotPayTheCost() {
        val d = customScenario(KillMaimBurn, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castModes(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
    }

    @Test
    fun twoGenericWithBothColorsCannotPayTheCost() {
        val d = customScenario(KillMaimBurn, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.RED)

        assertNotNull(d.castModes(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
    }

    @Test
    fun instantCanBeCastDuringAnOpponentsTurn() {
        val d = customScenario(KillMaimBurn)
        val p = d.player1
        val spell = d.putCardInHand(p, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(d.player2, d.activePlayer)
        assertNull(d.passPriority(d.player2).error)
        d.paySpell(p)

        assertNull(d.castModes(p, spell, listOf(2), listOf(listOf(ChosenTarget.Player(d.player2)))).error)
        d.resolveAll()

        assertEquals(17, d.getLifeTotal(d.player2))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
    }
}
