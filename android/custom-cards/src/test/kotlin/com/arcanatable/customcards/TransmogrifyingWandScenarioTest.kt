package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TransmogrifyingWand
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import org.junit.Assert.*
import org.junit.Test

class TransmogrifyingWandScenarioTest {
    @Test
    fun destroysTargetAndGivesItsControllerTheOxWhileSpendingOneCharge() {
        val d = customScenario(TransmogrifyingWand, "Grizzly Bears")
        val p = d.player1
        val wandCard = d.putCardInHand(p, "Transmogrifying Wand")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, wandCard).error)
        d.resolveAll()

        val wand = d.findPermanent(p, "Transmogrifying Wand")!!
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Transmogrifying Wand")!!.script.activatedAbilities.single()
        d.giveColorlessMana(p, 1)
        assertNull(
            d.submit(
                ActivateAbility(p, wand, ability.id, targets = listOf(ChosenTarget.Permanent(victim))),
            ).error,
        )
        d.resolveAll()

        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        val ox = d.permanentsNamed(d.player2, "Ox").single()
        assertEquals(2, d.projected.getPower(ox))
        assertEquals(4, d.projected.getToughness(ox))
        assertTrue(d.permanentsNamed(p, "Ox").isEmpty())
        assertEquals(
            2,
            d.state.getEntity(wand)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE),
        )
    }

    @Test
    fun oxGoesToTheCreatureControllerRememberedWhenItWasDestroyed() {
        val d = customScenario(TransmogrifyingWand, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.registerCard(card("Borrowed Control") {
            manaCost = "{U}"
            typeLine = "Instant"
            spell {
                val creature = target(com.wingedsheep.sdk.scripting.filters.unified.TargetFilter.Creature)
                effect = Effects.GainControl(creature, com.wingedsheep.sdk.scripting.Duration.EndOfTurn)
            }
        })

        val victim = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val wandCard = d.putCardInHand(p, "Transmogrifying Wand")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, wandCard).error)
        d.resolveAll()

        val wand = d.findPermanent(p, "Transmogrifying Wand")!!
        val wandAbility = d.cardRegistry.getCard("Transmogrifying Wand")!!.script.activatedAbilities.single()
        d.giveColorlessMana(p, 1)
        assertNull(
            d.submit(
                ActivateAbility(p, wand, wandAbility.id, targets = listOf(ChosenTarget.Permanent(victim))),
            ).error,
        )
        assertNull(d.passPriority(p).error)

        val controlSpell = d.putCardInHand(opponent, "Borrowed Control")
        d.giveMana(opponent, Color.BLUE)
        assertNull(d.castSpell(opponent, controlSpell, listOf(victim)).error)
        d.resolveAll()

        assertEquals(opponent, d.getController(victim))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
        assertEquals(1, d.permanentsNamed(opponent, "Ox").size)
        assertTrue(d.permanentsNamed(p, "Ox").isEmpty())
    }

    @Test
    fun indestructibleTargetSurvivesButItsControllerStillCreatesAnOx() {
        val d = customScenario(TransmogrifyingWand, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.registerCard(card("Indestructible Test Beast") {
            typeLine = "Creature — Beast"
            power = 3
            toughness = 3
            keywords(Keyword.INDESTRUCTIBLE)
        })
        val wandCard = d.putCardInHand(p, "Transmogrifying Wand")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, wandCard).error)
        d.resolveAll()

        val wand = d.findPermanent(p, "Transmogrifying Wand")!!
        val victim = d.putCreatureOnBattlefield(opponent, "Indestructible Test Beast")
        val wandAbility = d.cardRegistry.getCard("Transmogrifying Wand")!!.script.activatedAbilities.single()
        d.giveColorlessMana(p, 1)
        assertNull(
            d.submit(
                ActivateAbility(p, wand, wandAbility.id, targets = listOf(ChosenTarget.Permanent(victim))),
            ).error,
        )
        d.resolveAll()

        assertEquals(victim, d.findPermanent(opponent, "Indestructible Test Beast"))
        assertEquals(1, d.permanentsNamed(opponent, "Ox").size)
        assertTrue(d.permanentsNamed(p, "Ox").isEmpty())
        assertEquals(
            2,
            d.state.getEntity(wand)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE),
        )
    }
}
