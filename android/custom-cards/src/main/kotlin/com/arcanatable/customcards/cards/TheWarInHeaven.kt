package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.EntersWithCharacteristics
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TotalCap

/**
 * The War in Heaven — Warhammer 40,000 Commander #69 (Necron Dynasties). {3}{B}{B}{B} Enchantment — Saga.
 *
 * I — You draw three cards and you lose 3 life.
 * II — Mill three cards.
 * III — Choose up to three target creature cards with total mana value 8 or less in your graveyard. Return
 * each of them to the battlefield with a necrodermis counter on it. They're artifacts in addition to their
 * other types.
 *
 * Chapter III targets like Patch Up / The Super Hero Civil War: up to three creature cards in your graveyard
 * with `totalAtMost = TotalCap.manaValue(8)`, so a choice over 8 is refused when the trigger is put on the
 * stack (CR 601.2c via 603.3d), and zero targets is legal. On resolution the still-legal targets return in
 * one move. Each enters with a necrodermis counter (`addCounterType`, placed as it enters) and already an
 * artifact (`entersWith` adding the artifact type, the Relive the Past shape for "They are ... in addition
 * to their other types"). That type comes from a resolved ability, not from the Saga, so it stays after the
 * Saga is sacrificed, for as long as the permanent stays on the battlefield. If a target has left your
 * graveyard by resolution, only the others return (CR 608.2b). A necrodermis counter has no rules meaning
 * of its own.
 *
 * "As they enter" reading (coordinator decision, 2026-11-03): the type change is part of the same effect
 * that returns the cards, so they are artifacts as they enter. Entry replacements ("artifacts enter
 * tapped") and "whenever an artifact enters" triggers therefore see them. Upstream Argentum reads Relive
 * the Past's identical "They are ... in addition to their other types" the same way (`entersWith`). No
 * published ruling for this card addresses it.
 *
 * Official ruling (2022-10-07, Scryfall): "If a card in your graveyard has an {X} in its cost, X is zero."
 */
val TheWarInHeaven = card("The War in Heaven") {
    manaCost = "{3}{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Enchantment — Saga"
    oracleText = "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\n" +
        "I — You draw three cards and you lose 3 life.\n" +
        "II — Mill three cards.\n" +
        "III — Choose up to three target creature cards with total mana value 8 or less in your graveyard. " +
        "Return each of them to the battlefield with a necrodermis counter on it. They're artifacts in " +
        "addition to their other types."

    sagaChapter(1) {
        effect = Effects.DrawCards(3) then Effects.LoseLife(3, EffectTarget.Controller)
    }

    sagaChapter(2) {
        effect = Patterns.Library.mill(3)
    }

    sagaChapter(3) {
        targets(
            TargetFilter.CreatureInYourGraveyard,
            count = 3,
            optional = true,
            totalAtMost = TotalCap.manaValue(DynamicAmounts.fixed(8)),
        )
        effect = Effects.Pipeline {
            val chosen = gather(CardSource.ChosenTargets)
            move(
                chosen,
                CardDestination.ToZone(Zone.BATTLEFIELD, Player.You),
                addCounterType = CounterType.of("necrodermis"),
                entersWith = EntersWithCharacteristics(addedCardTypes = setOf(CardType.ARTIFACT)),
            )
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "69"
        artist = "Darren Tan"
        imageUri = "https://cards.scryfall.io/normal/front/c/d/cd556cd1-e33a-433d-ac18-4a4fd195c3ab.jpg?1783920897"
    }
}
