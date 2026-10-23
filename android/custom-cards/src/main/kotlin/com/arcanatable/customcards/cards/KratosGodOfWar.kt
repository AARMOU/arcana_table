package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.references.Player

val KratosGodOfWar = card("Kratos, God of War") {
    manaCost = "{R}{R}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — God"
    power = 2
    toughness = 3
    oracleText = "Double strike\nAll creatures have haste.\nAt the beginning of each player's end step, Kratos deals damage to that player equal to the number of creatures that player controls that didn't attack this turn."

    keywords(Keyword.DOUBLE_STRIKE)

    staticAbility {
        ability = GrantKeyword(Keyword.HASTE, GroupFilter(GameObjectFilter.Creature))
    }

    triggeredAbility {
        trigger = Triggers.anyPlayer.beginningOf(Step.END)
        effect = Effects.DealDamage(
            DynamicAmounts.battlefield(Player.TriggeringPlayer, GameObjectFilter.Creature.didntAttackThisTurn()).count(),
            EffectTarget.PlayerRef(Player.TriggeringPlayer),
        )
        description = "At the beginning of each player's end step, Kratos deals damage to that player equal to the number of creatures that player controls that didn't attack this turn."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2207"
        artist = "Magali Villeneuve"
        imageUri = "https://cards.scryfall.io/normal/front/f/b/fb97e2a1-0e86-4c12-8d31-6ff7b825ca10.jpg?1783905133"
    }
}
