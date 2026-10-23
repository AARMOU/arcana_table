package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.WardCost
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player

val RaffineSchemingSeer = card("Raffine, Scheming Seer") {
    manaCost = "{W}{U}{B}"
    colorIdentity = "WUB"
    typeLine = "Legendary Creature — Sphinx Demon"
    power = 1
    toughness = 4
    oracleText = "Flying, ward {1}\n" +
        "Whenever you attack, target attacking creature connives X, where X is the number of attacking creatures."

    keywords(Keyword.FLYING)
    keywordAbility(KeywordAbility.Ward(WardCost.Mana("{1}")))

    triggeredAbility {
        trigger = Triggers.you.attacks()
        val attacker = target(TargetFilter.AttackingCreature)
        effect = Effects.Connive(
            attacker,
            DynamicAmounts.battlefield(Player.Each, GameObjectFilter.Creature.attacking()).count(),
        )
        description = "Whenever you attack, target attacking creature connives X, where X is the number of attacking creatures."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "213"
        artist = "Johannes Voss"
        imageUri = "https://api.scryfall.com/cards/snc/213?format=image&version=normal"
    }
}
