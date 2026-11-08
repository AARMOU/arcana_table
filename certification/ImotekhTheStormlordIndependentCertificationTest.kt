package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ImotekhTheStormlord
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ImotekhTheStormlordIndependentCertificationTest {
    private fun GameTestDriver.activateCrypt(
        player: EntityId,
        crypt: EntityId,
        target: EntityId,
    ) = submit(
        ActivateAbility(
            player,
            crypt,
            cardRegistry.getCard("Tormod's Crypt")!!.script.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Player(target)),
        ),
    )

    @Test
    fun castsForTwoGenericAndTwoBlackAndEntersAsAThreeThreeArtifactNecron() {
        val d = customScenario(ImotekhTheStormlord)
        val player = d.player1
        val card = d.putCardInHand(player, "Imotekh the Stormlord")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLACK, 2)

        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        val imotekh = d.permanentsNamed(player, "Imotekh the Stormlord").single()
        assertTrue(d.projected.hasType(imotekh, "ARTIFACT"))
        assertTrue(d.projected.isCreature(imotekh))
        assertTrue(d.projected.hasSubtype(imotekh, "NECRON"))
        assertEquals(3, d.projected.getPower(imotekh))
        assertEquals(3, d.projected.getToughness(imotekh))
    }

    @Test
    fun threeGenericAndOneBlackCannotPayTheTwoBlackSymbols() {
        val d = customScenario(ImotekhTheStormlord)
        val player = d.player1
        val card = d.putCardInHand(player, "Imotekh the Stormlord")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.castSpell(player, card).error)

        assertTrue(d.getHand(player).contains(card))
        assertTrue(d.permanentsNamed(player, "Imotekh the Stormlord").isEmpty())
    }

    @Test
    fun multipleArtifactCardsLeavingYourGraveyardTogetherCreateExactlyTwoNecronWarriors() {
        val d = customScenario(
            ImotekhTheStormlord,
            "Tormod's Crypt",
            "Sol Ring",
            "Ornithopter",
            "Grizzly Bears",
        )
        val player = d.player1
        val imotekh = d.putCreatureOnBattlefield(player, "Imotekh the Stormlord")
        val crypt = d.putPermanentOnBattlefield(player, "Tormod's Crypt")
        d.putCardInGraveyard(player, "Sol Ring")
        d.putCardInGraveyard(player, "Ornithopter")
        d.putCardInGraveyard(player, "Grizzly Bears")

        assertNull(d.activateCrypt(player, crypt, player).error)
        d.resolveAll()

        assertTrue(d.getGraveyardCardNames(player).isEmpty())
        assertEquals(
            listOf("Grizzly Bears", "Ornithopter", "Sol Ring", "Tormod's Crypt"),
            d.zoneNames(player, Zone.EXILE).sorted(),
        )
        val tokens = d.getPermanents(player).filter {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        }
        assertEquals(2, tokens.size)
        tokens.forEach { token ->
            assertTrue(d.projected.hasType(token, "ARTIFACT"))
            assertTrue(d.projected.isCreature(token))
            assertTrue(d.projected.hasSubtype(token, "NECRON"))
            assertTrue(d.projected.hasSubtype(token, "WARRIOR"))
            assertTrue(d.projected.hasColor(token, Color.BLACK))
            assertEquals(2, d.projected.getPower(token))
            assertEquals(2, d.projected.getToughness(token))
        }
        assertEquals(3, d.projected.getPower(imotekh))
    }

    @Test
    fun aNonartifactCardLeavingYourGraveyardDoesNotCreateTokens() {
        val d = customScenario(ImotekhTheStormlord, "Cremate", "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Imotekh the Stormlord")
        val bear = d.putCardInGraveyard(player, "Grizzly Bears")
        val cremate = d.putCardInHand(player, "Cremate")
        d.giveMana(player, Color.BLACK)

        assertNull(
            d.castSpellWithTargets(
                player,
                cremate,
                listOf(ChosenTarget.Card(bear, player, Zone.GRAVEYARD)),
            ).error,
        )
        d.resolveAll()

        assertEquals(listOf("Grizzly Bears"), d.zoneNames(player, Zone.EXILE))
        assertTrue(d.getPermanents(player).none {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        })
    }

    @Test
    fun artifactCardsLeavingAnOpponentsGraveyardDoNotCreateTokens() {
        val d = customScenario(ImotekhTheStormlord, "Tormod's Crypt", "Sol Ring")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Imotekh the Stormlord")
        val crypt = d.putPermanentOnBattlefield(player, "Tormod's Crypt")
        d.putCardInGraveyard(opponent, "Sol Ring")

        assertNull(d.activateCrypt(player, crypt, opponent).error)
        d.resolveAll()

        assertEquals(listOf("Sol Ring"), d.zoneNames(opponent, Zone.EXILE))
        assertTrue(d.getPermanents(player).none {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        })
    }

    @Test
    fun yourCombatGivesAnotherArtifactCreatureTwoTwoAndMenaceUntilEndOfTurn() {
        val d = customScenario(ImotekhTheStormlord, "Ornithopter")
        val player = d.player1
        val imotekh = d.putCreatureOnBattlefield(player, "Imotekh the Stormlord")
        val ornithopter = d.putCreatureOnBattlefield(player, "Ornithopter")

        d.passPriorityUntil(Step.BEGIN_COMBAT, activePlayer = player)
        d.resolveAll(Choices(targets = listOf(ornithopter)))

        assertEquals(2, d.projected.getPower(ornithopter))
        assertEquals(4, d.projected.getToughness(ornithopter))
        assertTrue(d.projected.hasKeyword(ornithopter, Keyword.MENACE))
        assertEquals(3, d.projected.getPower(imotekh))
        assertFalse(d.projected.hasKeyword(imotekh, Keyword.MENACE))

        d.passPriorityUntil(Step.END, activePlayer = player)
        assertEquals(2, d.projected.getPower(ornithopter))
        assertEquals(4, d.projected.getToughness(ornithopter))
        assertTrue(d.projected.hasKeyword(ornithopter, Keyword.MENACE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(0, d.projected.getPower(ornithopter))
        assertEquals(2, d.projected.getToughness(ornithopter))
        assertFalse(d.projected.hasKeyword(ornithopter, Keyword.MENACE))
    }

    @Test
    fun beginningOfAnOpponentsCombatDoesNotGiveTheBonus() {
        val d = customScenario(ImotekhTheStormlord, "Ornithopter")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Imotekh the Stormlord")
        val ornithopter = d.putCreatureOnBattlefield(player, "Ornithopter")

        d.passPriorityUntil(Step.BEGIN_COMBAT, activePlayer = opponent)
        d.resolveAll()

        assertEquals(0, d.projected.getPower(ornithopter))
        assertEquals(2, d.projected.getToughness(ornithopter))
        assertFalse(d.projected.hasKeyword(ornithopter, Keyword.MENACE))
    }

    @Test
    fun itCannotTargetItselfANonartifactCreatureOrAnOpponentsArtifactCreature() {
        val d = customScenario(ImotekhTheStormlord, "Grizzly Bears", "Ornithopter")
        val player = d.player1
        val opponent = d.player2
        val imotekh = d.putCreatureOnBattlefield(player, "Imotekh the Stormlord")
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val ownOrnithopter = d.putCreatureOnBattlefield(player, "Ornithopter")
        val opposingOrnithopter = d.putCreatureOnBattlefield(opponent, "Ornithopter")

        d.passPriorityUntil(Step.BEGIN_COMBAT, activePlayer = player)
        d.resolveAll(Choices(targets = listOf(ownOrnithopter)))

        assertEquals(3, d.projected.getPower(imotekh))
        assertEquals(3, d.projected.getToughness(imotekh))
        assertFalse(d.projected.hasKeyword(imotekh, Keyword.MENACE))
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(2, d.projected.getToughness(bear))
        assertFalse(d.projected.hasKeyword(bear, Keyword.MENACE))
        assertEquals(2, d.projected.getPower(ownOrnithopter))
        assertEquals(4, d.projected.getToughness(ownOrnithopter))
        assertTrue(d.projected.hasKeyword(ownOrnithopter, Keyword.MENACE))
        assertEquals(0, d.projected.getPower(opposingOrnithopter))
        assertEquals(2, d.projected.getToughness(opposingOrnithopter))
        assertFalse(d.projected.hasKeyword(opposingOrnithopter, Keyword.MENACE))
    }
}
