package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziDisplacer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*
import org.junit.Test

/** Every scenario round-trips the definition through the same JSON loader as Android. */
class EldraziDisplacerScenarioTest {
    private fun GameTestDriver.blink(source: EntityId, target: EntityId) = submit(ActivateAbility(
        player1, source, cardRegistry.requireCard("Eldrazi Displacer").script.activatedAbilities.single().id,
        targets = listOf(ChosenTarget.Permanent(target)),
    ))

    @Test fun devoidIsColorlessOnTheBattlefieldAndStackWithWhiteIdentity() {
        val d = customScenario(EldraziDisplacer)
        val definition = d.cardRegistry.requireCard("Eldrazi Displacer")
        assertTrue(definition.colors.isEmpty())
        assertEquals(setOf(Color.WHITE), definition.colorIdentity)
        val card = d.putCardInHand(d.player1, definition.name)
        d.giveColorlessMana(d.player1, 2)
        assertNotNull("Devoid does not remove the white casting requirement", d.castSpell(d.player1, card).error)
        d.giveMana(d.player1, Color.WHITE)
        assertNull(d.castSpell(d.player1, card).error)
        assertTrue(d.projected.getColors(card).isEmpty())
        d.resolveAll()
        assertTrue(d.projected.getColors(card).isEmpty())
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
    }

    @Test fun ownCreatureReturnsTappedAsANewObjectWithCountersAndDamageCleared() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.addComponent(target, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2, CounterType.CHARGE to 3)))
        d.addComponent(target, DamageComponent(1))
        val oldObject = d.state.objectRef(target)
        d.giveColorlessMana(d.player1, 3)
        val castsBefore = d.events.filterIsInstance<SpellCastEvent>().size
        assertNull(d.blink(source, target).error)
        assertEquals("The activation uses the stack", 1, d.state.stack.size)
        assertEquals("It has not resolved yet", oldObject, d.state.objectRef(target))
        d.resolveAll()
        val returned = d.permanentsNamed(d.player1, "Grizzly Bears").single()
        assertNotEquals(oldObject, d.state.objectRef(returned))
        assertTrue(d.isTapped(returned))
        assertEquals(2, d.projected.getPower(returned))
        assertEquals(0, d.state.getEntity(returned)!!.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0)
        assertEquals(0, d.state.getEntity(returned)!!.get<DamageComponent>()?.amount ?: 0)
        assertEquals("Activating is not casting", castsBefore, d.events.filterIsInstance<SpellCastEvent>().size)
        assertFalse("There is no tap cost, even with summoning sickness", d.isTapped(source))
    }

    @Test fun opposingCreatureReturnsToItsOwnerTapped() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val before = d.state.objectRef(target)
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.blink(source, target).error)
        d.resolveAll()
        val returned = d.permanentsNamed(d.player2, "Grizzly Bears").single()
        assertNotEquals(before, d.state.objectRef(returned))
        assertEquals(d.player2, d.projected.getController(returned))
        assertTrue(d.isTapped(returned))
        assertTrue(d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
    }

    @Test fun stolenCreatureReturnsUnderItsOwnerRatherThanItsPreviousController() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        d.registerCard(card("Displacer Control Fixture") {
            manaCost = "{U}"
            typeLine = "Instant"
            spell {
                val creature = target(TargetFilter.Creature)
                effect = Effects.GainControl(creature, Duration.EndOfTurn)
            }
        })
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val steal = d.putCardInHand(d.player1, "Displacer Control Fixture")
        d.giveMana(d.player1, Color.BLUE)
        assertNull(d.castSpell(d.player1, steal, listOf(target)).error)
        d.resolveAll()
        assertEquals(d.player1, d.projected.getController(target))
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.blink(source, target).error)
        d.resolveAll()
        val returned = d.permanentsNamed(d.player2, "Grizzly Bears").single()
        assertEquals(d.player2, d.projected.getController(returned))
        assertTrue(d.isTapped(returned))
        assertTrue(d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
    }

    @Test fun coloredManaCannotPayTheRequiredColorlessButCanPayTheGenericPart() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.WHITE, 3)
        assertNotNull(d.blink(source, target).error)
        assertTrue(d.state.stack.isEmpty())
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.blink(source, target).error)
        d.resolveAll()
        assertTrue(d.isTapped(d.permanentsNamed(d.player1, "Grizzly Bears").single()))
    }

    @Test fun totalCostStillRequiresThreeMana() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 2)
        assertNotNull(d.blink(source, target).error)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.blink(source, target).error)
        d.resolveAll()
        assertNotNull("The paid mana was consumed", d.blink(source, target).error)
    }

    @Test fun anotherRejectsSelfAndNoncreaturesWithoutSpendingMana() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears", "Sol Ring")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(d.player1, "Sol Ring")
        d.giveColorlessMana(d.player1, 3)
        assertNotNull(d.blink(source, source).error)
        assertNotNull(d.blink(source, ring).error)
        assertNull(d.blink(source, target).error)
        d.resolveAll()
    }

    @Test fun opponentsHexproofAndShroudPreventTargetingButOwnHexproofDoesNot() {
        val d = customScenario(EldraziDisplacer)
        for (keyword in listOf(Keyword.HEXPROOF, Keyword.SHROUD)) {
            d.registerCard(card("Displacer $keyword Fixture") {
                typeLine = "Creature — Beast"
                power = 2; toughness = 2
                keywords(keyword)
            })
        }
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val hexproof = d.putCreatureOnBattlefield(d.player2, "Displacer HEXPROOF Fixture")
        val shroud = d.putCreatureOnBattlefield(d.player1, "Displacer SHROUD Fixture")
        val ownHexproof = d.putCreatureOnBattlefield(d.player1, "Displacer HEXPROOF Fixture")
        d.giveColorlessMana(d.player1, 3)
        assertNotNull(d.blink(source, hexproof).error)
        assertNotNull(d.blink(source, shroud).error)
        assertNull(d.blink(source, ownHexproof).error)
        d.resolveAll()
        assertTrue(d.isTapped(d.permanentsNamed(d.player1, "Displacer HEXPROOF Fixture").single()))
    }

    @Test fun exiledTokenCeasesToExistAndNeverReenters() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val token = d.putToken(d.player2, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.blink(source, token).error)
        d.resolveAll()
        assertNull(d.state.getEntity(token))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).isEmpty())
    }

    @Test fun targetLeavingBeforeResolutionIsNotReturnedFromItsNewZone() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears", "Unsummon")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.blink(source, target).error)
        assertNull(d.passPriority(d.player1).error)
        val unsummon = d.putCardInHand(d.player2, "Unsummon")
        d.giveMana(d.player2, Color.BLUE)
        assertNull(d.castSpell(d.player2, unsummon, listOf(target)).error)
        d.resolveAll()
        assertTrue(d.zoneNames(d.player2, Zone.HAND).contains("Grizzly Bears"))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
    }

    @Test fun targetBlinkedInResponseIsANewObjectAndIsNotBlinkedAgain() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears", "Flicker of Fate")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val oldObject = d.state.objectRef(target)
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.blink(source, target).error)
        assertNull(d.passPriority(d.player1).error)
        val flicker = d.putCardInHand(d.player2, "Flicker of Fate")
        d.giveMana(d.player2, Color.WHITE, 2)
        assertNull(d.castSpell(d.player2, flicker, listOf(target)).error)
        d.bothPass()
        val returned = d.permanentsNamed(d.player2, "Grizzly Bears").single()
        val newObject = d.state.objectRef(returned)
        assertNotEquals(oldObject, newObject)
        assertFalse(d.isTapped(returned))
        d.resolveAll()
        assertEquals("The old activation cannot follow a target across a new zone visit", newObject, d.state.objectRef(returned))
        assertFalse("Displacer fizzled rather than tapping the new object", d.isTapped(returned))
    }

    @Test fun auraGoesToGraveyardAndEquipmentStaysUnattached() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears", "Holy Strength", "Bonesplitter")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val aura = d.putCardInHand(d.player1, "Holy Strength")
        d.giveMana(d.player1, Color.WHITE)
        assertNull(d.castSpell(d.player1, aura, listOf(target)).error)
        d.resolveAll()
        val equipment = d.putCardInHand(d.player1, "Bonesplitter")
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, equipment).error)
        d.resolveAll()
        val equip = d.cardRegistry.requireCard("Bonesplitter").script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(d.player1, equipment, equip.id, targets = listOf(ChosenTarget.Permanent(target)))).error)
        d.resolveAll()
        assertEquals(target, d.state.getEntity(aura)!!.get<AttachedToComponent>()?.targetId)
        assertEquals(target, d.state.getEntity(equipment)!!.get<AttachedToComponent>()?.targetId)
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.blink(source, target).error)
        d.resolveAll()
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Holy Strength"))
        assertTrue(d.getPermanents(d.player1).contains(equipment))
        assertNull(d.state.getEntity(equipment)!!.get<AttachedToComponent>())
        assertEquals(2, d.projected.getPower(d.permanentsNamed(d.player1, "Grizzly Bears").single()))
    }

    @Test fun reentryRunsTheCreaturesEnterTrigger() {
        val d = customScenario(EldraziDisplacer, "Elvish Visionary")
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player1, "Elvish Visionary")
        val before = d.getHand(d.player1).size
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.blink(source, target).error)
        d.resolveAll()
        assertEquals(before + 1, d.getHand(d.player1).size)
        assertTrue(d.isTapped(d.permanentsNamed(d.player1, "Elvish Visionary").single()))
    }
}
