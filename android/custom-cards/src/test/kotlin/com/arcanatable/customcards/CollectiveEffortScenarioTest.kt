package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CollectiveEffort
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import org.junit.Assert.*
import org.junit.Test

class CollectiveEffortScenarioTest {
    @Test
    fun allModesResolveAndEscalateTapsOneCreaturePerExtraMode() {
        val d = customScenario(CollectiveEffort, "Grizzly Bears", "Pacifism")
        val fourPower = card("Four Power Creature") {
            manaCost = "{3}{G}"
            typeLine = "Creature — Beast"
            power = 4
            toughness = 4
        }
        d.registerCard(fourPower)

        val ownBear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val ownFourPower = d.putCreatureOnBattlefield(d.player1, "Four Power Creature")
        val targetCreature = d.putCreatureOnBattlefield(d.player2, "Four Power Creature")
        val otherCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Pacifism")
        val spell = d.putCardInHand(d.player1, "Collective Effort")
        d.giveMana(d.player1, Color.WHITE, 2)
        d.giveColorlessMana(d.player1, 1)

        val result = d.submit(
            CastSpell(
                playerId = d.player1,
                cardId = spell,
                chosenModes = listOf(0, 1, 2),
                modeTargetsOrdered = listOf(
                    listOf(ChosenTarget.Permanent(targetCreature)),
                    listOf(ChosenTarget.Permanent(enchantment)),
                    listOf(ChosenTarget.Player(d.player2)),
                ),
                additionalCostPayment = AdditionalCostPayment(tappedPermanents = listOf(ownBear, ownFourPower)),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNull(result.error)
        d.resolveAll()

        assertTrue(d.isTapped(ownBear))
        assertTrue(d.isTapped(ownFourPower))
        assertFalse(targetCreature in d.getCreatures(d.player2))
        assertTrue(otherCreature in d.getCreatures(d.player2))
        assertTrue(d.permanentsNamed(d.player2, "Pacifism").isEmpty())
        assertEquals(1, d.state.getEntity(otherCreature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(0, d.state.getEntity(ownBear)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
    }

    @Test
    fun creatureAndEnchantmentModesRejectTargetsOfTheWrongTypeOrPower() {
        val d = customScenario(CollectiveEffort, "Grizzly Bears", "Sol Ring")
        val lowPowerCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(d.player1, "Collective Effort")
        d.giveMana(d.player1, Color.WHITE, 2)
        d.giveColorlessMana(d.player1, 1)

        val lowPowerTarget = d.submit(
            CastSpell(
                playerId = d.player1,
                cardId = spell,
                chosenModes = listOf(0),
                modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(lowPowerCreature))),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNotNull("A creature with power less than 4 is not a legal target", lowPowerTarget.error)

        val wrongTypeTarget = d.submit(
            CastSpell(
                playerId = d.player1,
                cardId = spell,
                chosenModes = listOf(1),
                modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(artifact))),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNotNull("An artifact that is not an enchantment is not a legal target", wrongTypeTarget.error)
    }

    @Test
    fun escalationIsNotRequiredForOneModeAndCannotBePaidWithOnlyTappedCreatures() {
        val d = customScenario(CollectiveEffort, "Grizzly Bears", "Craw Wurm", "Pacifism")
        val tappedCreature = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.tapPermanent(tappedCreature)
        val targetCreature = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Pacifism")
        val spell = d.putCardInHand(d.player1, "Collective Effort")
        d.giveMana(d.player1, Color.WHITE, 2)
        d.giveColorlessMana(d.player1, 1)

        val escalatedWithoutUntappedCreature = d.submit(
            CastSpell(
                playerId = d.player1,
                cardId = spell,
                chosenModes = listOf(0, 1),
                modeTargetsOrdered = listOf(
                    listOf(ChosenTarget.Permanent(targetCreature)),
                    listOf(ChosenTarget.Permanent(enchantment)),
                ),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNotNull("Choosing an extra mode requires tapping an untapped creature", escalatedWithoutUntappedCreature.error)

        val oneMode = d.submit(
            CastSpell(
                playerId = d.player1,
                cardId = spell,
                chosenModes = listOf(0),
                modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(targetCreature))),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNull(oneMode.error)
        d.resolveAll()

        assertTrue(d.isTapped(tappedCreature))
        assertFalse(targetCreature in d.getCreatures(d.player2))
    }
}
