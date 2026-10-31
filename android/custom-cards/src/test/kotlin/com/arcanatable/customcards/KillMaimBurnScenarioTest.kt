package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KillMaimBurn
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class KillMaimBurnScenarioTest {
    private fun cast(
        d: GameTestDriver,
        modes: List<Int>,
        targetsByMode: List<List<ChosenTarget>>,
    ) = d.submit(CastSpell(
        playerId = d.player1,
        cardId = d.putCardInHand(d.player1, "Kill! Maim! Burn!"),
        paymentStrategy = PaymentStrategy.FromPool,
        targets = targetsByMode.flatten(),
        chosenModes = modes,
        modeTargetsOrdered = targetsByMode,
    ))

    private fun giveMana(d: GameTestDriver) {
        d.giveMana(d.player1, Color.BLACK)
        d.giveMana(d.player1, Color.RED)
        d.giveColorlessMana(d.player1, 3)
    }

    @Test
    fun choosingAllModesDestroysBothPermanentsAndDamagesThePlayer() {
        val d = customScenario(KillMaimBurn, "Sol Ring", "Grizzly Bears")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        giveMana(d)

        assertNull(cast(
            d,
            listOf(0, 1, 2),
            listOf(
                listOf(ChosenTarget.Permanent(artifact)),
                listOf(ChosenTarget.Permanent(creature)),
                listOf(ChosenTarget.Player(d.player2)),
            ),
        ).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(d.player2, "Sol Ring").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertEquals(17, d.getLifeTotal(d.player2))
    }

    @Test
    fun choosingOnlyThePlayerModeIsAllowed() {
        val d = customScenario(KillMaimBurn)
        giveMana(d)

        assertNull(cast(
            d,
            listOf(2),
            listOf(listOf(ChosenTarget.Player(d.player2))),
        ).error)
        d.resolveAll()

        assertEquals(17, d.getLifeTotal(d.player2))
    }

    @Test
    fun artifactModeRejectsACreatureTarget() {
        val d = customScenario(KillMaimBurn, "Grizzly Bears")
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        giveMana(d)

        assertNotNull(cast(
            d,
            listOf(0),
            listOf(listOf(ChosenTarget.Permanent(creature))),
        ).error)
        assertEquals(1, d.permanentsNamed(d.player2, "Grizzly Bears").size)
    }

    @Test
    fun creatureModeRejectsAnArtifactTarget() {
        val d = customScenario(KillMaimBurn, "Sol Ring")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        giveMana(d)

        assertNotNull(cast(
            d,
            listOf(1),
            listOf(listOf(ChosenTarget.Permanent(artifact))),
        ).error)
        assertEquals(1, d.permanentsNamed(d.player2, "Sol Ring").size)
    }

    @Test
    fun playerModeRejectsAPermanentTarget() {
        val d = customScenario(KillMaimBurn, "Sol Ring")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        giveMana(d)

        assertNotNull(cast(
            d,
            listOf(2),
            listOf(listOf(ChosenTarget.Permanent(artifact))),
        ).error)
        assertEquals(20, d.getLifeTotal(d.player2))
        assertEquals(1, d.permanentsNamed(d.player2, "Sol Ring").size)
    }

    @Test
    fun choosingNoModesIsNotAllowed() {
        val d = customScenario(KillMaimBurn)
        giveMana(d)

        assertNotNull(cast(d, emptyList(), emptyList()).error)
    }
}
