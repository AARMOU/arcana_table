package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * Imotekh the Stormlord — Warhammer 40,000 Commander #5 (Necron Dynasties). {2}{B}{B} Legendary Artifact
 * Creature — Necron, 3/3.
 *
 * Phaeron — Whenever one or more artifact cards leave your graveyard, create two 2/2 black Necron
 * Warrior artifact creature tokens.
 * Grand Strategist — At the beginning of combat on your turn, another target artifact creature you
 * control gets +2/+2 and gains menace until end of turn.
 *
 * Phaeron is the batching `Triggers.oneOrMore(GameObjectFilter.Artifact).leaveYourGraveyard()`, the
 * Desecrated Tomb shape. It triggers once for each batch of cards leaving at the same time, wherever they
 * go. Official ruling (2022-10-07): "If multiple artifact cards leave your graveyard at the same time,
 * the first ability triggers only once, and you will create only two tokens." It looks back in time
 * (CR 603.10a), so a card counts as the card it was in your graveyard. With Biotransference a creature
 * card there is an artifact card, so a creature card leaving triggers it. Engine patch 0039 records
 * those granted types as the card leaves.
 *
 * Grand Strategist is the Aethershield Artificer shape with `excludeSelf` ("another"). With no other
 * artifact creature you control, there is no legal target and the ability is removed from the stack
 * (CR 603.3d).
 */
val ImotekhTheStormlord = card("Imotekh the Stormlord") {
    manaCost = "{2}{B}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Artifact Creature — Necron"
    power = 3
    toughness = 3
    oracleText = "Phaeron — Whenever one or more artifact cards leave your graveyard, create two 2/2 black " +
        "Necron Warrior artifact creature tokens.\nGrand Strategist — At the beginning of combat on your " +
        "turn, another target artifact creature you control gets +2/+2 and gains menace until end of turn."

    triggeredAbility {
        trigger = Triggers.oneOrMore(GameObjectFilter.Artifact).leaveYourGraveyard()
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Necron", "Warrior"),
            name = "Necron Warrior",
            count = 2,
            artifactToken = true,
        )
    }

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        val creature = target(TargetFilter(GameObjectFilter.ArtifactCreature.youControl(), excludeSelf = true))
        effect = Effects.ModifyStats(2, 2, creature) then
            Effects.GrantKeyword(Keyword.MENACE, creature, Duration.EndOfTurn)
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "5"
        artist = "JB Casacop"
    }
}
