package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Zephyrim
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import org.junit.Assert.*
import org.junit.Test

class ZephyrimScenarioTest {
    @Test
    fun definitionMatchesOracleAndUsesNativeKeywordMechanics() {
        val expectedOracle = "Squad {2} (As an additional cost to cast this spell, you may pay {2} any number of times. " +
            "When this creature enters, create that many tokens that are copies of it.)\n" +
            "Flying, vigilance\n" +
            "Miracle {1}{W} (You may cast this card for its miracle cost when you draw it if it's the first card you drew this turn.)"
        assertEquals("{3}{W}", Zephyrim.manaCost.toString())
        assertEquals("Creature — Human Warrior", Zephyrim.typeLine.toString())
        assertEquals(3, Zephyrim.creatureStats?.basePower)
        assertEquals(3, Zephyrim.creatureStats?.baseToughness)
        assertEquals(expectedOracle, Zephyrim.oracleText)
        assertTrue(Zephyrim.hasKeyword(Keyword.FLYING))
        assertTrue(Zephyrim.hasKeyword(Keyword.VIGILANCE))
        assertTrue(Zephyrim.hasKeyword(Keyword.MIRACLE))
        assertEquals(ManaCost.parse("{1}{W}"),
            (Zephyrim.keywordAbilities.single { it is KeywordAbility.Miracle } as KeywordAbility.Miracle).cost)
    }

    @Test
    fun squadZeroOneAndTwoPaymentsMakeOnlyRealUntappedCopies() {
        for (times in 0..2) {
            val d = customScenario(Zephyrim)
            val card = d.putCardInHand(d.player1, "Zephyrim")
            d.giveMana(d.player1, Color.WHITE)
            d.giveColorlessMana(d.player1, 3 + 2 * times)

            val result = if (times == 0) {
                d.castSpell(d.player1, card)
            } else {
                d.submit(CastSpell(
                    playerId = d.player1,
                    cardId = card,
                    declaredCostSlot = ChoiceSlot.SQUAD,
                    declaredCostTimes = times,
                    paymentStrategy = PaymentStrategy.FromPool,
                ))
            }
            assertNull("Squad payments=$times: ${result.error}", result.error)
            assertEquals(0, manaRemaining(d, d.player1))
            d.resolveAll()

            val copies = d.permanentsNamed(d.player1, "Zephyrim")
            assertEquals(times + 1, copies.size)
            assertEquals(copies.size, copies.toSet().size)
            assertEquals(times, copies.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
            copies.forEach { copy ->
                val entity = d.state.getEntity(copy)!!
                assertEquals(d.player1, entity.get<ControllerComponent>()!!.playerId)
                assertEquals(d.player1, entity.get<OwnerComponent>()!!.playerId)
                assertFalse(entity.has<TappedComponent>())
                assertTrue(entity.get<CountersComponent>()?.counters.isNullOrEmpty())
                assertEquals(3, d.projected.getPower(copy))
                assertEquals(3, d.projected.getToughness(copy))
                assertTrue(d.projected.hasKeyword(copy, Keyword.FLYING))
                assertTrue(d.projected.hasKeyword(copy, Keyword.VIGILANCE))
            }
        }
    }

    @Test
    fun squadCannotBePaidWithoutEnoughMana() {
        val d = customScenario(Zephyrim)
        val card = d.putCardInHand(d.player1, "Zephyrim")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 4)

        val result = d.submitExpectFailure(CastSpell(
            playerId = d.player1,
            cardId = card,
            declaredCostSlot = ChoiceSlot.SQUAD,
            declaredCostTimes = 1,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNotNull(result.error)
        assertTrue(card in d.getHand(d.player1))
        assertEquals(5, manaRemaining(d, d.player1))
    }

    @Test
    fun squadCopiesDoNotCopyCountersTappedStatusOrDamage() {
        val d = customScenario(Zephyrim)
        val card = d.putCardInHand(d.player1, "Zephyrim")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 5)
        assertNull(d.submit(CastSpell(
            playerId = d.player1,
            cardId = card,
            declaredCostSlot = ChoiceSlot.SQUAD,
            declaredCostTimes = 1,
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.bothPass()

        d.replaceState(d.state.updateEntity(card) {
            it.with(TappedComponent)
                .with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
                .with(DamageComponent(1))
        })
        d.resolveAll()

        assertTrue(d.state.getEntity(card)!!.has<TappedComponent>())
        assertEquals(2, d.state.getEntity(card)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(1, d.state.getEntity(card)!!.get<DamageComponent>()?.amount)
        val copies = d.permanentsNamed(d.player1, "Zephyrim").filter { it != card }
        assertEquals(1, copies.size)
        val token = d.state.getEntity(copies.single())!!
        assertTrue(token.has<TokenComponent>())
        assertFalse(token.has<TappedComponent>())
        assertTrue(token.get<CountersComponent>()?.counters.isNullOrEmpty())
        assertNull(token.get<DamageComponent>())
    }

    @Test
    fun flyingAndVigilanceAffectCombatForOriginalAndSquadToken() {
        val d = customScenario(Zephyrim, "Grizzly Bears", "Air Elemental")
        val card = d.putCardInHand(d.player1, "Zephyrim")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 5)
        assertNull(d.submit(CastSpell(
            playerId = d.player1,
            cardId = card,
            declaredCostSlot = ChoiceSlot.SQUAD,
            declaredCostTimes = 1,
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
        val attackers = d.permanentsNamed(d.player1, "Zephyrim")
        assertEquals(2, attackers.size)
        attackers.forEach(d::removeSummoningSickness)
        val groundBlocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val flyingBlockers = (1..2).map { d.putCreatureOnBattlefield(d.player2, "Air Elemental") }

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, attackers, d.player2).error)
        attackers.forEach { assertFalse("Vigilance keeps $it untapped", d.isTapped(it)) }
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        attackers.forEach { attacker ->
            assertNotNull(d.declareBlockers(d.player2, mapOf(groundBlocker to listOf(attacker))).error)
        }
        assertNull(d.declareBlockers(
            d.player2,
            flyingBlockers.zip(attackers).associate { (blocker, attacker) -> blocker to listOf(attacker) },
        ).error)
    }

    @Test
    fun miracleIsNotCastableBeforeAQualifyingDrawOrOnALaterDrawThatTurn() {
        val d = customScenario(Zephyrim, "Plains", "Divination")
        val card = d.putCardInHand(d.player1, "Zephyrim")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 1)
        val illegalCast = d.submitExpectFailure(CastSpell(
            playerId = d.player1,
            cardId = card,
            paymentStrategy = PaymentStrategy.FromPool,
            alternativeCostType = AlternativeCostType.MIRACLE,
        ))
        assertNotNull(illegalCast.error)
        assertTrue(card in d.getHand(d.player1))

        val zephyrim = d.putCardOnTopOfLibrary(d.player2, "Zephyrim")
        d.putCardOnTopOfLibrary(d.player2, "Plains")
        d.passPriorityUntil(Step.DRAW, activePlayer = d.player2)
        assertNull(d.state.pendingDecision)
        assertTrue(d.events.none { it is CardRevealedForMiracleEvent && it.cardName == "Zephyrim" })

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val divination = d.putCardInHand(d.player2, "Divination")
        d.giveMana(d.player2, Color.BLUE)
        d.giveColorlessMana(d.player2, 2)
        assertNull(d.castSpell(d.player2, divination).error)
        d.resolveAll()
        assertTrue("A later draw must not offer Miracle",
            d.events.none { it is CardRevealedForMiracleEvent && it.cardName == "Zephyrim" })
        assertTrue(zephyrim in d.getHand(d.player2))
    }

    @Test
    fun miracleMayBeCastOnAnOpponentsTurnAndStillPaySquad() {
        for (times in 1..2) {
            val d = customScenario(Zephyrim)
            d.registerCard(OpponentDraw)
            val zephyrim = d.putCardOnTopOfLibrary(d.player1, "Zephyrim")
            val drawSpell = d.putCardInHand(d.player2, OpponentDraw.name)
            d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
            d.giveMana(d.player2, Color.BLUE)
            d.giveMana(d.player1, Color.WHITE)
            d.giveColorlessMana(d.player1, 1 + 2 * times)

            assertNull(d.castSpell(d.player2, drawSpell).error)
            d.bothPass()
            assertTrue(d.events.any {
                it is CardRevealedForMiracleEvent && it.cardName == "Zephyrim"
            })
            val offer = d.state.pendingDecision as? YesNoDecision
            assertNotNull("First draw on the opponent's turn should offer Miracle", offer)
            assertNull(d.submitYesNo(d.player1, true).error)

            val cast = d.submit(CastSpell(
                playerId = d.player1,
                cardId = zephyrim,
                declaredCostSlot = ChoiceSlot.SQUAD,
                declaredCostTimes = times,
                paymentStrategy = PaymentStrategy.FromPool,
                alternativeCostType = AlternativeCostType.MIRACLE,
            ))
            assertNull("Miracle plus Squad payments=$times: ${cast.error}", cast.error)
            assertEquals(0, manaRemaining(d, d.player1))
            d.resolveAll()
            val copies = d.permanentsNamed(d.player1, "Zephyrim")
            assertEquals(times + 1, copies.size)
            assertEquals(times, copies.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
            assertTrue(d.projected.hasKeyword(copies.first(), Keyword.FLYING))
            assertTrue(d.projected.hasKeyword(copies.first(), Keyword.VIGILANCE))
        }
    }

    private fun manaRemaining(d: GameTestDriver, player: EntityId): Int {
        val mana = d.state.getEntity(player)?.get<ManaPoolComponent>() ?: return 0
        return mana.white + mana.blue + mana.black + mana.red + mana.green + mana.colorless
    }

    private val OpponentDraw: CardDefinition = card("Opponent Draw Test Spell") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell { effect = Effects.DrawCards(1, EffectTarget.PlayerRef(Player.AnOpponent)) }
    }
}
