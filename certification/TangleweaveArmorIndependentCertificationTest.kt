package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TangleweaveArmor
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class TangleweaveArmorIndependentCertificationTest {
    private val name = "Tangleweave Armor"
    private val sourceCommander = "Koma, Cosmos Serpent"

    private fun commanderScenario(
        ownCommander: String,
        opponentCommander: String = ownCommander,
        ownPartner: String? = null,
        libraryCards: List<String> = emptyList(),
    ): GameTestDriver {
        val cards = listOfNotNull(ownCommander, ownPartner, opponentCommander) + libraryCards
        val d = customScenario(TangleweaveArmor, *cards.toTypedArray())
        val ownDeck = if (ownPartner == null) {
            Deck.of("Forest" to 50, "Island" to 49)
        } else {
            Deck.of("Forest" to 33, "Island" to 33, "Plains" to 32)
        }
        val opponentDeck = Deck.of("Forest" to 50, "Island" to 49)
        d.initMultiplayer(
            decks = listOf(ownDeck, opponentDeck),
            format = Format.Commander(),
            commanders = listOf(ownCommander, opponentCommander),
            partners = listOf(ownPartner, null),
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    private fun germTokens(d: GameTestDriver, player: EntityId): List<EntityId> =
        d.getPermanents(player).filter { token ->
            d.state.getEntity(token)?.has<TokenComponent>() == true &&
                d.projected.hasType(token, "CREATURE") &&
                d.projected.hasSubtype(token, "PHYREXIAN") &&
                d.projected.hasSubtype(token, "GERM")
        }

    private fun castArmor(d: GameTestDriver, player: EntityId): EntityId {
        val armor = d.putCardInHand(player, name)
        d.giveMana(player, Color.GREEN, 2)
        d.giveColorlessMana(player, 2)
        assertNull(d.castSpell(player, armor).error)
        d.resolveAll()
        return armor
    }

    private fun equip(d: GameTestDriver, player: EntityId, armor: EntityId, creature: EntityId) =
        d.submit(
            ActivateAbility(
                player,
                armor,
                d.cardRegistry.getCard(name)!!.script.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(creature)),
            )
        )

    private fun assertAttachedTo(d: GameTestDriver, armor: EntityId, creature: EntityId) {
        assertEquals(creature, d.state.getEntity(armor)?.get<AttachedToComponent>()?.targetId)
    }

    @Test
    fun castingRequiresTwoGreenAndTwoGenericManaAndLivingWeaponCreatesAndAttachesAGerm() {
        val wrongColors = customScenario(TangleweaveArmor)
        val player = wrongColors.player1
        val unpaidArmor = wrongColors.putCardInHand(player, name)
        wrongColors.giveMana(player, Color.GREEN)
        wrongColors.giveColorlessMana(player, 3)
        assertNotNull(wrongColors.castSpell(player, unpaidArmor).error)
        assertTrue(wrongColors.zoneNames(player, Zone.HAND).contains(name))

        val d = commanderScenario(sourceCommander)
        val p = d.player1
        val armor = castArmor(d, p)
        val germ = germTokens(d, p).single()

        assertTrue(d.permanentsNamed(p, name).contains(armor))
        assertTrue(d.state.getEntity(germ)!!.has<TokenComponent>())
        assertTrue(d.projected.hasType(germ, "CREATURE"))
        assertTrue(d.projected.hasSubtype(germ, "PHYREXIAN"))
        assertTrue(d.projected.hasSubtype(germ, "GERM"))
        val germColors = d.projected.getColors(germ)
        assertEquals(1, germColors.size)
        assertTrue(germColors.contains(Color.BLACK))
        val germCard = d.state.getEntity(germ)!!.get<CardComponent>()
        assertNotNull(germCard?.baseStats)
        assertEquals(0, germCard!!.baseStats!!.power)
        assertEquals(0, germCard.baseStats!!.toughness)
        assertEquals(7, d.projected.getPower(germ))
        assertEquals(7, d.projected.getToughness(germ))
        assertAttachedTo(d, armor, germ)
    }

    @Test
    fun bonusUsesTheGreatestManaValueAmongYourCommandersOnly() {
        val ownCommander = "Thrasios, Triton Hero"
        val ownPartner = "Ishai, Ojutai Dragonspeaker"
        val opponentCommander = "Koma, Cosmos Serpent"
        val d = commanderScenario(ownCommander, opponentCommander, ownPartner, listOf("Grizzly Bears"))

        val player = d.player1
        val ownCommanders = d.zoneNames(player, Zone.COMMAND)
        assertTrue(ownCommanders.contains(ownCommander))
        assertTrue(ownCommanders.contains(ownPartner))
        assertTrue(d.zoneNames(d.player2, Zone.COMMAND).contains(opponentCommander))

        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val armor = castArmor(d, player)
        val germ = germTokens(d, player).single()
        assertEquals(4, d.projected.getPower(germ))
        assertEquals(4, d.projected.getToughness(germ))

        d.giveColorlessMana(player, 4)
        assertNull(equip(d, player, armor, creature).error)
        d.resolveAll()

        assertAttachedTo(d, armor, creature)
        assertFalse(d.getPermanents(player).contains(germ))
        assertEquals(6, d.projected.getPower(creature))
        assertEquals(6, d.projected.getToughness(creature))
    }

    @Test
    fun equipRequiresAllFourManaAndMovesTheEquipmentToYourCreature() {
        val d = commanderScenario(sourceCommander, libraryCards = listOf("Grizzly Bears"))
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val armor = castArmor(d, player)
        val germ = germTokens(d, player).single()
        d.giveColorlessMana(player, 3)

        assertNotNull(equip(d, player, armor, creature).error)
        assertEquals(3, d.manaPool(player).colorless)
        assertAttachedTo(d, armor, germ)

        d.giveColorlessMana(player, 1)
        assertNull(equip(d, player, armor, creature).error)
        d.resolveAll()

        assertAttachedTo(d, armor, creature)
        assertFalse(d.getPermanents(player).contains(germ))
        assertEquals(9, d.projected.getPower(creature))
        assertEquals(9, d.projected.getToughness(creature))
    }

    @Test
    fun equipCannotTargetAnOpponentControlledCreature() {
        val d = commanderScenario(sourceCommander, libraryCards = listOf("Grizzly Bears"))
        val player = d.player1
        val opponent = d.player2
        val opponentCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val armor = castArmor(d, player)
        val germ = germTokens(d, player).single()
        d.giveColorlessMana(player, 4)

        assertNotNull(equip(d, player, armor, opponentCreature).error)
        assertEquals(4, d.manaPool(player).colorless)
        assertAttachedTo(d, armor, germ)
    }

    @Test
    fun equipCanOnlyBeActivatedAtSorceryTiming() {
        val d = commanderScenario(sourceCommander, libraryCards = listOf("Grizzly Bears"))
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val armor = castArmor(d, player)
        val germ = germTokens(d, player).single()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.giveColorlessMana(player, 4)

        assertNotNull(equip(d, player, armor, creature).error)
        assertEquals(4, d.manaPool(player).colorless)
        assertAttachedTo(d, armor, germ)
    }
}
