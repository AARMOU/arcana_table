package com.arcanatable.customcards

import com.arcanatable.customcards.cards.OpalPalace
import com.arcanatable.customcards.support.*
import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
import org.junit.Assert.*
import org.junit.Test

/**
 * Opal Palace (SOC #390) on the real engine, in Commander games. Player 1 commands the partners
 * Thrasios, Triton Hero ({G}{U}) and Akiri, Fearless Voyager ({1}{R}{W}): combined identity
 * {W, U, R, G}.
 *
 * Rulings (2020-11-10) the tests pin:
 * - "The "number of times it's been cast from the command zone" includes the most recent time. For
 *   example, the first time you cast your commander from the command zone in a game, if you spent
 *   mana from Opal Palace's last ability to do so, it will enter the battlefield with a +1/+1
 *   counter."
 * - "If you have two commanders, the last ability adds one mana of any color in their combined color
 *   identities."
 * - "If you don't have a commander, Opal Palace's last ability produces no mana."
 * - "If your commander is a card that has no colors in its color identity, Opal Palace's last
 *   ability produces no mana. It doesn't produce {C}."
 * The two-mana (Mana Reflection) ruling is pinned by engine patch 0034's own test, since Mana
 * Reflection isn't in the library.
 */
class OpalPalaceScenarioTest {
    private val thrasios = "Thrasios, Triton Hero"
    private val akiri = "Akiri, Fearless Voyager"
    private val rider = ManaSpellRider.CommanderEntersWithCastCountCounters()

    private fun game(commander: String = thrasios, partner: String? = akiri): Pair<GameTestDriver, EntityId> {
        val d = customScenario(
            OpalPalace, thrasios, akiri, "Traxos, Scourge of Kroog", "Akroma, Angel of Wrath",
            "Grizzly Bears", "Counterspell", "Unsummon",
        ).apply {
            initMultiplayer(
                decks = List(2) { Deck.of("Plains" to 40) },
                format = Format.Commander(),
                commanders = listOf(commander, "Akroma, Angel of Wrath"),
                partners = listOf(partner, null),
            )
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        return d to d.player1
    }

    private fun GameTestDriver.inCommandZone(p: EntityId, name: String): EntityId =
        state.getZone(p, Zone.COMMAND).single { getCardName(it) == name }

    private fun GameTestDriver.castsFromCommandZone(id: EntityId): Int =
        state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone

    private fun GameTestDriver.plusOnes(id: EntityId): Int =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun GameTestDriver.palaceManaFloating(p: EntityId): Int =
        manaPool(p).restrictedMana.count { rider in it.riders }

    private fun GameTestDriver.poolIsEmpty(p: EntityId): Boolean = manaPool(p).let {
        it.white + it.blue + it.black + it.red + it.green + it.colorless == 0 && it.restrictedMana.isEmpty()
    }

    private fun GameTestDriver.give(p: EntityId, colors: List<Color>, generic: Int = 0) {
        colors.forEach { giveMana(p, it, 1) }
        if (generic > 0) giveColorlessMana(p, generic)
    }

    /** A fresh Opal Palace, its {1} paid from the pool, adding [color] (or asking, when null). */
    private fun GameTestDriver.palaceMana(p: EntityId, color: Color?): ExecutionResult {
        assertTrue("pool must be empty before paying the Palace's {1}", poolIsEmpty(p))
        val palace = putLandOnBattlefield(p, "Opal Palace")
        giveColorlessMana(p, 1)
        val ability = cardRegistry.requireCard("Opal Palace").script.activatedAbilities[1]
        return submit(ActivateAbility(p, palace, ability.id, manaColorChoice = color))
    }

    /** Casts [name] from the command zone with exactly [colors] + [generic], then counters it. */
    private fun GameTestDriver.counteredCommandZoneCast(p: EntityId, name: String, colors: List<Color>, generic: Int = 0) {
        val id = inCommandZone(p, name)
        give(p, colors, generic)
        assertNull(castSpell(p, id).error)
        val counterspell = putCardInHand(p, "Counterspell")
        give(p, listOf(Color.BLUE, Color.BLUE))
        assertNull(castSpellWithTargets(p, counterspell, listOf(ChosenTarget.Spell(id))).error)
        resolveAll(Choices(yes = true)) // countered, then "put it into the command zone?" yes
        assertTrue("$name back in the command zone", name in zoneNames(p, Zone.COMMAND))
        assertTrue(poolIsEmpty(p))
    }

    // --- {T}: Add {C}. and the {1} cost ---------------------------------------------------------

    @Test
    fun firstAbilityAddsColorless() {
        val (d, p) = game()
        val palace = d.putLandOnBattlefield(p, "Opal Palace")
        assertNull(d.activate(p, palace, 0).error)
        assertTrue(d.isTapped(palace))
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(0, d.palaceManaFloating(p))
    }

    @Test
    fun secondAbilityNeedsItsGenericManaPaid() {
        val (d, p) = game()
        val palace = d.putLandOnBattlefield(p, "Opal Palace")
        val ability = d.cardRegistry.requireCard("Opal Palace").script.activatedAbilities[1]
        assertNotNull(d.submit(ActivateAbility(p, palace, ability.id, manaColorChoice = Color.WHITE)).error)
        assertFalse(d.isTapped(palace))
        assertEquals(0, d.palaceManaFloating(p))

        d.giveColorlessMana(p, 1)
        assertNull(d.submit(ActivateAbility(p, palace, ability.id, manaColorChoice = Color.WHITE)).error)
        assertTrue(d.isTapped(palace))
        assertEquals("{1} paid", 0, d.manaPool(p).colorless)
        assertEquals(listOf(Color.WHITE), d.manaPool(p).restrictedMana.filter { rider in it.riders }.map { it.color })
    }

    // --- Colors: exactly the commanders' combined identity ----------------------------------------

    @Test
    fun colorsOfferedAreExactlyThePartnersCombinedIdentity() {
        val (d, p) = game()
        assertNull(d.palaceMana(p, null).error)
        val decision = d.state.pendingDecision as ChooseColorDecision
        assertEquals(setOf(Color.WHITE, Color.BLUE, Color.RED, Color.GREEN), decision.availableColors)
        assertNull(d.submitDecision(p, ColorChosenResponse(decision.id, Color.RED)).error)
        assertEquals(listOf(Color.RED), d.manaPool(p).restrictedMana.filter { rider in it.riders }.map { it.color })
        assertEquals(0, d.manaPool(p).black)
    }

    @Test
    fun singleCommanderIdentityOnly() {
        val (d, p) = game(commander = akiri, partner = null)
        assertNull(d.palaceMana(p, null).error)
        val decision = d.state.pendingDecision as ChooseColorDecision
        assertEquals(setOf(Color.RED, Color.WHITE), decision.availableColors)
    }

    @Test
    fun colorlessIdentityCommanderMakesNoMana() {
        val (d, p) = game(commander = "Traxos, Scourge of Kroog", partner = null)
        assertNull("the ability can be activated; it just produces nothing", d.palaceMana(p, Color.WHITE).error)
        assertNull("no color to choose", d.state.pendingDecision)
        assertTrue("no colored mana and no {C}", d.poolIsEmpty(p))
    }

    @Test
    fun noCommanderMakesNoMana() {
        val d = customScenario(OpalPalace)
        val p = d.player1
        assertNull(d.palaceMana(p, Color.WHITE).error)
        assertNull(d.state.pendingDecision)
        assertTrue("no mana at all", d.poolIsEmpty(p))
    }

    // --- #671 acceptance 1: each commander's own count -------------------------------------------

    @Test
    fun partnerCastWithPalaceManaCountsOnlyItsOwnCasts() {
        val (d, p) = game()
        d.counteredCommandZoneCast(p, thrasios, listOf(Color.GREEN, Color.BLUE))
        d.counteredCommandZoneCast(p, thrasios, listOf(Color.GREEN, Color.BLUE), generic = 2)
        assertEquals(2, d.castsFromCommandZone(d.inCommandZone(p, thrasios)))

        val akiriId = d.inCommandZone(p, akiri)
        assertNull(d.palaceMana(p, Color.WHITE).error)
        d.give(p, listOf(Color.RED), generic = 1)
        assertNull(d.castSpell(p, akiriId).error)
        assertEquals("the Palace's {W} paid for Akiri", 0, d.palaceManaFloating(p))
        d.resolveAll()

        val onField = d.findPermanent(p, akiri)!!
        assertEquals("Akiri's first command-zone cast: one counter, not 1 + Thrasios's 2", 1, d.plusOnes(onField))
        assertEquals(4, d.projected.getPower(onField))
    }

    @Test
    fun swappedPartnersCountOnlyThrasiosCasts() {
        val (d, p) = game()
        d.counteredCommandZoneCast(p, akiri, listOf(Color.RED, Color.WHITE), generic = 1)
        d.counteredCommandZoneCast(p, akiri, listOf(Color.RED, Color.WHITE), generic = 3)
        assertEquals(2, d.castsFromCommandZone(d.inCommandZone(p, akiri)))

        val thrasiosId = d.inCommandZone(p, thrasios)
        assertNull(d.palaceMana(p, Color.GREEN).error)
        d.give(p, listOf(Color.BLUE))
        assertNull(d.castSpell(p, thrasiosId).error)
        assertEquals(0, d.palaceManaFloating(p))
        d.resolveAll()

        assertEquals(1, d.plusOnes(d.findPermanent(p, thrasios)!!))
    }

    // --- #671 acceptance 2: provenance -----------------------------------------------------------

    @Test
    fun onlyPalaceManaSpentOnThatCommanderCounts() {
        val (d, p) = game()

        // Palace {W} floats; Thrasios ({G}{U}) is cast with ordinary mana and can't use it.
        assertNull(d.palaceMana(p, Color.WHITE).error)
        d.give(p, listOf(Color.GREEN, Color.BLUE))
        assertNull(d.castSpell(p, d.inCommandZone(p, thrasios)).error)
        assertEquals("Palace mana still unspent", 1, d.palaceManaFloating(p))
        d.resolveAll()
        assertEquals("first command-zone cast, but no Palace mana spent on it", 0, d.plusOnes(d.findPermanent(p, thrasios)!!))

        // The floating Palace {W} pays for a noncommander: nothing.
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.give(p, listOf(Color.GREEN))
        assertNull(d.castSpell(p, bears).error)
        assertEquals(0, d.palaceManaFloating(p))
        d.resolveAll()
        assertEquals(0, d.plusOnes(d.findPermanent(p, "Grizzly Bears")!!))

        // Positive control: Palace mana on Akiri's second command-zone cast (the first was countered).
        d.counteredCommandZoneCast(p, akiri, listOf(Color.RED, Color.WHITE), generic = 1)
        val akiriId = d.inCommandZone(p, akiri)
        assertNull(d.palaceMana(p, Color.WHITE).error)
        d.give(p, listOf(Color.RED), generic = 3) // {1} + tax {2}
        assertNull(d.castSpell(p, akiriId).error)
        assertEquals(2, d.castsFromCommandZone(akiriId))
        d.resolveAll()
        assertEquals("countered first cast + this cast", 2, d.plusOnes(d.findPermanent(p, akiri)!!))
    }

    // --- #671 acceptance 3: other zones don't add to the count, but still read it ---------------

    @Test
    fun castFromHandReadsTheCommandZoneCountWithoutAddingToIt() {
        val (d, p) = game()
        val id = d.inCommandZone(p, thrasios)
        d.give(p, listOf(Color.GREEN, Color.BLUE))
        assertNull(d.castSpell(p, id).error)
        d.resolveAll()
        assertEquals(1, d.castsFromCommandZone(id))

        fun bounceToHand() {
            val target = d.findPermanent(p, thrasios)!!
            val unsummon = d.putCardInHand(p, "Unsummon")
            d.give(p, listOf(Color.BLUE))
            assertNull(d.castSpell(p, unsummon, listOf(target)).error)
            d.resolveAll(Choices(yes = false)) // keep it in hand, not the command zone
            assertTrue(thrasios in d.zoneNames(p, Zone.HAND))
        }

        // Hand cast with Palace mana: counters equal to the command-zone count (1).
        bounceToHand()
        assertNull(d.palaceMana(p, Color.GREEN).error)
        d.give(p, listOf(Color.BLUE))
        assertNull(d.castSpell(p, d.findCardInHand(p, thrasios)!!).error)
        assertEquals(0, d.palaceManaFloating(p))
        assertEquals("a hand cast doesn't add to the count", 1, d.castsFromCommandZone(d.state.stack.single()))
        d.resolveAll()
        assertEquals(1, d.plusOnes(d.findPermanent(p, thrasios)!!))

        // Hand cast with ordinary mana: nothing.
        bounceToHand()
        d.give(p, listOf(Color.GREEN, Color.BLUE))
        assertNull(d.castSpell(p, d.findCardInHand(p, thrasios)!!).error)
        d.resolveAll()
        assertEquals(0, d.plusOnes(d.findPermanent(p, thrasios)!!))

        // Another Palace-funded hand cast: still 1, the two hand casts never counted.
        bounceToHand()
        assertNull(d.palaceMana(p, Color.GREEN).error)
        d.give(p, listOf(Color.BLUE))
        assertNull(d.castSpell(p, d.findCardInHand(p, thrasios)!!).error)
        d.resolveAll()
        val onField = d.findPermanent(p, thrasios)!!
        assertEquals(1, d.castsFromCommandZone(onField))
        assertEquals(1, d.plusOnes(onField))
    }

    // --- Auto-pay and the AI with Opal Palace among ordinary lands --------------------------------

    private fun GameTestDriver.emptyHand(p: EntityId) {
        var s = state
        for (id in s.getZone(p, Zone.HAND)) s = s.removeFromZone(com.wingedsheep.engine.state.ZoneKey(p, Zone.HAND), id)
        replaceState(s)
    }

    /** Lets the AI act for [p] until it passes priority (at most [max] actions); every action must be legal. */
    private fun GameTestDriver.aiActsUntilPass(p: EntityId, max: Int = 12): List<String> {
        val ai = AIPlayer.create(cardRegistry, p)
        val taken = mutableListOf<String>()
        repeat(max) {
            val decision = state.pendingDecision
            val action = if (decision != null) SubmitDecision(decision.playerId, ai.respondToDecision(state, decision))
                else ai.chooseAction(state)
            taken += action::class.simpleName ?: "?"
            assertNull("AI action $action rejected", submit(action).error)
            if (decision == null && action is PassPriority) return taken
        }
        return taken
    }

    @Test
    fun autoPayCastsANormalSpellWithPalaceColorlessAndABasic() {
        val (d, p) = game()
        d.emptyHand(p)
        d.putLandOnBattlefield(p, "Opal Palace")
        d.putLandOnBattlefield(p, "Forest")
        val bears = d.putCardInHand(p, "Grizzly Bears")
        assertTrue("Grizzly Bears offered", d.legalActions(p).any { (it.action as? CastSpell)?.cardId == bears })
        assertNull(d.submit(CastSpell(p, bears, paymentStrategy = PaymentStrategy.AutoPay)).error)
        d.resolveAll()
        assertEquals(0, d.plusOnes(d.findPermanent(p, "Grizzly Bears")!!))
    }

    @Test
    fun aiCastsANormalSpellWithPalaceAndABasic() {
        val (d, p) = game()
        d.emptyHand(p)
        d.putLandOnBattlefield(p, "Opal Palace")
        d.putLandOnBattlefield(p, "Forest")
        d.putCardInHand(p, "Grizzly Bears")
        val taken = d.aiActsUntilPass(p)
        d.resolveAll()
        assertNotNull("AI cast Grizzly Bears with Palace {C} + Forest; actions=$taken", d.findPermanent(p, "Grizzly Bears"))
    }

    @Test
    fun autoPayUsesPalaceColoredManaForTheCommanderAndItGetsItsCounter() {
        val (d, p) = game()
        d.emptyHand(p)
        d.putLandOnBattlefield(p, "Opal Palace")
        d.putLandOnBattlefield(p, "Forest")
        d.putLandOnBattlefield(p, "Forest")
        val id = d.inCommandZone(p, thrasios)
        assertTrue("Thrasios offered", d.legalActions(p).any { (it.action as? CastSpell)?.cardId == id })
        assertNull(d.submit(CastSpell(p, id, paymentStrategy = PaymentStrategy.AutoPay)).error)
        d.resolveAll()
        assertEquals("{U} came from the Palace (paid by a Forest)", 1, d.plusOnes(d.findPermanent(p, thrasios)!!))
    }

    @Test
    fun aiCastsItsCommanderWithPalaceColoredMana() {
        val (d, p) = game()
        d.emptyHand(p)
        d.putLandOnBattlefield(p, "Opal Palace")
        d.putLandOnBattlefield(p, "Forest")
        d.putLandOnBattlefield(p, "Forest")
        val taken = d.aiActsUntilPass(p)
        d.resolveAll()
        assertNotNull("AI cast Thrasios using Palace {U}; actions=$taken", d.findPermanent(p, thrasios))
    }
}
