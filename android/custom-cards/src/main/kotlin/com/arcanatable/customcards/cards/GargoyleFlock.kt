package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player

/** The end-step condition is checked both on triggering and again on resolution. */
val GargoyleFlock = card("Gargoyle Flock") {
    manaCost = "{2}{G}{U}"
    colorIdentity = "GU"
    typeLine = "Creature — Tyranid Gargoyle"
    power = 2
    toughness = 2
    oracleText = "Flying\nSkyswarm — At the beginning of your end step, if a creature entered the battlefield " +
        "under your control this turn, create a 1/1 blue Tyranid Gargoyle creature token with flying."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        interveningIf = Conditions.CreaturesEnteredThisTurn(atLeast = 1, player = Player.You)
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            colors = setOf(Color.BLUE),
            creatureTypes = setOf("Tyranid", "Gargoyle"),
            keywords = setOf(Keyword.FLYING),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "123"
        artist = "Nikola Matkovic"
    }
}
