package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Canoptek Scarab Swarm — Warhammer 40,000 Commander #150 (Necron Dynasties). {4} Artifact Creature —
 * Insect, 1/1.
 *
 * Flying. Feeder Mandibles — When this creature enters, exile target player's graveyard. For each
 * artifact or land card exiled this way, create a 1/1 colorless Insect artifact creature token with
 * flying.
 *
 * Angel of Finality's "exile target player's graveyard" (gather the target player's graveyard, exile it),
 * then Culmination of Studies' filtered count over the exiled collection. The filter is
 * `GameObjectFilter.ArtifactOrLand`, a single `Or(IsArtifact, IsLand)` predicate, so each card is counted
 * at most once. Official ruling (2022-10-07): "If an exiled card is both an artifact card and a land
 * card, you will create one token, not two." Any player may be targeted, including you.
 */
val CanoptekScarabSwarm = card("Canoptek Scarab Swarm") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Insect"
    power = 1
    toughness = 1
    oracleText = "Flying\nFeeder Mandibles — When this creature enters, exile target player's graveyard. " +
        "For each artifact or land card exiled this way, create a 1/1 colorless Insect artifact creature " +
        "token with flying."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.enters()
        target(Targets.Player)
        effect = Effects.Pipeline {
            val graveyard = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.TargetPlayer))
            exile(graveyard)
            val artifactsOrLands = filter(graveyard, GameObjectFilter.ArtifactOrLand)
            run(
                Effects.CreateToken(
                    count = artifactsOrLands.count,
                    power = 1,
                    toughness = 1,
                    creatureTypes = setOf("Insect"),
                    keywords = setOf(Keyword.FLYING),
                    name = "Insect",
                    artifactToken = true,
                )
            )
        }
        description = "Feeder Mandibles — When this creature enters, exile target player's graveyard. For each " +
            "artifact or land card exiled this way, create a 1/1 colorless Insect artifact creature token with flying."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "150"
        artist = "Alexey Kruglov"
        imageUri = "https://cards.scryfall.io/normal/front/3/c/3ce2021c-1422-4901-a554-2d4fab72c8e4.jpg?1783920826"
        ruling("2022-10-07", "If an exiled card is both an artifact card and a land card, you will create one token, not two.")
    }
}
