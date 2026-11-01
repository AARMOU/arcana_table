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
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CharacteristicValue
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.KeywordAbility
import org.junit.Assert.*
import org.junit.Test

class TangleweaveArmorIndependentCertificationTest {
    private val name = "Tangleweave Armor"
    // Test-only legendary cards isolate Tangleweave's real commander-mana-value logic
    // from unrelated cards absent from the B140 runtime (Koma and Thrasios).
    private val sourceCommander = CardDefinition.creature(
        name = "Fixture Seven-Mana Commander",
        manaCost = ManaCost.parse("{5}{G}{U}"),
        subtypes = setOf(Subtype("Beast")),
        power = 7,
        toughness = 7,
        supertypes = setOf(Supertype.LEGENDARY),
    )
    private val fixturePartnerTwo = CardDefinition.creature(
        name = "Fixture Two-Mana Partner",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Elf")),
        power = 2,
        toughness = 2,
        supertypes = setOf(Supertype.LEGENDARY),
        oracleText = "Partner",
    ).copy(keywordAbilities = listOf(KeywordAbility.partner()))
    private val fixturePartnerFour = CardDefinition.creature(
        name = "Fixture Four-Mana Partner",
        manaCost = ManaCost.parse("{3}{U}"),
        subtypes = setOf(Subtype("Wizard")),
        power = 4,
        toughness = 4,
        supertypes = setOf(Supertype.LEGENDARY),
        oracleText = "Partner",
    ).copy(keywordAbilities = listOf(KeywordAbility.partner()))
    private val fixtureOpponentSeven = CardDefinition.creature(
        name = "Fixture Opponent Seven-Mana Commander",
        manaCost = ManaCost.parse("{5}{G}{U}"),
        subtypes = setOf(Subtype("Beast")),
        power = 7,
        toughness = 7,
        supertypes = setOf(Supertype.LEGENDARY),
    )

    private fun commanderScenario(
        ownCommander: CardDefinition,
        opponentCommander: CardDefinition = ownCommander,
        ownPartner: CardDefinition? = null,
        libraryCards: List<String> = emptyList(),
    ): GameTestDriver {
        val d = customScenario(TangleweaveArmor, *libraryCards.toTypedArray())
        // Register actual SDK definitions: no mocked mana value, partner status or game state.
        d.registerCards(listOfNotNull(ownCommander, ownPartner, opponentCommander).distinctBy { it.name })
        val ownDeck = if (ownPartner == null) {
            Deck.of("Forest" to 50, "Island" to 49)
        } else {
            Deck.of("Forest" to 49, "Island" to 49)
        }
        val opponentDeck = Deck.of("Forest" to 50, "Island" to 49)
        d.initMultiplayer(
            decks = listOf(ownDeck, opponentDeck),
            format = Format.Commander(),
            commanders = listOf(ownCommander.name, opponentCommander.name),
            partners = listOf(ownPartner?.name, null),
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
        assertEquals(Color.BLACK.toString(), germColors.single().toString())
        val germCard = d.state.getEntity(germ)!!.get<CardComponent>()
        assertNotNull(germCard?.baseStats)
        assertEquals(CharacteristicValue.Fixed(0), germCard!!.baseStats!!.power)
        assertEquals(CharacteristicValue.Fixed(0), germCard.baseStats!!.toughness)
        assertEquals(7, d.projected.getPower(germ))
        assertEquals(7, d.projected.getToughness(germ))
        assertAttachedTo(d, armor, germ)
    }

    @Test
    fun bonusUsesTheGreatestManaValueAmongYourCommandersOnly() {
        val ownCommander = fixturePartnerTwo
        val ownPartner = fixturePartnerFour
        val opponentCommander = fixtureOpponentSeven
        val d = commanderScenario(ownCommander, opponentCommander, ownPartner, listOf("Grizzly Bears"))

        val player = d.player1
        val ownCommanders = d.zoneNames(player, Zone.COMMAND)
        assertTrue(ownCommanders.contains(ownCommander.name))
        assertTrue(ownCommanders.contains(ownPartner.name))
        assertTrue(d.zoneNames(d.player2, Zone.COMMAND).contains(opponentCommander.name))

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
