package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * Eldrazi Confluence — M3C #32. Choose three, repeats allowed (Cosmium Confluence's shape).
 * The blink mode reuses Eldrazi Displacer's exile-then-return-tapped sequence (owner's control).
 */
// CARDKIT-SCAFFOLD — unregistered: implemented by Claude, awaiting CI scenario evidence before registration.
val EldraziConfluence = card("Eldrazi Confluence") {
    manaCost = "{2}{C}{C}"
    colorIdentity = ""
    typeLine = "Instant"
    oracleText = "Choose three. You may choose the same mode more than once.\n" +
        "• Target creature gets +3/-3 until end of turn.\n" +
        "• Exile target nonland permanent, then return it to the battlefield tapped under its owner's control.\n" +
        "• Create a 1/1 colorless Eldrazi Scion creature token with \"Sacrifice this token: Add {C}.\""

    spell {
        modal(chooseCount = 3, allowRepeat = true) {
            mode("Target creature gets +3/-3 until end of turn") {
                val creature = target(TargetFilter.Creature)
                effect = Effects.ModifyStats(3, -3, creature)
            }
            mode("Exile target nonland permanent, then return it to the battlefield tapped under its owner's control") {
                val permanent = target(TargetFilter.NonlandPermanent)
                effect = Effects.Move(permanent, Zone.EXILE) then
                    Effects.Move(permanent, Zone.BATTLEFIELD, placement = ZonePlacement.Tapped, fromZone = Zone.EXILE)
            }
            mode("Create a 1/1 colorless Eldrazi Scion creature token with \"Sacrifice this token: Add {C}.\"") {
                effect = createEldraziScions(1)
            }
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "32"
        artist = "Hristo D. Chukov"
        imageUri = "https://cards.scryfall.io/normal/front/7/8/78ee2013-29dc-4879-9d59-1b492996d297.jpg?1783911429"
    }
}
