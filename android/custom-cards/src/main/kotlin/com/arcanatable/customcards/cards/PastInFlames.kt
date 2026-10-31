package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val PastInFlames = card("Past in Flames") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost.\nFlashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    spell {
        effect = Effects.Pipeline {
            val spells = gather(
                CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.InstantOrSorcery),
            )
            run(
                Effects.ForEachInCollection(
                    spells,
                    Effects.GrantFlashback(EffectTarget.IterationEntity, null, Duration.EndOfTurn),
                ),
            )
        }
    }

    keywordAbility(KeywordAbility.flashback("{4}{R}"))

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "105"
        artist = "Anna Steinbauer"
        imageUri = "https://cards.scryfall.io/normal/front/2/b/2b7472f4-37b0-439f-b4ac-80706d40d191.jpg?1783936642"
    }
}
