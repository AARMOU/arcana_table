package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val Malanthrope = card("Malanthrope") {
    manaCost = "{1}{G}{U}"
    colorIdentity = "GU"
    typeLine = "Creature — Tyranid"
    power = 2
    toughness = 2
    oracleText = "Flying\nScavenge the Dead — When this creature enters, exile target player's graveyard. Put a +1/+1 counter on this creature for each creature card exiled this way."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.enters()
        target(Targets.Player)
        effect = Effects.Pipeline {
            val graveyard = gather(
                CardSource.FromZone(Zone.GRAVEYARD, Player.TargetPlayer, GameObjectFilter.Any),
                name = "malanthropeTargetGraveyard",
            )
            val exiled = moveTracked(
                graveyard,
                CardDestination.ToZone(Zone.EXILE, Player.TargetPlayer),
            )
            val creatures = filter(exiled, GameObjectFilter.Creature, name = "malanthropeExiledCreatures")
            run(
                Effects.AddDynamicCounters(
                    CounterType.PLUS_ONE_PLUS_ONE,
                    DynamicAmounts.distinctEntitiesIn(creatures),
                    EffectTarget.Self,
                ),
            )
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "132"
        artist = "Ørjan Ruttenborg Svendsen"
        imageUri = "https://cards.scryfall.io/normal/front/2/8/28d4eb51-a6ec-488a-a79a-ec4676d7af52.jpg?1783920843"
    }
}
