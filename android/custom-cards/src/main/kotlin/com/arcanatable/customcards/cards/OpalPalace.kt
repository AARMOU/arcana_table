package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider

/**
 * Opal Palace (SOC #390), Eldrazi Incursion.
 *
 * The second ability adds one mana of a color in the controller's commander color identity
 * (`ManaColorSet.CommanderIdentity`, the union of both partners' identities), and that mana carries
 * engine patch 0034's `ManaSpellRider.CommanderEntersWithCastCountCounters`. When the mana is
 * actually spent on a spell that is one of the caster's own commanders, that commander is owed
 * +1/+1 counters equal to **its own** command-zone cast count (not a partner-pair sum, unlike patch
 * 0024's `DynamicAmount.CommanderCastsFromCommandZone`), placed as it enters. The count is read after
 * the cast is recorded, so it includes the current cast when cast from the command zone; casts from
 * other zones don't add to it but still read it; a countered cast still counted. A commander cast
 * with other mana, or this mana spent on anything else, gets nothing.
 *
 * Official rulings (2020-11-10), quoted:
 * - "If you don't have a commander, Opal Palace's last ability produces no mana."
 * - "If your commander is a card that has no colors in its color identity, Opal Palace's last
 *   ability produces no mana. It doesn't produce {C}."
 * - "If Opal Palace's last ability produces two mana (most likely due to Mana Reflection) and you
 *   spend them to cast a commander, that commander enters with two counters for each time it's been
 *   cast from the command zone this game." (each rider mana spent adds its own counters)
 * - "The "number of times it's been cast from the command zone" includes the most recent time. For
 *   example, the first time you cast your commander from the command zone in a game, if you spent
 *   mana from Opal Palace's last ability to do so, it will enter the battlefield with a +1/+1
 *   counter."
 * - "If you have two commanders, the last ability adds one mana of any color in their combined color
 *   identities."
 */
val OpalPalace = card("Opal Palace") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n" +
        "{1}, {T}: Add one mana of any color in your commander's color identity. If you spend this " +
        "mana to cast your commander, it enters with a number of additional +1/+1 counters on it " +
        "equal to the number of times it's been cast from the command zone this game."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C}."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Effects.AddManaOfColorInCommanderColorIdentity(
            riders = setOf(ManaSpellRider.CommanderEntersWithCastCountCounters())
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{1}, {T}: Add one mana of any color in your commander's color identity. If you " +
            "spend this mana to cast your commander, it enters with a number of additional +1/+1 " +
            "counters on it equal to the number of times it's been cast from the command zone this game."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "390"
        artist = "John Avon"
        imageUri = "https://cards.scryfall.io/normal/front/9/1/912553e7-1e67-4045-84fd-0a791754cf6c.jpg?1783903726"
        ruling("2020-11-10", "If you don't have a commander, Opal Palace's last ability produces no mana.")
        ruling("2020-11-10", "If your commander is a card that has no colors in its color identity, Opal Palace's last ability produces no mana. It doesn't produce {C}.")
        ruling("2020-11-10", "If Opal Palace's last ability produces two mana (most likely due to Mana Reflection) and you spend them to cast a commander, that commander enters with two counters for each time it's been cast from the command zone this game.")
        ruling("2020-11-10", "The \"number of times it's been cast from the command zone\" includes the most recent time. For example, the first time you cast your commander from the command zone in a game, if you spent mana from Opal Palace's last ability to do so, it will enter the battlefield with a +1/+1 counter.")
        ruling("2020-11-10", "If you have two commanders, the last ability adds one mana of any color in their combined color identities.")
    }
}
