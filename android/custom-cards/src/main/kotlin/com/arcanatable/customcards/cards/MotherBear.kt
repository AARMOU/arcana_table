package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.values.*

val MotherBear = card("Mother Bear") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Bear"
    power = 2
    toughness = 2
    oracleText = "{3}{G}{G}, Exile this card from your graveyard: Create two 2/2 green Bear creature tokens. " +
        "Activate only as a sorcery."

    activatedAbility {
        activateFromZone = Zone.GRAVEYARD
        cost = Costs.Composite(Costs.Mana("{3}{G}{G}"), Costs.ExileSelf)
        timing = TimingRule.SorcerySpeed
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Bear"),
            count = 2,
        )
        description = "{3}{G}{G}, Exile this card from your graveyard: Create two 2/2 green Bear creature tokens. " +
            "Activate only as a sorcery."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "171"
        artist = "Svetlin Velinov"
        imageUri = "https://cards.scryfall.io/normal/front/e/f/efae4d84-8134-461a-a352-a5bdff7259a7.jpg?1783933094"
    }
}
