package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldritchImmunity
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*
import org.junit.Test

class EldritchImmunityScenarioTest {
    private fun cast(d: GameTestDriver, targets: List<EntityId> = emptyList(), overload: Boolean = false) =
        d.submit(CastSpell(
            playerId = d.player1,
            cardId = d.putCardInHand(d.player1, "Eldritch Immunity"),
            targets = targets.map { ChosenTarget.Permanent(it) },
            paymentStrategy = PaymentStrategy.FromPool,
            useAlternativeCost = overload,
            alternativeCostType = if (overload) AlternativeCostType.OVERLOAD else null,
        ))

    private fun assertProtection(d: GameTestDriver, creature: EntityId, expected: Boolean = true) {
        val granted = d.projected.getKeywords(creature).toString()
        for (color in listOf("WHITE", "BLUE", "BLACK", "RED", "GREEN")) {
            assertEquals("protection from $color on $creature", expected, granted.contains("PROTECTION_FROM_$color"))
        }
        assertFalse("Colorless is not a sixth color", granted.contains("PROTECTION_FROM_COLORLESS"))
    }

    @Test fun normalCostProtectsExactlyTheChosenCreatureFromAllFiveColors() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears", "Craw Wurm")
        val chosen = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val other = d.putCreatureOnBattlefield(d.player1, "Craw Wurm")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 1)
        assertNull(cast(d, listOf(chosen)).error)
        d.resolveStack()
        assertProtection(d, chosen)
        assertProtection(d, other, false)
        assertProtection(d, enemy, false)
    }

    @Test fun normalCostRequiresAControlledCreatureTarget() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val land = d.putLandOnBattlefield(d.player1, "Plains")
        d.giveColorlessMana(d.player1, 3)
        assertNotNull("opponent creature is illegal", cast(d, listOf(enemy)).error)
        assertNotNull("own noncreature is illegal", cast(d, listOf(land)).error)
        assertNotNull("normal cast cannot omit its target", cast(d).error)
    }

    @Test fun normalCostCannotBePaidWithColoredMana() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN, 5)
        assertNotNull(cast(d, listOf(bear)).error)
    }

    @Test fun overloadProtectsAllOurCreaturesIncludingShroudButNotOpponentsOrLands() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        d.registerCard(card("Immunity Shroud Probe") {
            manaCost = "{2}"
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
            keywords(Keyword.SHROUD)
        })
        val shrouded = d.putCreatureOnBattlefield(d.player1, "Immunity Shroud Probe")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val land = d.putLandOnBattlefield(d.player1, "Plains")
        d.giveMana(d.player1, Color.BLUE, 4)
        d.giveColorlessMana(d.player1, 1)
        assertNull(cast(d, overload = true).error)
        d.resolveStack()
        assertProtection(d, bear)
        assertProtection(d, shrouded)
        assertProtection(d, enemy, false)
        assertProtection(d, land, false)
    }

    @Test fun shroudPreventsTheNormalTargetedCast() {
        val d = customScenario(EldritchImmunity)
        d.registerCard(card("Untargetable Immunity Probe") {
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
            keywords(Keyword.SHROUD)
        })
        val creature = d.putCreatureOnBattlefield(d.player1, "Untargetable Immunity Probe")
        d.giveColorlessMana(d.player1, 1)
        assertNotNull(cast(d, listOf(creature)).error)
    }

    @Test fun overloadRejectsExplicitTargetsAndRequiresAllFiveMana() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 1)
        assertNotNull("overload cannot be paid at the printed cost", cast(d, overload = true).error)
        d.giveColorlessMana(d.player1, 4)
        assertNotNull("overload cannot target", cast(d, listOf(bear), overload = true).error)
        assertNull(cast(d, overload = true).error)
        d.resolveStack()
        assertProtection(d, bear)
    }

    @Test fun overloadStillRequiresOneTrueColorlessMana() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.RED, 5)
        assertNotNull(cast(d, overload = true).error)
    }

    @Test fun overloadCanBeCastWithNoCreaturesAndDoesNotAffectLaterEntrants() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 5)
        assertNull(cast(d, overload = true).error)
        d.resolveStack()
        val later = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        assertProtection(d, later, false)
    }

    @Test fun protectionExpiresAtEndOfTurn() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 1)
        assertNull(cast(d, listOf(bear)).error)
        d.resolveStack()
        assertProtection(d, bear)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertProtection(d, bear, false)
    }

    @Test fun redDamageIsPreventedButColorlessDamageStillKills() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears", "Pyroclasm")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 1)
        assertNull(cast(d, listOf(bear)).error)
        d.resolveStack()
        d.giveMana(d.player1, Color.RED, 2)
        val pyroclasm = d.putCardInHand(d.player1, "Pyroclasm")
        assertNull(d.castSpell(d.player1, pyroclasm).error)
        d.resolveStack()
        assertTrue("red non-targeted damage prevented", bear in d.getCreatures(d.player1))
        assertFalse("unprotected opponent creature takes damage", enemy in d.getCreatures(d.player2))
        d.registerCard(card("Colorless Damage Probe") {
            manaCost = "{0}"
            typeLine = "Instant"
            spell {
                val creature = target(TargetFilter.Creature)
                effect = Effects.DealDamage(3, creature)
            }
        })
        val probe = d.putCardInHand(d.player1, "Colorless Damage Probe")
        assertNull("colors do not stop colorless targeting", d.castSpell(d.player1, probe, listOf(bear)).error)
        d.resolveStack()
        assertFalse("colorless damage is not prevented", bear in d.getCreatures(d.player1))
    }
}
