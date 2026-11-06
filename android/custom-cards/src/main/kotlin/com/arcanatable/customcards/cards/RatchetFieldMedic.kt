package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Ratchet, Field Medic // Ratchet, Rescue Racer (Transformers #2).
 *
 * Needs engine patch 0041: More Than Meets the Eye (cast converted from hand / command zone),
 * Living metal, and "you may convert ~. When you do" not firing once the conversion is ignored
 * (CR 701.28f / 603.12). "Convert" is `Effects.Transform` (CR 701.28).
 */
private val RatchetFieldMedicFront = card("Ratchet, Field Medic") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Legendary Artifact Creature — Robot"
    power = 2
    toughness = 4
    oracleText = "More Than Meets the Eye {1}{W} (You may cast this card converted for {1}{W}.)\n" +
        "Lifelink\n" +
        "Whenever you gain life, you may convert Ratchet. When you do, return target artifact card with mana value less than or equal to the amount of life you gained this turn from your graveyard to the battlefield tapped."

    moreThanMeetsTheEye("{1}{W}")
    keywords(Keyword.LIFELINK)

    triggeredAbility {
        trigger = Triggers.you.gainsLife()
        effect = Effects.ReflexiveTrigger(
            action = Effects.Transform(EffectTarget.Self),
            optional = true,
            descriptionOverride = "When you do, return target artifact card with mana value less than or equal to the amount of life you gained this turn from your graveyard to the battlefield tapped.",
        ) {
            val artifact = target(
                TargetFilter(
                    GameObjectFilter.Artifact.ownedByYou().manaValueAtMostDynamic(DynamicAmounts.lifeGainedThisTurn(Player.You)),
                    zone = Zone.GRAVEYARD,
                )
            )
            effect = Effects.PutOntoBattlefield(artifact, tapped = true)
        }
        description = "Whenever you gain life, you may convert Ratchet. When you do, return target artifact card with mana value less than or equal to the amount of life you gained this turn from your graveyard to the battlefield tapped."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2"
        artist = "Volta Creation"
        imageUri = "https://cards.scryfall.io/normal/front/c/2/c23a2d6b-43c2-4756-94c7-207cc344f0a7.jpg"
    }
}

private val RatchetRescueRacer = card("Ratchet, Rescue Racer") {
    manaCost = ""
    colorIndicator = "W"
    colorIdentity = "W"
    typeLine = "Legendary Artifact — Vehicle"
    power = 1
    toughness = 4
    oracleText = "Living metal (As long as it's your turn, this Vehicle is also a creature.)\n" +
        "Lifelink\n" +
        "Whenever one or more nontoken artifacts you control are put into a graveyard from the battlefield, convert Ratchet. This ability triggers only once each turn."

    livingMetal()
    keywords(Keyword.LIFELINK)

    triggeredAbility {
        trigger = Triggers.oneOrMore(GameObjectFilter.Artifact.nontoken()).die()
        oncePerTurn = true
        effect = Effects.Transform(EffectTarget.Self)
        description = "Whenever one or more nontoken artifacts you control are put into a graveyard from the battlefield, convert Ratchet. This ability triggers only once each turn."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2"
        artist = "Volta Creation"
        imageUri = "https://cards.scryfall.io/normal/back/c/2/c23a2d6b-43c2-4756-94c7-207cc344f0a7.jpg"
    }
}

val RatchetFieldMedic: CardDefinition = CardDefinition.doubleFacedPermanent(
    frontFace = RatchetFieldMedicFront,
    backFace = RatchetRescueRacer,
)
