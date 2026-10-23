package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Includes itself: the artifact-creature entering can cause this trigger once it is
 * on the battlefield. Later artifacts under its controller grant first strike again.
 */
val SkorpekhDestroyer = card("Skorpekh Destroyer") {
    manaCost = "{2}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 4
    toughness = 2
    oracleText = "Deathtouch\nHyperphase Threshers — Whenever an artifact enters under your control, " +
        "this creature gains first strike until end of turn."

    keywords(Keyword.DEATHTOUCH)

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Artifact.youControl()).enters()
        effect = Effects.GrantKeyword(Keyword.FIRST_STRIKE, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "57"
        artist = "Games Workshop"
    }
}
