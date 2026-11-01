package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BastionProtector
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.KeywordAbility
import org.junit.Assert.*
import org.junit.Test

class BastionProtectorIndependentCertificationTest {
    private val commander = CardDefinition.creature(
        name = "Fixture Partner Commander",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Elf")),
        power = 2,
        toughness = 2,
        supertypes = setOf(Supertype.LEGENDARY),
        oracleText = "Partner",
    ).copy(keywordAbilities = listOf(KeywordAbility.partner()))
    private val partner = CardDefinition.creature(
        name = "Fixture Second Partner",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Wizard")),
        power = 2,
        toughness = 2,
        supertypes = setOf(Supertype.LEGENDARY),
        oracleText = "Partner",
    ).copy(keywordAbilities = listOf(KeywordAbility.partner()))
    private val opponentCommander = CardDefinition.creature(
        name = "Fixture Opponent Commander",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 2,
        toughness = 2,
        supertypes = setOf(Supertype.LEGENDARY),
    )

    private fun commanderScenario(): GameTestDriver {
        val d = customScenario(BastionProtector, "Murder", "Grizzly Bears")
        d.registerCards(listOf(commander, partner, opponentCommander))
        d.initMultiplayer(
            decks = listOf(
                Deck.of("Forest" to 49, "Island" to 49),
                Deck.of("Forest" to 50, "Island" to 49),
            ),
            startingPlayer = 1,
            format = Format.Commander(),
            commanders = listOf(commander.name, opponentCommander.name),
            partners = listOf(partner.name, null),
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        castCommander(d, d.player2, opponentCommander.name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player1)
        castCommander(d, d.player1, commander.name)
        castCommander(d, d.player1, partner.name)
        return d
    }

    private fun castCommander(d: GameTestDriver, player: EntityId, name: String): EntityId {
        val card = d.state.getZone(player, Zone.COMMAND).single { d.getCardName(it) == name }
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()
        return d.permanentsNamed(player, name).single()
    }

    private fun destroyWithMurder(d: GameTestDriver, target: EntityId) {
        val murder = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, murder, listOf(target)).error)
        d.resolveAll()
    }

    @Test
    fun castingRequiresTwoGenericAndOneWhiteAndEntersAsAThreeThree() {
        val noWhite = customScenario(BastionProtector)
        val noWhiteCard = noWhite.putCardInHand(noWhite.player1, "Bastion Protector")
        noWhite.giveColorlessMana(noWhite.player1, 3)
        assertNotNull(noWhite.castSpell(noWhite.player1, noWhiteCard).error)
        assertTrue(noWhite.zoneNames(noWhite.player1, Zone.HAND).contains("Bastion Protector"))

        val d = customScenario(BastionProtector)
        val player = d.player1
        val card = d.putCardInHand(player, "Bastion Protector")
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 1)
        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Bastion Protector"))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()
        val protector = d.permanentsNamed(player, "Bastion Protector").single()
        assertEquals(3, d.projected.getPower(protector))
        assertEquals(3, d.projected.getToughness(protector))
    }

    @Test
    fun buffsEveryCommanderYouControlButNotAnOrdinaryCreatureOrAnOpponentsCommander() {
        val d = commanderScenario()
        val player = d.player1
        val ownCommander = d.permanentsNamed(player, commander.name).single()
        val ownPartner = d.permanentsNamed(player, partner.name).single()
        val enemyCommander = d.permanentsNamed(d.player2, opponentCommander.name).single()
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val protector = d.putCreatureOnBattlefield(player, "Bastion Protector")

        assertEquals(4, d.projected.getPower(ownCommander))
        assertEquals(4, d.projected.getToughness(ownCommander))
        assertEquals(4, d.projected.getPower(ownPartner))
        assertEquals(4, d.projected.getToughness(ownPartner))
        assertEquals(2, d.projected.getPower(enemyCommander))
        assertEquals(2, d.projected.getToughness(enemyCommander))
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(2, d.projected.getToughness(bear))
        assertEquals(3, d.projected.getPower(protector))
        assertEquals(3, d.projected.getToughness(protector))
    }

    @Test
    fun indestructibleProtectsACommanderYouControlFromDestruction() {
        val d = commanderScenario()
        val player = d.player1
        val ownCommander = d.permanentsNamed(player, commander.name).single()
        d.putCreatureOnBattlefield(player, "Bastion Protector")

        destroyWithMurder(d, ownCommander)

        assertTrue(d.permanentsNamed(player, commander.name).contains(ownCommander))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains(commander.name))
    }

    @Test
    fun indestructibleDoesNotProtectAnOpponentsCommander() {
        val d = commanderScenario()
        val enemy = d.player2
        val enemyCommander = d.permanentsNamed(enemy, opponentCommander.name).single()
        d.putCreatureOnBattlefield(d.player1, "Bastion Protector")

        destroyWithMurder(d, enemyCommander)

        assertFalse(d.permanentsNamed(enemy, opponentCommander.name).contains(enemyCommander))
    }

    @Test
    fun indestructibleDoesNotProtectAnOrdinaryCreatureYouControl() {
        val d = commanderScenario()
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(player, "Bastion Protector")

        destroyWithMurder(d, bear)

        assertFalse(d.permanentsNamed(player, "Grizzly Bears").contains(bear))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }
}
