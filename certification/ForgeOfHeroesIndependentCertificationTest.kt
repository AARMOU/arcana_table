package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ForgeOfHeroes
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ForgeOfHeroesIndependentCertificationTest {
    private val commander = CardDefinition.creature(
        name = "Fixture Forge Commander",
        manaCost = ManaCost.parse("{0}"),
        subtypes = setOf(Subtype("Human")),
        power = 1,
        toughness = 1,
        supertypes = setOf(Supertype.LEGENDARY),
    )
    private val opponentCommander = CardDefinition.creature(
        name = "Fixture Opponent Commander",
        manaCost = ManaCost.parse("{0}"),
        subtypes = setOf(Subtype("Beast")),
        power = 1,
        toughness = 1,
        supertypes = setOf(Supertype.LEGENDARY),
    )

    private fun commanderScenario(planeswalker: Boolean = false): GameTestDriver {
        val ownCommanderName = if (planeswalker) "Ugin, the Ineffable" else commander.name
        val d = customScenario(ForgeOfHeroes, "Grizzly Bears", "Ugin, the Ineffable")
        d.registerCards(listOfNotNull(commander.takeUnless { planeswalker }, opponentCommander))
        d.initMultiplayer(
            decks = listOf(Deck.of("Plains" to 49), Deck.of("Plains" to 50)),
            startingPlayer = 1,
            format = Format.Commander(),
            commanders = listOf(ownCommanderName, opponentCommander.name),
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player1)
        return d
    }

    private fun GameTestDriver.castCommander(name: String, colorlessMana: Int = 0): EntityId {
        val player = player1
        val card = state.getZone(player, Zone.COMMAND).single { getCardName(it) == name }
        giveColorlessMana(player, colorlessMana)
        assertNull(castSpell(player, card).error)
        resolveAll()
        return permanentsNamed(player, name).single()
    }

    private fun GameTestDriver.counterCount(permanent: EntityId, type: CounterType): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(type) ?: 0

    private fun GameTestDriver.activateCounter(player: EntityId, forge: EntityId, target: EntityId) =
        submit(
            ActivateAbility(
                player,
                forge,
                cardRegistry.getCard("Forge of Heroes")!!.script.activatedAbilities[1].id,
                targets = listOf(ChosenTarget.Permanent(target)),
            ),
        )

    @Test
    fun tapAbilityAddsColorlessManaAndCannotBeActivatedWhileTapped() {
        val d = customScenario(ForgeOfHeroes)
        val player = d.player1
        val forge = d.putLandOnBattlefield(player, "Forge of Heroes")

        assertEquals(0, d.manaPool(player).colorless)
        assertNull(d.activate(player, forge, index = 0).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(player).colorless)
        assertTrue(d.isTapped(forge))
        assertNotNull(d.activate(player, forge, index = 0).error)
        assertEquals(1, d.manaPool(player).colorless)
    }

    @Test
    fun thisTurnsCreatureCommanderGetsAPlusOnePlusOneCounter() {
        val d = commanderScenario()
        val player = d.player1
        val target = d.castCommander(commander.name)
        val forge = d.putLandOnBattlefield(player, "Forge of Heroes")

        assertEquals(0, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
        assertNull(d.activateCounter(player, forge, target).error)
        d.resolveAll(Choices(targets = listOf(target)))

        assertEquals(1, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
        assertTrue(d.isTapped(forge))
        assertNotNull(d.activateCounter(player, forge, target).error)
    }

    @Test
    fun thisTurnsPlaneswalkerCommanderGetsALoyaltyCounter() {
        val d = commanderScenario(planeswalker = true)
        val player = d.player1
        val target = d.castCommander("Ugin, the Ineffable", colorlessMana = 6)
        val forge = d.putLandOnBattlefield(player, "Forge of Heroes")
        val loyaltyBefore = d.counterCount(target, CounterType.LOYALTY)

        assertNull(d.activateCounter(player, forge, target).error)
        d.resolveAll(Choices(targets = listOf(target)))

        assertEquals(loyaltyBefore + 1, d.counterCount(target, CounterType.LOYALTY))
        assertTrue(d.isTapped(forge))
    }

    @Test
    fun aCreatureThatIsNotACommanderCannotBeTargeted() {
        val d = commanderScenario()
        val player = d.player1
        val forge = d.putLandOnBattlefield(player, "Forge of Heroes")
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")

        assertNotNull(d.activateCounter(player, forge, creature).error)
        assertEquals(0, d.counterCount(creature, CounterType.PLUS_ONE_PLUS_ONE))
        assertFalse(d.isTapped(forge))
    }

    @Test
    fun aCommanderThatEnteredOnAnEarlierTurnCannotBeTargeted() {
        val d = commanderScenario()
        val player = d.player1
        val target = d.castCommander(commander.name)
        val forge = d.putLandOnBattlefield(player, "Forge of Heroes")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = player)

        assertNotNull(d.activateCounter(player, forge, target).error)
        assertEquals(0, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
        assertFalse(d.isTapped(forge))
    }
}
