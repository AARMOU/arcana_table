package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player

/** Celestine, the Living Saint — 40K #10. */
val CelestineTheLivingSaint = card("Celestine, the Living Saint") {
    manaCost = "{4}{W}"
    colorIdentity = "W"
    typeLine = "Legendary Creature — Human Warrior"
    power = 3
    toughness = 4
    oracleText = "Flying, lifelink\nHealing Tears — At the beginning of your end step, return target creature card with mana value X or less from your graveyard to the battlefield, where X is the amount of life you gained this turn."

    keywords(Keyword.FLYING, Keyword.LIFELINK)

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        val creatureCard = target(
            TargetFilter(
                GameObjectFilter.Creature.ownedByYou()
                    .manaValueAtMostDynamic(DynamicAmounts.lifeGainedThisTurn(Player.You)),
                zone = Zone.GRAVEYARD,
            ),
        )
        effect = Effects.PutOntoBattlefield(creatureCard)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "10"
        artist = "Irina Nordsol"
        imageUri = "https://cards.scryfall.io/normal/front/a/0/a099cbd2-e92a-433a-bc51-0aa2f8fc6857.jpg?1783920947"
    }
}
