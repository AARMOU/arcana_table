package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.CardNumericProperty

/**
 * Benthic Anomaly (M3C #46).
 *
 * The cast trigger is on the stack above Benthic and resolves even if Benthic is countered. On
 * resolution, for each opponent (`forEachPlayerCollecting(Player.EachOpponent)`, where `Player.You`
 * is that opponent), Benthic's controller *chooses* (`Chooser.SourceController`, not a target, so
 * hexproof and shroud don't matter) exactly one creature that opponent controls; an opponent with no
 * creatures adds nothing. The loop returns every opponent's choice as one collection. The
 * controller then chooses which of those creatures to copy (forced when there is only one).
 *
 * The token copies that creature's copiable values with the listed exceptions, all part of the
 * copy (so other effects copying the token see them): base power and toughness are the totals of
 * the chosen creatures' current (projected) power and toughness, read through engine patch 0033
 * (`slot.sumOf(CardNumericProperty)`); it is colorless; and it is an Eldrazi creature *instead of*
 * its other card types and subtypes, keeping its supertypes (M3C release notes ruling, 2024-06-07).
 * Counters, Auras and pumps on the original are not copied, but they do count in the totals.
 */
val BenthicAnomaly = card("Benthic Anomaly") {
    manaCost = "{6}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Eldrazi Serpent"
    power = 7
    toughness = 8
    oracleText = "Devoid (This card has no color.)\n" +
        "When you cast this spell, for each opponent, choose a creature that player controls. " +
        "Create a token that's a copy of one of those creatures, except its power is equal to the " +
        "total power of those creatures, its toughness is equal to the total toughness of those " +
        "creatures, and it's a colorless Eldrazi creature."

    keywords(Keyword.DEVOID)

    triggeredAbility {
        trigger = Triggers.self.isCast()
        effect = Effects.Pipeline {
            val chosen = forEachPlayerCollecting(Player.EachOpponent) {
                val theirs = gather(GameObjectFilter.Creature, player = Player.You)
                listOf(
                    chooseExactly(
                        1,
                        from = theirs,
                        chooser = Chooser.SourceController,
                        prompt = "Choose a creature that player controls",
                    )
                )
            }.single()
            val copied = chooseExactly(
                1,
                from = chosen,
                chooser = Chooser.SourceController,
                prompt = "Choose which of those creatures to copy",
            )
            run(
                Effects.CreateTokenCopyOfTarget(
                    target = copied.asTarget,
                    basePower = chosen.sumOf(CardNumericProperty.POWER),
                    baseToughness = chosen.sumOf(CardNumericProperty.TOUGHNESS),
                    overrideColors = emptySet(),
                    overrideCardTypes = setOf(CardType.CREATURE),
                    overrideSubtypes = setOf(Subtype("Eldrazi")),
                )
            )
        }
        description = "When you cast this spell, for each opponent, choose a creature that player " +
            "controls. Create a token that's a copy of one of those creatures, except its power is " +
            "equal to the total power of those creatures, its toughness is equal to the total " +
            "toughness of those creatures, and it's a colorless Eldrazi creature."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "46"
        artist = "Nino Is"
        imageUri = "https://cards.scryfall.io/normal/front/0/8/08ff764d-2c0d-4599-a22a-6a64766dad3a.jpg?1783911425"
    }
}
