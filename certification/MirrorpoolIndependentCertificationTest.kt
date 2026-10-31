package com.arcanatable.customcards

// Independent scenarios from Mirrorpool's Oracle text:
// "This land enters tapped. {T}: Add {C}. {2}{C}, {T}, Sacrifice this land: Copy target
// instant or sorcery spell you control. You may choose new targets for the copy.
// {4}{C}, {T}, Sacrifice this land: Create a token that's a copy of target creature you control."
import com.arcanatable.customcards.cards.Mirrorpool
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MirrorpoolIndependentCertificationTest {
    private fun GameTestDriver.playMirrorpool(player: EntityId): EntityId {
        val mirrorpool = putCardInHand(player, "Mirrorpool")
        assertNull(playLand(player, mirrorpool).error)
        resolveAll()
        return mirrorpool
    }

    private fun GameTestDriver.activateTargetingAbility(
        player: EntityId,
        mirrorpool: EntityId,
        abilityIndex: Int,
        target: ChosenTarget,
    ) = submit(ActivateAbility(
        player,
        mirrorpool,
        cardRegistry.getCard("Mirrorpool")!!.script.activatedAbilities[abilityIndex].id,
        targets = listOf(target),
    ))

    @Test
    fun entersTappedAndItsTapAbilityAddsOneColorlessManaAfterUntapping() {
        val d = customScenario(Mirrorpool)
        val p = d.player1
        val mirrorpool = d.playMirrorpool(p)

        assertTrue(d.isTapped(mirrorpool))
        assertNotNull(d.activate(p, mirrorpool, index = 0).error)
        assertEquals(0, d.manaPool(p).colorless)

        d.untapPermanent(mirrorpool)
        assertNull(d.activate(p, mirrorpool, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(mirrorpool))
    }

    @Test
    fun copiesYourInstantAndCanChooseANewTarget() {
        val d = customScenario(Mirrorpool, "Shock")
        val p = d.player1
        val mirrorpool = d.playMirrorpool(p)
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        val original = d.getTopOfStack()!!
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.activateTargetingAbility(
            p, mirrorpool, 1, ChosenTarget.Spell(original),
        ).error)
        assertTrue(d.isTapped(mirrorpool))
        assertFalse(d.getGraveyardCardNames(p).contains("Mirrorpool"))

        d.untapPermanent(mirrorpool)
        assertNull(d.activateTargetingAbility(
            p, mirrorpool, 1, ChosenTarget.Spell(original),
        ).error)
        assertTrue(d.getGraveyardCardNames(p).contains("Mirrorpool"))
        d.resolveAll(Choices(yes = true, targets = listOf(p)))

        assertEquals(18, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(d.player2))
    }

    @Test
    fun copiesYourSorcerySpell() {
        val d = customScenario(Mirrorpool, "Divination")
        val p = d.player1
        val mirrorpool = d.playMirrorpool(p)
        d.untapPermanent(mirrorpool)
        val divination = d.putCardInHand(p, "Divination")
        val handBeforeCast = d.getHandSize(p)
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, divination).error)
        val original = d.getTopOfStack()!!
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNull(d.activateTargetingAbility(
            p, mirrorpool, 1, ChosenTarget.Spell(original),
        ).error)
        assertTrue(d.getGraveyardCardNames(p).contains("Mirrorpool"))
        d.resolveAll()

        assertEquals(handBeforeCast + 3, d.getHandSize(p))
    }

    @Test
    fun theInstantOrSorceryCopyCostRequiresItsColorlessSymbol() {
        val d = customScenario(Mirrorpool, "Shock")
        val p = d.player1
        val mirrorpool = d.playMirrorpool(p)
        d.untapPermanent(mirrorpool)
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        val original = d.getTopOfStack()!!
        repeat(3) { d.giveMana(p, Color.GREEN) }

        assertNotNull(d.activateTargetingAbility(
            p, mirrorpool, 1, ChosenTarget.Spell(original),
        ).error)

        assertEquals(3, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
        assertFalse(d.isTapped(mirrorpool))
        assertFalse(d.getGraveyardCardNames(p).contains("Mirrorpool"))
    }

    @Test
    fun cannotCopyAnOpponentControlledSpell() {
        val d = customScenario(Mirrorpool, "Shock")
        val p = d.player1
        val opponent = d.player2
        val mirrorpool = d.playMirrorpool(p)
        d.untapPermanent(mirrorpool)
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val shock = d.putCardInHand(opponent, "Shock")
        d.giveMana(opponent, Color.RED)
        assertNull(d.castSpell(opponent, shock, listOf(p)).error)
        val opponentsSpell = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        assertNotNull(d.activateTargetingAbility(
            p, mirrorpool, 1, ChosenTarget.Spell(opponentsSpell),
        ).error)

        assertFalse(d.isTapped(mirrorpool))
        assertFalse(d.getGraveyardCardNames(p).contains("Mirrorpool"))
    }

    @Test
    fun cannotCopyYourCreatureSpell() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        val p = d.player1
        val mirrorpool = d.playMirrorpool(p)
        d.untapPermanent(mirrorpool)
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        val creatureSpell = d.getTopOfStack()!!
        repeat(2) { d.giveMana(p, Color.GREEN) }
        d.giveColorlessMana(p, 1)

        assertNotNull(d.activateTargetingAbility(
            p, mirrorpool, 1, ChosenTarget.Spell(creatureSpell),
        ).error)

        assertFalse(d.isTapped(mirrorpool))
        assertFalse(d.getGraveyardCardNames(p).contains("Mirrorpool"))
    }

    @Test
    fun copiesYourCreatureAsATokenAtOpponentPriority() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val mirrorpool = d.playMirrorpool(p)
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        repeat(4) { d.giveMana(p, Color.GREEN) }
        d.giveColorlessMana(p, 1)

        assertNotNull(d.activateTargetingAbility(
            p, mirrorpool, 2, ChosenTarget.Permanent(bears),
        ).error)
        assertTrue(d.isTapped(mirrorpool))
        d.untapPermanent(mirrorpool)
        assertNull(d.activateTargetingAbility(
            p, mirrorpool, 2, ChosenTarget.Permanent(bears),
        ).error)
        assertTrue(d.getGraveyardCardNames(p).contains("Mirrorpool"))
        d.resolveAll()

        val creatures = d.permanentsNamed(p, "Grizzly Bears")
        assertEquals(2, creatures.size)
        assertTrue(creatures.contains(bears))
        val copy = creatures.single { it != bears }
        assertEquals(2, d.projected.getPower(copy))
        assertEquals(2, d.projected.getToughness(copy))
    }

    @Test
    fun theCreatureCopyCostRequiresItsColorlessSymbol() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        val p = d.player1
        val mirrorpool = d.playMirrorpool(p)
        d.untapPermanent(mirrorpool)
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        repeat(4) { d.giveMana(p, Color.GREEN) }

        assertNotNull(d.activateTargetingAbility(
            p, mirrorpool, 2, ChosenTarget.Permanent(bears),
        ).error)

        assertEquals(4, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
        assertFalse(d.isTapped(mirrorpool))
        assertFalse(d.getGraveyardCardNames(p).contains("Mirrorpool"))
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
    }

    @Test
    fun cannotCopyAnOpponentControlledCreature() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        val p = d.player1
        val mirrorpool = d.playMirrorpool(p)
        d.untapPermanent(mirrorpool)
        val opponentBears = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        repeat(4) { d.giveMana(p, Color.GREEN) }
        d.giveColorlessMana(p, 1)

        assertNotNull(d.activateTargetingAbility(
            p, mirrorpool, 2, ChosenTarget.Permanent(opponentBears),
        ).error)

        assertFalse(d.isTapped(mirrorpool))
        assertFalse(d.getGraveyardCardNames(p).contains("Mirrorpool"))
        assertEquals(1, d.permanentsNamed(d.player2, "Grizzly Bears").size)
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
    }
}
