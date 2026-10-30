package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val Psychomancer = card("Psychomancer") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron Wizard"
    power = 1
    toughness = 1
    oracleText = "Flying\nHarbinger of Despair — Whenever this creature or another nontoken artifact you control is put into a graveyard from the battlefield or is put into exile from the battlefield, target opponent loses 1 life and you gain 1 life."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.or(Triggers.self.dies(), Triggers.self.leaves(to = Zone.EXILE))
        val opponent = target(Targets.Opponent)
        effect = Effects.LoseLife(1, opponent) then Effects.GainLife(1)
    }

    val otherNontokenArtifact = GameObjectFilter.Artifact.nontoken().youControl()
    triggeredAbility {
        trigger = Triggers.or(
            Triggers.another(otherNontokenArtifact).dies(),
            Triggers.another(otherNontokenArtifact).leaves(to = Zone.EXILE)
        )
        val opponent = target(Targets.Opponent)
        effect = Effects.LoseLife(1, opponent) then Effects.GainLife(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "51"
        artist = "Alex Konstad"
        imageUri = "https://cards.scryfall.io/normal/front/7/8/780743bf-e1a0-4114-9474-319632e0c775.jpg?1783920912"
    }
}
