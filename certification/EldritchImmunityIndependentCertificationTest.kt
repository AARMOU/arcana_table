package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldritchImmunity
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class EldritchImmunityIndependentCertificationTest {
    private fun assertColoredSpellsCannotTarget(
        d: GameTestDriver,
        caster: com.wingedsheep.sdk.model.EntityId,
        targets: List<com.wingedsheep.sdk.model.EntityId>,
    ) {
        val spells = listOf(
            Triple("Swords to Plowshares", Color.WHITE, 1),
            Triple("Unsummon", Color.BLUE, 1),
            Triple("Murder", Color.BLACK, 3),
            Triple("Shock", Color.RED, 1),
            Triple("Giant Growth", Color.GREEN, 1),
        )

        targets.forEach { target ->
            spells.forEach { (name, color, amount) ->
                val spell = d.putCardInHand(caster, name)
                d.giveMana(caster, color, amount)
                assertNotNull(
                    "$name cannot target a creature with protection from each color",
                    d.castSpell(caster, spell, listOf(target)).error,
                )
                assertTrue(d.getHand(caster).contains(spell))
            }
        }
    }

    @Test
    fun normalCastProtectsItsControllerControlledTargetFromEachColorUntilEndOfTurn() {
        val d = customScenario(
            EldritchImmunity,
            "Grizzly Bears",
            "Swords to Plowshares",
            "Unsummon",
            "Murder",
            "Shock",
            "Giant Growth",
        )
        val player = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spell = d.putCardInHand(player, "Eldritch Immunity")
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpellWithTargets(player, spell, listOf(ChosenTarget.Permanent(bear))).error)
        assertEquals(0, d.manaPool(player).colorless)
        d.resolveAll()
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Eldritch Immunity"))

        assertNull(d.passPriority(player).error)
        assertColoredSpellsCannotTarget(d, opponent, listOf(bear))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val afterTurn = d.putCardInHand(opponent, "Swords to Plowshares")
        d.giveMana(opponent, Color.WHITE)
        assertNull(d.castSpell(opponent, afterTurn, listOf(bear)).error)
    }

    @Test
    fun normalCastCannotTargetAnOpponentCreatureOrANoncreature() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears", "Plains")
        val player = d.player1
        val opponentBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val plains = d.putPermanentOnBattlefield(player, "Plains")
        val spell = d.putCardInHand(player, "Eldritch Immunity")
        d.giveColorlessMana(player, 1)

        assertNotNull(d.castSpellWithTargets(player, spell, listOf(ChosenTarget.Permanent(opponentBear))).error)
        assertNotNull(d.castSpellWithTargets(player, spell, listOf(ChosenTarget.Permanent(plains))).error)
        assertTrue(d.getHand(player).contains(spell))
        assertEquals(1, d.manaPool(player).colorless)
    }

    @Test
    fun coloredManaCannotPayTheRequiredColorlessSymbol() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spell = d.putCardInHand(player, "Eldritch Immunity")
        d.giveMana(player, Color.WHITE)

        assertNotNull(d.castSpellWithTargets(player, spell, listOf(ChosenTarget.Permanent(bear))).error)
        assertTrue(d.getHand(player).contains(spell))
        assertEquals(1, d.manaPool(player).white)
        assertEquals(0, d.manaPool(player).colorless)
    }

    @Test
    fun overloadCostsFourGenericAndOneColorlessAndProtectsEachCreature() {
        val d = customScenario(
            EldritchImmunity,
            "Grizzly Bears",
            "Swords to Plowshares",
            "Unsummon",
            "Murder",
            "Shock",
            "Giant Growth",
        )
        val player = d.player1
        val opponent = d.player2
        val playerBear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val anotherPlayerBear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opponentBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(player, "Eldritch Immunity")
        d.giveColorlessMana(player, 5)

        assertNull(
            d.submit(
                CastSpell(
                    playerId = player,
                    cardId = spell,
                    paymentStrategy = PaymentStrategy.FromPool,
                    useAlternativeCost = true,
                ),
            ).error,
        )
        assertEquals(0, d.manaPool(player).colorless)
        d.resolveAll()
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Eldritch Immunity"))

        assertNull(d.passPriority(player).error)
        assertColoredSpellsCannotTarget(d, opponent, listOf(playerBear, anotherPlayerBear))

        val ownTargetSpell = d.putCardInHand(opponent, "Swords to Plowshares")
        d.giveMana(opponent, Color.WHITE)
        assertNull(d.castSpell(opponent, ownTargetSpell, listOf(opponentBear)).error)
        d.resolveAll()
        assertTrue(d.zoneNames(opponent, Zone.EXILE).contains("Grizzly Bears"))
    }

    @Test
    fun fourColorlessManaCannotPayTheOverloadCost() {
        val d = customScenario(EldritchImmunity, "Grizzly Bears")
        val player = d.player1
        val spell = d.putCardInHand(player, "Eldritch Immunity")
        d.giveColorlessMana(player, 4)

        assertNotNull(
            d.submit(
                CastSpell(
                    playerId = player,
                    cardId = spell,
                    paymentStrategy = PaymentStrategy.FromPool,
                    useAlternativeCost = true,
                ),
            ).error,
        )
        assertTrue(d.getHand(player).contains(spell))
        assertEquals(4, d.manaPool(player).colorless)
    }
}
