package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TangleweaveArmor
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class TangleweaveArmorScenarioTest {
    @Test
    fun livingWeaponUsesGreatestOwnedCommanderManaValueAndEquipTargetsOnlyYourCreature() {
        val d = customScenario(TangleweaveArmor, "Akroma, Angel of Wrath", "Grizzly Bears").apply {
            initMultiplayer(
                decks = List(2) { Deck.of("Plains" to 40) },
                format = Format.Commander(),
                commanders = listOf("Leonardo, the Balance", "Akroma, Angel of Wrath"),
                partners = listOf("Raphael, the Muscle", null),
            )
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val opponent = d.player2
        val armorCard = d.putCardInHand(p, "Tangleweave Armor")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, armorCard).error)
        d.resolveAll()

        val armor = d.findPermanent(p, "Tangleweave Armor")!!
        val germ = d.getPermanents(p).singleOrNull {
            d.getCardName(it)?.contains("Germ", ignoreCase = true) == true
        } ?: error("Living weapon did not leave a Germ: ${d.zoneNames(p, Zone.BATTLEFIELD)}")
        assertEquals(5, d.projected.getPower(germ))
        assertEquals(5, d.projected.getToughness(germ))

        val raphael = d.state.getZone(p, Zone.COMMAND).single { d.getCardName(it) == "Raphael, the Muscle" }
        d.moveToGraveyard(raphael)
        assertEquals(5, d.projected.getPower(germ))
        assertEquals(5, d.projected.getToughness(germ))

        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponentBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        assertEquals(2, d.projected.getPower(bear))
        d.giveColorlessMana(p, 4)
        val equip = d.cardRegistry.requireCard("Tangleweave Armor").script.activatedAbilities.single()

        val wrongControllerTarget = d.submit(
            ActivateAbility(p, armor, equip.id, targets = listOf(ChosenTarget.Permanent(opponentBear))),
        )
        assertNotNull(wrongControllerTarget.error)

        assertNull(
            d.submit(ActivateAbility(p, armor, equip.id, targets = listOf(ChosenTarget.Permanent(bear)))).error,
        )
        d.resolveAll()

        assertEquals(7, d.projected.getPower(bear))
        assertEquals(7, d.projected.getToughness(bear))
        assertTrue(d.getPermanents(p).none { d.getCardName(it)?.contains("Germ", ignoreCase = true) == true })
    }

    @Test
    fun livingWeaponGermDiesWhenYouHaveNoCommander() {
        val d = customScenario(TangleweaveArmor)
        val p = d.player1
        val armorCard = d.putCardInHand(p, "Tangleweave Armor")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, armorCard).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).none { d.getCardName(it)?.contains("Germ", ignoreCase = true) == true })
        assertNotNull(d.findPermanent(p, "Tangleweave Armor"))
    }
}
