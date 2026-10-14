package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    @Test
    fun detectsAsEntersCreatureTypeChoicesButIgnoresFaceDownCards() {
        val choosingPermanent = GameCard(1, "Type Picker", "Artifact", androidx.compose.ui.graphics.Color.Black, oracleText = "As this enters, choose a creature type.")
        assertTrue(needsCreatureTypeChoice(choosingPermanent))
        assertTrue(needsCreatureTypeChoice(choosingPermanent.copy(oracleText = "As this enters the battlefield, choose a creature type.")))
        assertFalse(needsCreatureTypeChoice(choosingPermanent.copy(isFaceDown = true)))
        assertFalse(needsCreatureTypeChoice(choosingPermanent.copy(oracleText = "Choose a creature type.")))
    }

    @Test
    fun onlySpellResolutionMovesItsCardOutOfTheStack() {
        assertEquals(true, stackResolutionMovesCardToZone("SPELL"))
        assertEquals(true, stackResolutionMovesCardToZone("COMMANDER"))
        assertEquals(false, stackResolutionMovesCardToZone("ACTIVATED ABILITY"))
        assertEquals(false, stackResolutionMovesCardToZone("LOYALTY ABILITY"))
        assertEquals(false, stackResolutionMovesCardToZone("TRIGGER"))
    }

    @Test
    fun estimatesRequiredAndOptionalTargetCounts() {
        assertEquals(TargetCountRange(2, 2), parseTargetCountRange("Destroy target creature and exile target artifact."))
        assertEquals(TargetCountRange(0, 3), parseTargetCountRange("Choose up to three target creatures."))
        assertEquals(TargetCountRange(0, 99), parseTargetCountRange("Divide among any number of target creatures."))
        assertNull(parseTargetCountRange("Draw a card."))
    }

    @Test
    fun targetPickerCandidatesIncludeGameObjectsAndRespectHiddenHands() {
        val ownCreature = GameCard(11, "Own Creature", "Creature", androidx.compose.ui.graphics.Color.Black)
        val hiddenCard = GameCard(12, "Hidden Card", "Creature", androidx.compose.ui.graphics.Color.Black)
        val graveCard = GameCard(13, "Grave Card", "Instant", androidx.compose.ui.graphics.Color.Black)
        val otherHand = GameCard(14, "Opponent Hand", "Sorcery", androidx.compose.ui.graphics.Color.Black)
        val source = StackAction(id = "source", title = "Source spell", controllerId = "p1", card = GameCard(20, "Source", "Instant", androidx.compose.ui.graphics.Color.Black, oracleText = "Target creature."))
        val counterspell = StackAction(id = "response", title = "Counterspell", controllerId = "p2", kind = "SPELL", card = GameCard(21, "Counterspell", "Instant", androidx.compose.ui.graphics.Color.Black))
        val players = listOf(
            TablePlayer("P1", id = "p1", hand = listOf(hiddenCard), battlefield = listOf(ownCreature)),
            TablePlayer("P2", id = "p2", hand = listOf(otherHand), graveyard = listOf(graveCard))
        )
        val candidates = buildTargetCandidates(players, listOf(source, counterspell), "source", "p1")
        assertEquals(true, candidates.any { it.key == "player:p1" })
        assertEquals(true, candidates.any { it.key == "battlefield:p1:11" })
        assertEquals(true, candidates.any { it.key == "graveyard:p2:13" })
        assertEquals(true, candidates.any { it.key == "stack:response" })
        assertEquals(false, candidates.any { it.key == "stack:source" })
        assertEquals(false, candidates.any { it.key == "hand:p2:14" })
    }

    @Test
    fun summarizesRepeatedMainDeckNamesAcrossPrintingRowsButOmitsCommander() {
        val repeated = repeatedDeckEntries(listOf(
            DeckEntry("Ancient Tomb", 1, setCode = "UMA"),
            DeckEntry("ancient tomb", 1, setCode = "ZNE"),
            DeckEntry("Sol Ring", 1, commander = true),
            DeckEntry("Island", 5)
        ))
        assertEquals(listOf(RepeatedDeckEntry("Ancient Tomb", 2), RepeatedDeckEntry("Island", 5)), repeated)
    }

    @Test
    fun parsesUnconditionalLifeDrawAndTokenEffects() {
        val effects = parseAutomaticResolutionEffects(
            "You gain 3 life. Draw two cards. Create two 1/1 white Soldier creature tokens."
        )
        assertEquals(3, effects.lifeGain)
        assertEquals(2, effects.cardsDrawn)
        assertEquals(1, effects.tokens.size)
        assertEquals(2, effects.tokens.single().count)
        assertEquals("Soldier", effects.tokens.single().name)
        assertEquals("1", effects.tokens.single().power)
        assertEquals("1", effects.tokens.single().toughness)
    }

    @Test
    fun leavesTargetedOrConditionalCardEffectsManual() {
        val targeted = parseAutomaticResolutionEffects("Target player gains 3 life. Create a Treasure token.")
        assertEquals(true, targeted.isEmpty)
        assertEquals(true, targeted.requiresManualReview)

        val conditional = parseAutomaticResolutionEffects("If you control a creature, create a Treasure token.")
        assertEquals(true, conditional.isEmpty)
        assertEquals(true, conditional.requiresManualReview)
    }

    @Test
    fun doesNotPartiallyApplyAnUnrecognizedSpell() {
        val effects = parseAutomaticResolutionEffects("You gain 3 life. Target player draws a card.")
        assertEquals(true, effects.isEmpty)
        assertEquals(true, effects.requiresManualReview)
    }

    @Test
    fun identifiesSimpleCreatureEntryLifeTriggers() {
        val effects = parseCreatureEntryLifeEffects(
            "Whenever another creature enters the battlefield under your control, you gain 1 life."
        )
        assertEquals(1, effects.size)
        assertEquals(1, effects.single().amount)
        assertEquals(true, effects.single().anotherOnly)
        assertEquals("under your control", effects.single().controllerFilter)
    }

    @Test
    fun recognizesSimpleCastDrawAndCreatureEntryTriggerEvents() {
        val triggers = parseRecognizedTriggeredAbilities(
            "Whenever you cast a spell, draw a card.\n" +
                "Whenever an opponent draws a card, create a Treasure token.\n" +
                "Whenever another creature enters the battlefield under your control, you gain 1 life."
        )
        assertEquals(listOf("CAST", "DRAW", "CREATURE_ETB"), triggers.map { it.event })
        assertEquals(listOf("YOU", "OPPONENT", "YOU"), triggers.map { it.actorFilter })
        assertEquals(true, triggers.last().anotherOnly)
    }

    @Test
    fun parsesSimpleTargetPlayerEffectsForTargetPrompts() {
        assertEquals(
            TargetedAutomaticEffect("OPPONENT", "lose 3 life"),
            parseTargetedAutomaticEffect("Target opponent loses 3 life.")
        )
        assertEquals(
            TargetedAutomaticEffect("PLAYER", "draw a card"),
            parseTargetedAutomaticEffect("Target player draws a card")
        )
        assertNull(parseTargetedAutomaticEffect("Target creature gets +1/+1."))
        assertEquals("PLAYER", parseTargetPlayerRule("Target player mills two cards."))
        assertNull(parseTargetPlayerRule("Target creature gets +1/+1."))
    }

    @Test
    fun carriesTriggerPromptsAcrossOnlineSnapshots() {
        val prompt = TriggerPrompt(
            id = "trigger-1",
            cardName = "Example Mentor",
            controllerIndex = 1,
            eventPlayerIndex = 0,
            kind = "TARGET_EFFECT",
            triggerText = "Whenever you cast a spell",
            effectText = "lose 2 life",
            targetRule = "OPPONENT"
        )
        val state = OnlineGameState(
            players = listOf(TablePlayer("YOU"), TablePlayer("PLAYER 2")),
            activePlayer = 0,
            turnNumber = 1,
            phaseIndex = 3,
            elapsedGameSeconds = 0,
            elapsedTurnSeconds = 0,
            turnOrder = listOf(0, 1),
            readyPlayers = emptySet(),
            gameLog = emptyList(),
            chat = emptyList(),
            gameStarted = true,
            triggerPrompts = listOf(prompt)
        )
        assertEquals(prompt, parseOnlineGameState(state.toJson())?.triggerPrompts?.single())
    }

    @Test
    fun carriesChosenTargetsOnStackAcrossOnlineSnapshots() {
        val player = TablePlayer("YOU", id = "p1")
        val action = StackAction(
            id = "targeted-spell",
            title = "Murder",
            controllerId = "p1",
            kind = "SPELL",
            card = GameCard(71, "Murder", "Instant", androidx.compose.ui.graphics.Color.Black, oracleText = "Destroy target creature."),
            targetText = "Destroy target creature.",
            targets = listOf(StackTarget("battlefield:p2:40", "Example Creature", "BATTLEFIELD · Creature", "p2")),
            targetsConfirmed = true
        )
        val state = OnlineGameState(
            players = listOf(player, TablePlayer("P2", id = "p2")), activePlayer = 0, turnNumber = 1, phaseIndex = 3,
            elapsedGameSeconds = 0, elapsedTurnSeconds = 0, turnOrder = listOf(0, 1), readyPlayers = emptySet(),
            gameLog = emptyList(), chat = emptyList(), gameStarted = true, stackActions = listOf(action)
        )
        assertEquals(action, parseOnlineGameState(state.toJson())?.stackActions?.single())
    }

    @Test
    fun spendsColorlessBeforeColoredMana() {
        assertEquals(mapOf("R" to 1, "G" to 1), spendGenericMana(mapOf("C" to 2, "R" to 1, "G" to 1), 2))
    }

    @Test
    fun leavesPoolUnchangedWhenCostCannotBePaid() {
        val pool = mapOf("U" to 1)
        assertNull(spendGenericMana(pool, 2))
        assertEquals(mapOf("U" to 1), pool)
    }

    @Test
    fun rejectsNegativeManaCosts() {
        assertNull(spendGenericMana(mapOf("C" to 1), -1))
    }

    @Test
    fun followsRandomizedTurnOrderAndWraps() {
        val order = listOf(2, 0, 3, 1)
        assertEquals(3, nextTurnIndex(0, order, 4))
        assertEquals(2, nextTurnIndex(1, order, 4))
    }

    @Test
    fun fallsBackToSeatOrderForInvalidOrder() {
        assertEquals(2, nextTurnIndex(1, listOf(0, 0, 2), 3))
    }

    @Test
    fun advancesOnePhaseWithoutChangingTurnOwner() {
        assertEquals(TurnProgression(3, 1, 4, false), advanceTurnProgression(2, 10, 1, 4, listOf(1, 0), 2))
    }

    @Test
    fun wrapsFinalPhaseToNextPlayerWithoutAdvancingRoundUntilOrderCompletes() {
        assertEquals(TurnProgression(0, 0, 4, true), advanceTurnProgression(10, 10, 1, 4, listOf(1, 0), 2))
    }

    @Test
    fun turnCounterChangesAfterEveryEligiblePlayerHasTakenATurn() {
        val order = listOf(2, 0, 1)
        val eligible = setOf(0, 1, 2)
        assertEquals(TurnProgression(0, 0, 8, true), advanceTurnProgression(10, 10, 2, 8, order, 3, eligible))
        assertEquals(TurnProgression(0, 1, 8, true), advanceTurnProgression(10, 10, 0, 8, order, 3, eligible))
        assertEquals(TurnProgression(0, 2, 9, true), advanceTurnProgression(10, 10, 1, 8, order, 3, eligible))
    }

    @Test
    fun eliminatedSeatsDoNotDelayRoundCounter() {
        assertEquals(true, isLastEligibleTurnInRound(2, listOf(0, 1, 2, 3), 4, setOf(0, 1, 2)))
        assertEquals(false, isLastEligibleTurnInRound(1, listOf(0, 1, 2, 3), 4, setOf(0, 1, 2)))
    }

    @Test
    fun untapStepRemovesOneStunCounterInsteadOfUntapping() {
        assertEquals(
            UntapStepResult(true, emptyMap(), true),
            resolveUntapStep(true, mapOf("Stun" to 1))
        )
        assertEquals(
            UntapStepResult(true, mapOf("Stun" to 1), true),
            resolveUntapStep(true, mapOf("Stun" to 2))
        )
        assertEquals(
            UntapStepResult(false, mapOf("Stun" to 1), false),
            resolveUntapStep(false, mapOf("Stun" to 1))
        )
        assertEquals(
            UntapStepResult(false, emptyMap(), false),
            resolveUntapStep(true, emptyMap())
        )
    }

    @Test
    fun paysColoredSymbolsBeforeGeneric() {
        assertEquals(mapOf("G" to 1), paySimpleManaCost(mapOf("U" to 1, "C" to 1, "G" to 1), "{U}{1}"))
    }

    @Test
    fun refusesToPayWrongColor() {
        assertNull(paySimpleManaCost(mapOf("C" to 3), "{R}{1}"))
    }

    @Test
    fun reportsHybridCostsForManualPayment() {
        assertEquals(false, supportsSimpleManaCost("{W/U}"))
    }

    @Test
    fun parsesSimpleManaSymbolsWithoutRegex() {
        assertEquals(listOf("2", "W", "C"), simpleManaSymbols("{2}{W}{C}"))
        assertEquals(true, supportsSimpleManaCost("{2}{W}{C}"))
    }

    @Test
    fun rejectsMalformedOrUnsupportedManaSymbols() {
        assertEquals(null, simpleManaSymbols("{2}{W"))
        assertEquals(null, simpleManaSymbols("{W/U}"))
        assertEquals(null, simpleManaSymbols("{2}x"))
    }

    @Test
    fun parsesPrintedCombatStatsWithoutGuessingVariableValues() {
        assertEquals(4, printedCreatureStat("4"))
        assertEquals(-1, printedCreatureStat("-1"))
        assertEquals(null, printedCreatureStat("*"))
        assertEquals(null, printedCreatureStat("1+*"))
        assertEquals(null, printedCreatureStat(null))
    }

    @Test
    fun detectsManaRocksFlexibleManaAndDualLandChoices() {
        assertEquals(listOf(mapOf("C" to 2)), manaProductionPools("Sol Ring", "Artifact", "{T}: Add {C}{C}."))
        assertEquals(listOf("W", "U", "B", "R", "G").map { mapOf(it to 1) }, manaProductionPools("Arcane Signet", "Artifact", "{T}: Add one mana of any color in your commander's color identity."))
        assertEquals(listOf(mapOf("R" to 1), mapOf("G" to 1)), manaProductionPools("Dual Land", "Land", "{T}: Add {R} or {G}."))
        assertEquals(listOf(mapOf("U" to 1)), manaProductionPools("Island", "Basic Land — Island", null))
    }

    @Test
    fun doesNotAutoTrackManaAbilitiesWithAdditionalCosts() {
        assertEquals(emptyList<Map<String, Int>>(), manaProductionPools("Costly Rock", "Artifact", "{T}, Sacrifice this artifact: Add {C}{C}."))
    }

    @Test
    fun aiChoosesProfitableBlockAndDoesNotReuseAssignedBlocker() {
        val attacker = CombatCreature(1, 4, 4)
        val strongBlocker = CombatCreature(2, 5, 5)
        val smallBlocker = CombatCreature(3, 1, 1)
        assertEquals(setOf(2), chooseAiBlockers(attacker, listOf(strongBlocker, smallBlocker), 40))
        assertEquals(emptySet<Int>(), chooseAiBlockers(attacker, listOf(strongBlocker, smallBlocker), 40, setOf(2)))
    }

    @Test
    fun flyingAttackerNeedsFlyingOrReachBlocker() {
        val attacker = CombatCreature(1, 2, 2, setOf("Flying"))
        val ground = CombatCreature(2, 3, 3)
        val reach = CombatCreature(3, 3, 3, setOf("Reach"))
        assertEquals(setOf(3), chooseAiBlockers(attacker, listOf(ground, reach), 40))
    }

    @Test
    fun menaceAttackerGetsTwoBlockersWhenTheyCanStopIt() {
        val attacker = CombatCreature(1, 3, 3, setOf("Menace"))
        val blockers = listOf(CombatCreature(2, 4, 4), CombatCreature(3, 2, 2), CombatCreature(4, 1, 1))
        assertEquals(setOf(2, 4), chooseAiBlockers(attacker, blockers, 40))
    }

    @Test
    fun aiChumpBlocksOnlyLethalNonTrampleAttack() {
        val blocker = CombatCreature(2, 1, 1)
        assertEquals(setOf(2), chooseAiBlockers(CombatCreature(1, 5, 5), listOf(blocker), 5))
        assertEquals(emptySet<Int>(), chooseAiBlockers(CombatCreature(1, 5, 5), listOf(blocker), 6))
        assertEquals(emptySet<Int>(), chooseAiBlockers(CombatCreature(1, 5, 5, setOf("Trample")), listOf(blocker), 5))
    }

    @Test
    fun aiDoesNotGuessVariablePowerOrBlockUnblockableAttacker() {
        val blocker = CombatCreature(2, 8, 8)
        assertEquals(emptySet<Int>(), chooseAiBlockers(CombatCreature(1, null, 4), listOf(blocker), 40))
        assertEquals(emptySet<Int>(), chooseAiBlockers(CombatCreature(1, 8, 8, oracleText = "This creature can't be blocked."), listOf(blocker), 40))
    }
}
