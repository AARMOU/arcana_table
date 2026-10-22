package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ForgeOfHeroes
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class ForgeOfHeroesScenarioTest {
    @Test fun targetMustBeCommanderThatEnteredThisTurnThenGetsPlusOneCounter() {
        val d = customScenario(ForgeOfHeroes, "Grizzly Bears")
        val p = d.player1
        d.replaceState(d.state.copy(format = Format.Commander()))
        val forge = d.putPermanentOnBattlefield(p, "Forge of Heroes")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Forge of Heroes")!!.script.activatedAbilities[1]
        val action = ActivateAbility(p, forge, ability.id, targets = listOf(ChosenTarget.Permanent(bear)))
        assertNotNull("ordinary creature isn't a commander", d.submit(action).error)
        d.replaceState(d.state.updateEntity(bear) { c ->
            c.with(CommanderComponent(ownerId = p)).with(EnteredThisTurnComponent)
        })
        assertNull(d.submit(action).error)
        d.resolveAll()
        assertEquals(3, d.projected.getPower(bear))
        assertEquals(3, d.projected.getToughness(bear))
        assertTrue(d.isTapped(forge))
    }

    @Test fun planeswalkerCommanderGetsLoyaltyInsteadOfCreatureCounters() {
        val d = customScenario(ForgeOfHeroes, "Jace, the Mind Sculptor")
        val p = d.player1
        d.replaceState(d.state.copy(format = Format.Commander()))
        val forge = d.putPermanentOnBattlefield(p, "Forge of Heroes")
        val walker = d.putPermanentOnBattlefield(p, "Jace, the Mind Sculptor")
        // Direct-board test helper bypasses planeswalker entry, including starting
        // loyalty. Set Jace's printed three counters so he survives state-based actions.
        d.replaceState(d.state.updateEntity(walker) { c ->
            c.with(CommanderComponent(ownerId = p))
                .with(EnteredThisTurnComponent)
                .with(com.wingedsheep.engine.state.components.battlefield.CountersComponent()
                    .withAdded(CounterType.LOYALTY, 3))
        })
        val before = d.state.getEntity(walker)!!
            .get<com.wingedsheep.engine.state.components.battlefield.CountersComponent>()
            ?.getCount(CounterType.LOYALTY) ?: 0
        assertEquals("initial Jace loyalty", 3, before)
        val ability = d.cardRegistry.getCard("Forge of Heroes")!!.script.activatedAbilities[1]
        assertNull(d.submit(ActivateAbility(p, forge, ability.id,
            targets = listOf(ChosenTarget.Permanent(walker)))).error)
        d.resolveAll()
        val after = d.state.getEntity(walker)!!
            .get<com.wingedsheep.engine.state.components.battlefield.CountersComponent>()
            ?.getCount(CounterType.LOYALTY) ?: 0
        assertEquals(before + 1, after)
        assertEquals(0, d.state.getEntity(walker)!!
            .get<com.wingedsheep.engine.state.components.battlefield.CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
    }
}
