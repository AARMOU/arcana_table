package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeywordToOwnSpells
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/**
 * Zhulodok, Void Gorger — Commander Masters (Eldrazi Unbound face commander).
 *
 * "Cascade, cascade" is two separate cascade abilities, so the grant is authored twice; each
 * instance becomes its own cast trigger. Mana value is read as cast (X included). Needs the
 * granted-cascade engine patch (engine-upgrade/ulalek/patches); the shipped engine ignores a
 * granted CASCADE keyword.
 */
private val colorlessSevenPlus: GameObjectFilter =
    GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless).manaValueAtLeast(7)

val ZhulodokVoidGorger = card("Zhulodok, Void Gorger") {
    manaCost = "{5}{C}"
    colorIdentity = ""
    typeLine = "Legendary Creature — Eldrazi"
    power = 7
    toughness = 4
    oracleText = "Colorless spells you cast from your hand with mana value 7 or greater have \"Cascade, cascade.\" " +
        "(When you cast one, exile cards from the top of your library until you exile a nonland card that costs less. " +
        "You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order. Then do it again.)"
    repeat(2) {
        staticAbility {
            ability = GrantKeywordToOwnSpells(Keyword.CASCADE, colorlessSevenPlus, fromZone = Zone.HAND)
        }
    }
    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "752"
    }
}
