package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** One trigger per artifact leaving, including tokens and opponents' artifacts. */
val ShardOfTheVoidDragon = card("Shard of the Void Dragon") {
    manaCost = "{4}{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Creature — C'tan"
    power = 7
    toughness = 7
    oracleText = "Flying\nSpear of the Void Dragon — Whenever this creature attacks, each opponent sacrifices a nonland permanent of their choice.\nMatter Absorption — Whenever an artifact is put into a graveyard from the battlefield or is put into exile from the battlefield, put two +1/+1 counters on this creature."
    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.Sacrifice(GameObjectFilter.NonlandPermanent, 1, EffectTarget.PlayerRef(Player.EachOpponent))
    }
    triggeredAbility {
        trigger = Triggers.or(
            Triggers.a(GameObjectFilter.Artifact).dies(),
            Triggers.a(GameObjectFilter.Artifact).leaves(to = Zone.EXILE),
        )
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 2, EffectTarget.Self)
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "56"
        artist = "Alex Konstad"
        imageUri = "https://cards.scryfall.io/normal/front/4/1/41dd0b55-3dbb-4b65-a949-20c2a12f7edd.jpg?1783920909"
    }
}
