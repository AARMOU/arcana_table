package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziConfluence
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class EldraziConfluenceIndependentCertificationTest {
    private val name = "Eldrazi Confluence"

    private fun cast(
        d: GameTestDriver,
        player: EntityId,
        card: EntityId,
        modes: List<Int>,
        modeTargets: List<List<ChosenTarget>>,
    ) = d.submit(
        CastSpell(
            playerId = player,
            cardId = card,
            targets = modeTargets.flatten(),
            paymentStrategy = PaymentStrategy.FromPool,
            chosenModes = modes,
            modeTargetsOrdered = modeTargets,
        ),
    )

    private fun scions(d: GameTestDriver, player: EntityId) =
        d.getPermanents(player).filter { token ->
            d.state.getEntity(token)?.has<TokenComponent>() == true &&
                d.projected.hasType(token, "CREATURE") &&
                d.projected.hasSubtype(token, "ELDRAZI") &&
                d.projected.hasSubtype(token, "SCION")
        }

    @Test
    fun castsAtInstantSpeedPayingTwoGenericAndTwoColorlessAndResolvesAllThreeModes() {
        val d = customScenario(EldraziConfluence, "Act of Treason", "Grizzly Bears", "Giant Spider")
        val p = d.player1
        val owner = d.player2
        val borrowed = d.putCreatureOnBattlefield(owner, "Grizzly Bears")
        val spider = d.putCreatureOnBattlefield(p, "Giant Spider")
        val steal = d.putCardInHand(p, "Act of Treason")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpellWithTargets(p, steal, listOf(ChosenTarget.Permanent(borrowed))).error)
        d.resolveAll()
        assertEquals(p, d.projected.getController(borrowed))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = owner)
        assertNull(d.passPriority(owner).error)
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)
        assertNull(
            cast(
                d,
                p,
                confluence,
                listOf(0, 1, 2),
                listOf(listOf(ChosenTarget.Permanent(spider)), listOf(ChosenTarget.Permanent(borrowed)), emptyList()),
            ).error,
        )
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()

        assertEquals(5, d.projected.getPower(spider))
        assertEquals(1, d.projected.getToughness(spider))
        val returned = d.permanentsNamed(owner, "Grizzly Bears").single()
        assertTrue(d.isTapped(returned))
        assertEquals(owner, d.projected.getController(returned))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(owner, Zone.EXILE).isEmpty())
        assertEquals(1, scions(d, p).size)
    }

    @Test
    fun coloredManaCannotPayTheTwoColorlessSymbols() {
        val d = customScenario(EldraziConfluence, "Giant Spider", "Sol Ring")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val confluence = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLUE, 3)
        d.giveColorlessMana(p, 1)

        assertNotNull(
            cast(
                d,
                p,
                confluence,
                listOf(0, 1, 2),
                listOf(listOf(ChosenTarget.Permanent(creature)), listOf(ChosenTarget.Permanent(artifact)), emptyList()),
            ).error,
        )

        assertTrue(d.getHand(p).contains(confluence))
        assertEquals(3, d.manaPool(p).blue)
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.getPermanents(d.player2).contains(creature))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
    }

    @Test
    fun threeColorlessManaCannotPayTheFourManaCost() {
        val d = customScenario(EldraziConfluence, "Giant Spider", "Sol Ring")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)

        assertNotNull(
            cast(
                d,
                p,
                confluence,
                listOf(0, 1, 2),
                listOf(listOf(ChosenTarget.Permanent(creature)), listOf(ChosenTarget.Permanent(artifact)), emptyList()),
            ).error,
        )

        assertTrue(d.getHand(p).contains(confluence))
        assertEquals(3, d.manaPool(p).colorless)
        assertTrue(d.getPermanents(d.player2).contains(creature))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
    }

    @Test
    fun mayChooseTheCreatureModeThreeTimesAndItsBoostEndsAtEndOfTurn() {
        val d = customScenario(EldraziConfluence, "Giant Spider")
        val p = d.player1
        val targets = List(3) { d.putCreatureOnBattlefield(d.player2, "Giant Spider") }
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNull(
            cast(
                d,
                p,
                confluence,
                listOf(0, 0, 0),
                targets.map { listOf(ChosenTarget.Permanent(it)) },
            ).error,
        )
        d.resolveAll()

        targets.forEach {
            assertEquals(5, d.projected.getPower(it))
            assertEquals(1, d.projected.getToughness(it))
        }
        d.passPriorityUntil(Step.END, activePlayer = p)
        targets.forEach {
            assertEquals(5, d.projected.getPower(it))
            assertEquals(1, d.projected.getToughness(it))
        }
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        targets.forEach {
            assertEquals(2, d.projected.getPower(it))
            assertEquals(4, d.projected.getToughness(it))
        }
    }

    @Test
    fun mayChooseTheBlinkModeThreeTimesAndReturnsEachPermanentTapped() {
        val d = customScenario(EldraziConfluence, "Sol Ring", "Phyrexian Arena", "Ornithopter")
        val p = d.player1
        val owner = d.player2
        val targets = listOf(
            d.putPermanentOnBattlefield(owner, "Sol Ring"),
            d.putPermanentOnBattlefield(owner, "Phyrexian Arena"),
            d.putPermanentOnBattlefield(owner, "Ornithopter"),
        )
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNull(
            cast(
                d,
                p,
                confluence,
                listOf(1, 1, 1),
                targets.map { listOf(ChosenTarget.Permanent(it)) },
            ).error,
        )
        d.resolveAll()

        listOf("Sol Ring", "Phyrexian Arena", "Ornithopter").forEach { cardName ->
            val returned = d.permanentsNamed(owner, cardName).single()
            assertTrue("$cardName returns tapped", d.isTapped(returned))
            assertTrue(d.permanentsNamed(p, cardName).isEmpty())
        }
    }

    @Test
    fun creatureModeCannotTargetANoncreaturePermanent() {
        val d = customScenario(EldraziConfluence, "Sol Ring", "Phyrexian Arena")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNotNull(
            cast(
                d,
                p,
                confluence,
                listOf(0, 1, 2),
                listOf(
                    listOf(ChosenTarget.Permanent(artifact)),
                    listOf(ChosenTarget.Permanent(enchantment)),
                    emptyList(),
                ),
            ).error,
        )

        assertTrue(d.getHand(p).contains(confluence))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getPermanents(d.player2).contains(enchantment))
    }

    @Test
    fun blinkModeCannotTargetALand() {
        val d = customScenario(EldraziConfluence, "Giant Spider", "Plains")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(p, "Giant Spider")
        val land = d.putPermanentOnBattlefield(d.player2, "Plains")
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNotNull(
            cast(
                d,
                p,
                confluence,
                listOf(0, 1, 2),
                listOf(listOf(ChosenTarget.Permanent(creature)), listOf(ChosenTarget.Permanent(land)), emptyList()),
            ).error,
        )

        assertTrue(d.getHand(p).contains(confluence))
        assertTrue(d.getPermanents(d.player2).contains(land))
    }

    @Test
    fun mayChooseTheTokenModeThreeTimesAndEachScionCanBeSacrificedForColorlessMana() {
        val d = customScenario(EldraziConfluence)
        val p = d.player1
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNull(
            cast(d, p, confluence, listOf(2, 2, 2), listOf(emptyList(), emptyList(), emptyList())).error,
        )
        d.resolveAll()

        val tokens = scions(d, p)
        assertEquals(3, tokens.size)
        tokens.forEach { token ->
            assertTrue(d.state.getEntity(token)!!.has<TokenComponent>())
            assertTrue(d.projected.getColors(token).isEmpty())
            assertEquals(1, d.projected.getPower(token))
            assertEquals(1, d.projected.getToughness(token))
        }
        tokens.forEachIndexed { index, token ->
            val sacrifice = d.abilityActions(p, token).single().action
            assertNull(d.submit(sacrifice).error)
            assertEquals(index + 1, d.manaPool(p).colorless)
            assertFalse(d.getPermanents(p).contains(token))
            assertEquals(tokens.size - index - 1, scions(d, p).size)
        }
    }

    @Test
    fun mustChooseExactlyThreeModes() {
        val d = customScenario(EldraziConfluence, "Giant Spider", "Sol Ring")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val confluence = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNotNull(
            cast(
                d,
                p,
                confluence,
                listOf(0, 1),
                listOf(listOf(ChosenTarget.Permanent(creature)), listOf(ChosenTarget.Permanent(artifact))),
            ).error,
        )

        assertTrue(d.getHand(p).contains(confluence))
        assertTrue(d.getPermanents(d.player2).contains(creature))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
    }
}
