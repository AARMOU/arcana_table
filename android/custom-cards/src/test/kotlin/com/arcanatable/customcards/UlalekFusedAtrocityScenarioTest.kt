package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UlalekFusedAtrocity
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.OrderObjectsDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaSymbol
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Ulalek, Fused Atrocity (M3C #4) on the patched engine: true {C/color} hybrid cost, and the optional
 * {C}{C} that copies all spells you control, then all other activated and triggered abilities you
 * control (Oracle text and MH3 release notes).
 */
class UlalekFusedAtrocityScenarioTest {

    private val ulalek = "Ulalek, Fused Atrocity"
    private val fiend = "Elder Deep-Fiend" // {8} Eldrazi with flash and a "when you cast" trigger

    /** Names of everything on the stack, top first; abilities as "<source> ability". */
    private fun GameTestDriver.stackObjects(): List<String> = state.stack.reversed().mapNotNull { id ->
        val e = state.getEntity(id) ?: return@mapNotNull null
        when {
            e.has<SpellOnStackComponent>() -> e.get<CardComponent>()?.name
            e.has<TriggeredAbilityOnStackComponent>() -> "${e.get<TriggeredAbilityOnStackComponent>()!!.sourceName} ability"
            e.has<ActivatedAbilityOnStackComponent>() -> "${e.get<ActivatedAbilityOnStackComponent>()!!.sourceName} activated"
            else -> null
        }
    }

    private fun GameTestDriver.isToken(id: EntityId) = state.getEntity(id)?.has<TokenComponent>() == true

    /** Resolves everything; answers Ulalek's "Pay {C}{C}?" with [pay] and counts how often it was asked. */
    private fun GameTestDriver.resolveUlalek(pay: Boolean, choices: Choices = Choices(), maxSteps: Int = 80): Int {
        var asked = 0
        repeat(maxSteps) {
            val decision = pendingDecision
            when {
                decision is YesNoDecision && decision.context.sourceName == ulalek -> {
                    asked++
                    submitYesNo(decision.playerId, pay)
                }
                // Ulalek's controller orders each batch of copies; keep the stack's own order.
                decision is OrderObjectsDecision -> submitDecision(decision.playerId, OrderedResponse(decision.id, decision.objects))
                decision != null -> decide(choices)
                state.stack.isNotEmpty() -> bothPass()
                else -> return asked
            }
        }
        error("Stack did not settle: ${stackObjects()} decision=${pendingDecision}")
    }

    // ---- Card definition ----

    @Test
    fun printedCostIsFiveColorlessHybridSymbolsWithFiveColorIdentityAndDevoid() {
        val symbols = UlalekFusedAtrocity.manaCost.symbols
        assertEquals(5, symbols.size)
        assertTrue(symbols.all { it is ManaSymbol.ColorlessHybrid })
        assertEquals(setOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN),
            symbols.map { (it as ManaSymbol.ColorlessHybrid).color }.toSet())
        assertEquals(5, UlalekFusedAtrocity.manaCost.cmc)
        assertEquals(setOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN), UlalekFusedAtrocity.colorIdentity)

        val d = customScenario(UlalekFusedAtrocity)
        val u = d.putCreatureOnBattlefield(d.player1, ulalek)
        assertTrue(d.projected.hasKeyword(u, Keyword.DEVOID))
        assertTrue("Devoid: the card has no color", d.projected.getColors(u).isEmpty())
    }

    // ---- Colorless-hybrid payment ----

    private fun castUlalekWith(pay: (GameTestDriver, EntityId) -> Unit): Pair<GameTestDriver, String?> {
        val d = customScenario(UlalekFusedAtrocity)
        val card = d.putCardInHand(d.player1, ulalek)
        pay(d, d.player1)
        val error = d.castSpell(d.player1, card).error
        if (error == null) d.resolveAll()
        return d to error
    }

    @Test
    fun eachHalfCanBePaidWithItsColor() {
        val (d, error) = castUlalekWith { d, p -> listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN).forEach { d.giveMana(p, it) } }
        assertNull(error)
        assertNotNull(d.findPermanent(d.player1, ulalek))
        assertEquals(0, d.manaPool(d.player1).let { it.white + it.blue + it.black + it.red + it.green + it.colorless })
    }

    @Test
    fun eachHalfCanBePaidWithColorlessMana() {
        val (d, error) = castUlalekWith { d, p -> d.giveColorlessMana(p, 5) }
        assertNull(error)
        assertNotNull(d.findPermanent(d.player1, ulalek))
    }

    @Test
    fun mixedColorlessAndColoredPaymentWorks() {
        val (d, error) = castUlalekWith { d, p -> d.giveColorlessMana(p, 3); d.giveMana(p, Color.WHITE); d.giveMana(p, Color.GREEN) }
        assertNull(error)
        assertNotNull(d.findPermanent(d.player1, ulalek))
    }

    @Test
    fun manaOfOneColorCannotPayTheOtherColorsHalves() {
        val (d, error) = castUlalekWith { d, p -> d.giveMana(p, Color.RED, 5) }
        assertNotNull("Five red mana pays only {C/R}; the other four halves need colorless or their color", error)
        assertNull(d.findPermanent(d.player1, ulalek))
    }

    // ---- The {C}{C} copy trigger ----

    private fun withUlalekOnBattlefield(vararg library: String): GameTestDriver =
        customScenario(UlalekFusedAtrocity, fiend, *library).also { it.putCreatureOnBattlefield(it.player1, ulalek) }

    @Test
    fun payingCopiesTheEldraziSpellAndThePermanentCopyBecomesAToken() {
        val d = withUlalekOnBattlefield()
        val card = d.putCardInHand(d.player1, fiend)
        d.giveColorlessMana(d.player1, 10)
        assertNull(d.castSpell(d.player1, card).error)
        assertTrue("Ulalek triggers on casting an Eldrazi spell", d.stackObjects().contains("$ulalek ability"))

        assertEquals("Asked once; copies are not cast, so nothing retriggers", 1, d.resolveUlalek(pay = true))
        val fiends = d.permanentsNamed(d.player1, fiend)
        assertEquals(2, fiends.size)
        assertEquals("Exactly one of them is a token copy", 1, fiends.count { d.isToken(it) })
        assertEquals("{C}{C} was paid from the two spare colorless", 0, d.manaPool(d.player1).colorless)
    }

    @Test
    fun decliningMakesNoCopiesAndKeepsTheMana() {
        val d = withUlalekOnBattlefield()
        val card = d.putCardInHand(d.player1, fiend)
        d.giveColorlessMana(d.player1, 10)
        assertNull(d.castSpell(d.player1, card).error)
        assertEquals(1, d.resolveUlalek(pay = false))
        assertEquals(1, d.permanentsNamed(d.player1, fiend).size)
        assertEquals(2, d.manaPool(d.player1).colorless)
    }

    @Test
    fun coloredManaCannotPayTheColorlessCost() {
        val d = withUlalekOnBattlefield()
        val card = d.putCardInHand(d.player1, fiend)
        d.giveMana(d.player1, Color.GREEN, 10)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveUlalek(pay = true)
        assertEquals("Two green can't pay {C}{C}: no copy", 1, d.permanentsNamed(d.player1, fiend).size)
        assertEquals("Green mana is not spent on a failed payment", 2, d.manaPool(d.player1).green)
    }

    @Test
    fun nonEldraziSpellsDoNotTrigger() {
        val d = withUlalekOnBattlefield("Grizzly Bears")
        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 2)
        d.giveMana(d.player1, Color.GREEN)
        assertNull(d.castSpell(d.player1, bears).error)
        assertFalse(d.stackObjects().contains("$ulalek ability"))
        assertEquals(0, d.resolveUlalek(pay = true))
    }

    @Test
    fun otherTriggeredAbilitiesAreCopiedButUlaleksOwnIsNot() {
        val d = withUlalekOnBattlefield("Beast Whisperer", "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Beast Whisperer") // whenever you cast a creature spell, draw a card
        // A creature spell puts a Beast Whisperer trigger on the stack BELOW the Eldrazi cast.
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN); d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        assertEquals(listOf("Beast Whisperer ability", "Grizzly Bears"), d.stackObjects())
        val card = d.putCardInHand(p, fiend)
        val handBefore = d.getHand(p).size - 1
        d.giveColorlessMana(p, 10)
        assertNull(d.castSpell(p, card).error)

        assertEquals("Ulalek's own trigger is not copied, so it is asked once", 1, d.resolveUlalek(pay = true))
        // Draws: the Eldrazi's own Whisperer trigger, the older Whisperer trigger, and its Ulalek copy.
        assertEquals(handBefore + 3, d.getHand(p).size)
        assertEquals("Both of my spells were copied", 2, d.permanentsNamed(p, "Grizzly Bears").size)
        assertEquals(2, d.permanentsNamed(p, fiend).size)
    }

    @Test
    fun activatedAbilitiesAreCopiedWithTheirTargets() {
        val d = withUlalekOnBattlefield("Prodigal Pyromancer")
        val p = d.player1
        val pyro = d.putCreatureOnBattlefield(p, "Prodigal Pyromancer") // {T}: 1 damage to any target
        d.removeSummoningSickness(pyro)
        val ability = d.cardRegistry.getCard("Prodigal Pyromancer")!!.script.activatedAbilities.single().id
        assertNull(d.submit(ActivateAbility(p, pyro, ability, targets = listOf(ChosenTarget.Player(d.player2)))).error)
        assertTrue(d.stackObjects().any { it.startsWith("Prodigal Pyromancer") })
        val card = d.putCardInHand(p, fiend)
        d.giveColorlessMana(p, 10)
        assertNull(d.castSpell(p, card).error)

        d.resolveUlalek(pay = true, choices = Choices(yes = false, targets = listOf(d.player2)))
        assertEquals("Pyromancer ability plus its copy", 18, d.getLifeTotal(d.player2))
    }

    @Test
    fun opponentsActivatedAbilitiesAreNotCopiedEvenWhenTheyAreOnTheStack() {
        val d = withUlalekOnBattlefield("Prodigal Pyromancer", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val pyro = d.putCreatureOnBattlefield(opponent, "Prodigal Pyromancer")
        d.removeSummoningSickness(pyro)

        // Give the opponent priority while our creature spell is on the stack.
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        d.passPriority(p)
        val ability = d.cardRegistry.getCard("Prodigal Pyromancer")!!.script.activatedAbilities.single().id
        assertNull(d.submit(ActivateAbility(opponent, pyro, ability,
            targets = listOf(ChosenTarget.Player(p)))).error)
        assertTrue(d.stackObjects().contains("Prodigal Pyromancer activated"))

        // Ulalek can copy our own pending spells, but never the opponent's activation.
        d.passPriority(opponent)
        val fiendCard = d.putCardInHand(p, fiend)
        d.giveColorlessMana(p, 10)
        assertNull(d.castSpell(p, fiendCard).error)
        assertEquals(1, d.resolveUlalek(pay = true))
        assertEquals("Opponent's Pyromancer ability must resolve only once", 19, d.getLifeTotal(p))
        assertEquals(2, d.permanentsNamed(p, "Grizzly Bears").size)
        assertEquals(2, d.permanentsNamed(p, fiend).size)
    }

    @Test
    fun otherSpellsYouControlAreCopiedAndCopiesCanBeRetargeted() {
        val d = withUlalekOnBattlefield("Lightning Bolt", "Grizzly Bears")
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpell(d.player1, bolt, listOf(opponent)).error)
        // Still holding priority: cast the flash Eldrazi on top of the Bolt.
        val card = d.putCardInHand(d.player1, fiend)
        d.giveColorlessMana(d.player1, 10)
        assertNull(d.castSpell(d.player1, card).error)

        // The Bolt copy is pointed at the Bear; the original still hits the opponent.
        d.resolveUlalek(pay = true, choices = Choices(yes = true, targets = listOf(bear)))
        assertEquals("Original Bolt", 17, d.getLifeTotal(opponent))
        assertTrue("Retargeted Bolt copy killed the Bear", d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
        assertEquals(2, d.permanentsNamed(d.player1, fiend).size)
    }

    @Test
    fun opponentsSpellsAreNotCopied() {
        val d = withUlalekOnBattlefield("Shock", "Grizzly Bears")
        val p = d.player1
        // Put a spell of mine on the stack so the opponent can respond at instant speed.
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN); d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        d.passPriority(p)
        val shock = d.putCardInHand(d.player2, "Shock")
        d.giveMana(d.player2, Color.RED)
        assertNull(d.castSpell(d.player2, shock, listOf(p)).error)
        d.passPriority(d.player2)
        val card = d.putCardInHand(p, fiend)
        d.giveColorlessMana(p, 10)
        assertNull(d.castSpell(p, card).error)

        d.resolveUlalek(pay = true)
        assertEquals("Only the opponent's one Shock resolved", 18, d.getLifeTotal(p))
        assertEquals("My Bears spell was copied", 2, d.permanentsNamed(p, "Grizzly Bears").size)
        assertEquals(2, d.permanentsNamed(p, fiend).size)
    }

    @Test
    fun withNoOtherObjectsOnlyTheTriggeringSpellIsCopied() {
        val d = withUlalekOnBattlefield()
        val card = d.putCardInHand(d.player1, fiend)
        d.giveColorlessMana(d.player1, 10)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveUlalek(pay = true)
        assertTrue(d.state.stack.isEmpty())
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).isEmpty())
        assertEquals(3, d.getPermanents(d.player1).size) // Ulalek, Fiend, token Fiend
    }

    @Test
    fun castingUlalekItselfDoesNotTriggerItWhenItIsNotOnTheBattlefield() {
        val d = customScenario(UlalekFusedAtrocity)
        val card = d.putCardInHand(d.player1, ulalek)
        d.giveColorlessMana(d.player1, 7)
        assertNull(d.castSpell(d.player1, card).error)
        assertEquals(listOf(ulalek), d.stackObjects())
        assertEquals(0, d.resolveUlalek(pay = true))
        assertEquals(1, d.permanentsNamed(d.player1, ulalek).size)
    }

    // ---- As a commander ----

    @Test
    fun commanderCastFromCommandZoneWithHybridCostAndTax() {
        val d = CardPool.driver().apply {
            registerCards(listOf(com.wingedsheep.sdk.tooling.CardLoader.fromJsonPreservingIds(exportLine(UlalekFusedAtrocity))))
        }
        val seats = d.initMultiplayer(
            decks = List(2) { com.wingedsheep.sdk.model.Deck.of("Plains" to 40) },
            format = com.wingedsheep.sdk.core.Format.Commander(),
            commanders = listOf(ulalek, "Inquisitor Greyfax"),
        )
        val p = seats[0]
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        val inZone = d.state.getZone(p, Zone.COMMAND).single { d.getCardName(it) == ulalek }

        listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN).forEach { d.giveMana(p, it) }
        assertNull("First cast from the command zone, one mana of each color", d.castSpell(p, inZone).error)
        d.resolveAll()
        val onBattlefield = d.findPermanent(p, ulalek)!!

        d.moveToGraveyard(onBattlefield)
        var guard = 0
        while (guard++ < 10) {
            val decision = d.pendingDecision
            if (decision is YesNoDecision) { d.submitYesNo(p, true); break }
            if (decision != null) d.autoResolveDecision() else d.passPriority(d.state.priorityPlayerId ?: break)
        }
        val back = d.state.getZone(p, Zone.COMMAND).single { d.getCardName(it) == ulalek }
        if (d.state.step != com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN && d.state.step != com.wingedsheep.sdk.core.Step.POSTCOMBAT_MAIN)
            d.passPriorityUntil(com.wingedsheep.sdk.core.Step.POSTCOMBAT_MAIN)
        d.giveColorlessMana(p, 5)
        assertFalse("Second cast needs the {2} tax", d.legalActions(p).any { (it.action as? com.wingedsheep.engine.core.CastSpell)?.cardId == back })
        d.giveColorlessMana(p, 2)
        assertNull("Five colorless for the hybrid halves plus {2} tax", d.castSpell(p, back).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, ulalek))
    }
}
