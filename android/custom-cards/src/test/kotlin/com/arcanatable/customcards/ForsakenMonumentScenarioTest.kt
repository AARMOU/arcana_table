package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ForsakenMonument
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.TimingRule
import org.junit.Assert.*
import org.junit.Test

class ForsakenMonumentScenarioTest {
    private fun setup(): GameTestDriver = customScenario(ForsakenMonument, "Grizzly Bears", "Ornithopter", "Wastes", "Sol Ring").apply {
        registerCard(card("Monument Colorless Probe") {
            manaCost = "{2}"
            typeLine = "Creature — Eldrazi"
            power = 3
            toughness = 3
        })
        registerCard(card("Monument Devoid Probe") {
            manaCost = "{1}{R}"
            colorIdentity = "R"
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            keywords(Keyword.DEVOID)
        })
        registerCard(card("Monument Colored Eldrazi Probe") {
            manaCost = "{1}{R}"
            colorIdentity = "R"
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
        })
    }

    private fun GameTestDriver.tapForMana(p: EntityId, source: EntityId) {
        val action = abilityActions(p, source).first().action as ActivateAbility
        assertNull(submit(action).error)
        resolveAll()
    }

    private fun GameTestDriver.stats(id: EntityId) = projected.getPower(id) to projected.getToughness(id)

    @Test fun anthemBuffsOnlyColorlessCreaturesYouControl() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val mine = d.putCreatureOnBattlefield(d.player1, "Monument Colorless Probe")
        val thopter = d.putCreatureOnBattlefield(d.player1, "Ornithopter")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Monument Colorless Probe")
        assertEquals(5 to 5, d.stats(mine))
        assertEquals(2 to 4, d.stats(thopter))
        assertEquals("colored creature untouched", 2 to 2, d.stats(bear))
        assertEquals("opponent's colorless creature untouched", 3 to 3, d.stats(theirs))
    }

    @Test fun anthemEndsWhenMonumentLeaves() {
        val d = setup()
        val monument = d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val mine = d.putCreatureOnBattlefield(d.player1, "Monument Colorless Probe")
        assertEquals(5 to 5, d.stats(mine))
        d.moveToGraveyard(monument)
        assertEquals(3 to 3, d.stats(mine))
    }

    @Test fun tappingAColorlessLandAddsAnAdditionalColorless() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val wastes = d.putLandOnBattlefield(d.player1, "Wastes")
        d.tapForMana(d.player1, wastes)
        assertEquals(2, d.manaPool(d.player1).colorless)
    }

    @Test fun nonLandPermanentsTappedForColorlessAlsoCount() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val ring = d.putPermanentOnBattlefield(d.player1, "Sol Ring")
        d.tapForMana(d.player1, ring)
        assertEquals("Sol Ring {C}{C} plus exactly one additional {C}", 3, d.manaPool(d.player1).colorless)
    }

    @Test fun coloredManaAndOpponentsTapsGetNoBonus() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val plains = d.putLandOnBattlefield(d.player1, "Plains")
        d.tapForMana(d.player1, plains)
        assertEquals(1, d.manaPool(d.player1).white)
        assertEquals(0, d.manaPool(d.player1).colorless)
        val theirWastes = d.putLandOnBattlefield(d.player2, "Wastes")
        // The active player retains priority after activating a mana ability.
        // Give the opponent priority before exercising their mana ability.
        assertEquals(d.player1, d.priorityPlayer)
        assertNull(d.passPriority(d.player1).error)
        assertEquals(d.player2, d.priorityPlayer)
        val action = d.abilityActions(d.player2, theirWastes).firstOrNull()?.action as? ActivateAbility
        assertNotNull("opponent must have a legal Wastes mana action at priority", action)
        assertNull(d.submit(action!!).error)
        d.resolveAll()
        assertEquals("opponent's {C} is not doubled", 1, d.manaPool(d.player2).colorless)
    }

    @Test fun withoutMonumentNoExtraMana() {
        val d = setup()
        val wastes = d.putLandOnBattlefield(d.player1, "Wastes")
        d.tapForMana(d.player1, wastes)
        assertEquals(1, d.manaPool(d.player1).colorless)
    }

    @Test fun castingAColorlessSpellGainsTwoLife() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val before = d.getLifeTotal(d.player1)
        val probe = d.putCardInHand(d.player1, "Monument Colorless Probe")
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, probe).error)
        d.resolveStack()
        assertEquals(before + 2, d.getLifeTotal(d.player1))
    }

    @Test fun colorlessNonCreatureSpellAlsoGainsTwoLife() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val before = d.getLifeTotal(d.player1)
        val ring = d.putCardInHand(d.player1, "Sol Ring")
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, ring).error)
        d.resolveStack()
        assertEquals("a colorless artifact spell also triggers Monument", before + 2, d.getLifeTotal(d.player1))
    }

    @Test fun opponentsMonumentDoesNotTriggerFromMyColorlessCast() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player2, "Forsaken Monument")
        val theirLife = d.getLifeTotal(d.player2)
        val myLife = d.getLifeTotal(d.player1)
        val probe = d.putCardInHand(d.player1, "Monument Colorless Probe")
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, probe).error)
        d.resolveStack()
        assertEquals("opponent's Monument cannot gain life from my spell", theirLife, d.getLifeTotal(d.player2))
        assertEquals("I do not control a Monument", myLife, d.getLifeTotal(d.player1))
    }

    @Test fun manaBonusEndsWhenMonumentLeavesBattlefield() {
        val d = setup()
        val monument = d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val first = d.putLandOnBattlefield(d.player1, "Wastes")
        d.tapForMana(d.player1, first)
        assertEquals(2, d.manaPool(d.player1).colorless)
        d.moveToGraveyard(monument)
        val second = d.putLandOnBattlefield(d.player1, "Wastes")
        d.tapForMana(d.player1, second)
        assertEquals("the second tap gets no additional {C} after Monument leaves", 3, d.manaPool(d.player1).colorless)
    }

    @Test fun castTriggerSurvivesMonumentLeavingBeforeItResolves() {
        val d = setup()
        val monument = d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val before = d.getLifeTotal(d.player1)
        val probe = d.putCardInHand(d.player1, "Monument Colorless Probe")
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, probe).error)
        // A cast trigger already created on the stack must survive losing its source.
        d.moveToGraveyard(monument)
        d.resolveStack()
        assertEquals("the existing cast trigger survives Monument leaving", before + 2, d.getLifeTotal(d.player1))
    }

    @Test fun colorlessCastAfterMonumentLeavesCannotCreateItsLifeTrigger() {
        val d = setup()
        val monument = d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        d.moveToGraveyard(monument)
        val before = d.getLifeTotal(d.player1)
        val probe = d.putCardInHand(d.player1, "Monument Colorless Probe")
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, probe).error)
        d.resolveStack()
        assertEquals("a later cast cannot trigger a Monument in the graveyard", before, d.getLifeTotal(d.player1))
    }

    @Test fun devoidCreatureIsColorlessDespiteRedManaCost() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val before = d.getLifeTotal(d.player1)
        val devoid = d.putCardInHand(d.player1, "Monument Devoid Probe")
        d.giveColorlessMana(d.player1, 1)
        d.giveMana(d.player1, Color.RED, 1)
        assertNull(d.castSpell(d.player1, devoid).error)
        d.resolveStack()
        assertEquals("Devoid is colorless even when its casting cost contains {R}", before + 2, d.getLifeTotal(d.player1))
        val permanent = d.findPermanent(d.player1, "Monument Devoid Probe")
        assertNotNull("Devoid creature resolves to battlefield", permanent)
        assertEquals("Devoid creature gets the colorless anthem", 3 to 3, d.stats(permanent!!))
    }

    @Test fun coloredEldraziWithoutDevoidDoesNotTriggerOrReceiveAnthem() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val before = d.getLifeTotal(d.player1)
        val colored = d.putCardInHand(d.player1, "Monument Colored Eldrazi Probe")
        d.giveColorlessMana(d.player1, 1)
        d.giveMana(d.player1, Color.RED, 1)
        assertNull(d.castSpell(d.player1, colored).error)
        d.resolveStack()
        assertEquals("Eldrazi subtype alone does not make a spell colorless", before, d.getLifeTotal(d.player1))
        val permanent = d.findPermanent(d.player1, "Monument Colored Eldrazi Probe")
        assertNotNull("colored Eldrazi resolves to battlefield", permanent)
        assertEquals("colored Eldrazi does not receive the anthem", 1 to 1, d.stats(permanent!!))
    }

    @Test fun coloredSpellsGainNoLife() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Forsaken Monument")
        val before = d.getLifeTotal(d.player1)
        val bear = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN, 2)
        assertNull(d.castSpell(d.player1, bear).error)
        d.resolveStack()
        assertEquals(before, d.getLifeTotal(d.player1))
    }

    @Test fun monumentItselfIsAColorlessSpellButDoesNotSeeItsOwnCast() {
        val d = setup()
        val before = d.getLifeTotal(d.player1)
        val monument = d.putCardInHand(d.player1, "Forsaken Monument")
        d.giveColorlessMana(d.player1, 5)
        assertNull(d.castSpell(d.player1, monument).error)
        d.resolveStack()
        assertEquals("its trigger only works on the battlefield", before, d.getLifeTotal(d.player1))
        d.assertPermanentExists(d.player1, "Forsaken Monument")
    }
}
